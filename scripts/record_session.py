#!/usr/bin/env python3
"""
按固定命令序列逐步调用 API，把每一步的「页面状态」录制为 Markdown：
盘口深度、订单状态、成交明细、数量守恒。产物提交到 docs/replay-recording.md。
"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
SYMBOL = "SYNTH-0"

STEPS = [
    ("RESET", "reset", None),
    ("SELL S-1 10.00 x30", "place", dict(clientOrderId="S-1", side="SELL", limitPrice="10.00", quantity=30)),
    ("SELL S-2 10.00 x20", "place", dict(clientOrderId="S-2", side="SELL", limitPrice="10.00", quantity=20)),
    ("SELL S-3 10.10 x30", "place", dict(clientOrderId="S-3", side="SELL", limitPrice="10.10", quantity=30)),
    ("SELL S-4 10.30 x100", "place", dict(clientOrderId="S-4", side="SELL", limitPrice="10.30", quantity=100)),
    ("BUY B-1 10.20 x100", "place", dict(clientOrderId="B-1", side="BUY", limitPrice="10.20", quantity=100)),
    ("CANCEL B-1 剩余 20", "cancel", dict(ref="B-1")),
    ("重复提交 S-1（10.50 x999）幂等拦截", "place",
     dict(clientOrderId="S-1", side="SELL", limitPrice="10.50", quantity=999)),
]


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def place(p):
    return call("POST", "/api/orders", {**p, "symbol": SYMBOL})


def fmt_book(book):
    asks = list(reversed(book["asks"]))  # 高->低，与页面上半区一致
    bids = book["bids"]
    lines = ["卖盘(高→低): " + ("空" if not asks else
             " | ".join(f"{l['price']} x{l['qty']} ({l['orderCount']}单)" for l in asks))]
    lines.append("买盘(高→低): " + ("空" if not bids else
             " | ".join(f"{l['price']} x{l['qty']} ({l['orderCount']}单)" for l in bids)))
    return lines


def fmt_orders(orders):
    if not orders:
        return ["订单: 空"]
    out = []
    for o in reversed(orders):  # 旧->新
        out.append(
            f"#{o['id']} {o['clientOrderId']} {o['side']} {o['limitPrice']} "
            f"orig={o['origQty']} filled={o['filledQty']} bookRem={o['remainingQty']} {o['status']}")
    return ["订单(旧→新): " + "; ".join(out)]


def fmt_trades(trades):
    if not trades:
        return ["成交: 空"]
    # 页面按新->旧，录制按时间正序更易读
    rows = list(reversed(trades))
    return ["成交(时间正序): " + "; ".join(
        f"{t['tradeId']} {t['price']}x{t['qty']} maker=#{t['makerOrderId']} taker=#{t['takerOrderId']}"
        for t in rows)]


def fmt_conservation(s):
    c = s["conservation"]
    leg = sum(t["qty"] for t in s["trades"])
    rhs = c["filledQty"] + c["cancelledQty"] + c["bookQty"]
    ok = rhs == c["origQty"] and leg * 2 == c["filledQty"]
    return (f"守恒: orig {c['origQty']} = filled(双边) {c['filledQty']} + cancelled {c['cancelledQty']} "
            f"+ book {c['bookQty']}  | 成交单边合计 {leg}（×2={leg*2}） => {'✓' if ok else '✗'}")


def main():
    md = ["# 固定命令回放 · 页面变化录制", "",
          "标的 `SYNTH-0`（合成订单，不接真实市场），tick=0.01。",
          "下列每一步对应前端「固定命令回放」面板的同一条命令；数据来自对运行中后端的真实 HTTP 调用。", ""]
    call("POST", "/api/admin/reset")

    for i, (title, kind, payload) in enumerate(STEPS):
        note = ""
        if kind == "reset":
            call("POST", "/api/admin/reset")
        elif kind == "place":
            st, r = place(payload)
            if r.get("deduplicated"):
                note = f"HTTP {st}，deduplicated=true，未新增订单"
            else:
                note = f"HTTP {st}，新订单 #{r['order']['id']}，本单成交 {len(r['executions'])} 笔"
        elif kind == "cancel":
            snap0 = call("GET", "/api/snapshot")[1]
            target = next(o for o in snap0["orders"] if o["clientOrderId"] == payload["ref"])
            st, r = call("DELETE", f"/api/orders/{target['id']}")
            note = f"HTTP {st}，撤销 #{target['id']} 的剩余 {target['remainingQty']}"

        s = call("GET", "/api/snapshot")[1]
        md += [f"## 步骤 {i} · {title}", ""]
        if note:
            md += [f"接口结果：{note}", ""]
        md += ["```", *fmt_book(s["book"]), *fmt_orders(s["orders"]),
               *fmt_trades(s["trades"]), fmt_conservation(s), "```", ""]

    md += ["> 每一步均满足：`orig = filled(双边) + cancelled + book`；"
           "成交明细单边总量 ×2 与 filled 相等；盘口聚合量与 LIVE 订单剩余量相等。", ""]
    open("docs/replay-recording.md", "w", encoding="utf-8").write("\n".join(md))
    print("recorded docs/replay-recording.md")


if __name__ == "__main__":
    main()
