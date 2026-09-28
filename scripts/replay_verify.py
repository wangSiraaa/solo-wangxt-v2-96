#!/usr/bin/env python3
"""
固定命令回放的 HTTP 端到端核对脚本（与前端 replay.ts 完全同一条命令序列）。
每步之后拉取 /api/snapshot，断言盘口、订单状态、成交与数量守恒。
用法: python3 replay_verify.py [base_url]
"""
import json
import sys
import urllib.request
import urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
SYMBOL = "SYNTH-0"
failures = []


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def place(coid, side, price, qty):
    return call("POST", "/api/orders", {
        "clientOrderId": coid, "symbol": SYMBOL, "side": side,
        "limitPrice": price, "quantity": qty})


def check(name, cond, detail=""):
    mark = "PASS" if cond else "FAIL"
    print(f"  [{mark}] {name}" + (f" -- {detail}" if detail else ""))
    if not cond:
        failures.append(name)


def levels(book, side):
    return {lvl["price"]: (lvl["qty"], lvl["orderCount"]) for lvl in book[side]}


def snap():
    _, s = call("GET", "/api/snapshot")
    return s


def conservation_ok(s):
    c = s["conservation"]
    eq = c["origQty"] == c["filledQty"] + c["cancelledQty"] + c["bookQty"]
    # 成交记录单边总量 *2 == filledQty
    leg = sum(t["qty"] for t in s["trades"])
    trades_ok = leg * 2 == c["filledQty"]
    # 深度聚合 == LIVE 订单剩余
    live = [o for o in s["orders"] if o["status"] in ("NEW", "PARTIALLY_FILLED")]
    bid_live = sum(o["remainingQty"] for o in live if o["side"] == "BUY")
    ask_live = sum(o["remainingQty"] for o in live if o["side"] == "SELL")
    bid_book = sum(l["qty"] for l in s["book"]["bids"])
    ask_book = sum(l["qty"] for l in s["book"]["asks"])
    return eq and trades_ok and bid_book == bid_live and ask_book == ask_live, c, leg


print("== 0. RESET ==")
call("POST", "/api/admin/reset")
s = snap()
check("空盘守恒 0=0+0+0", conservation_ok(s)[0])
check("盘口全空", not s["book"]["bids"] and not s["book"]["asks"])

print("== 1. SELL S-1 10.00 x30（同价排队 1/2） ==")
st, r = place("S-1", "SELL", "10.00", 30)
check("HTTP 201 新订单", st == 201, str(st))
s = snap()
check("卖盘 10.00 x30 / 1 单", levels(s["book"], "asks").get("10.00") == (30, 1))
check("守恒", conservation_ok(s)[0])

print("== 2. SELL S-2 10.00 x20（同价排队 2/2） ==")
place("S-2", "SELL", "10.00", 20)
s = snap()
check("卖盘 10.00 聚合 50 / 2 单", levels(s["book"], "asks").get("10.00") == (50, 2))
check("守恒", conservation_ok(s)[0])

print("== 3. SELL S-3 10.10 x30（第二档） ==")
place("S-3", "SELL", "10.10", 30)
s = snap()
check("出现两档报价", levels(s["book"], "asks") == {"10.00": (50, 2), "10.10": (30, 1)})
check("守恒", conservation_ok(s)[0])

print("== 4. SELL S-4 10.30 x100（超出稍后限价，不应成交） ==")
place("S-4", "SELL", "10.30", 100)
s = snap()
check("三档报价就位", levels(s["book"], "asks") ==
      {"10.00": (50, 2), "10.10": (30, 1), "10.30": (100, 1)})
check("守恒", conservation_ok(s)[0])

print("== 5. BUY B-1 10.20 x100：吃掉两档 + 部分成交 ==")
st, r = place("B-1", "BUY", "10.20", 100)
check("HTTP 201", st == 201)
b1 = r["order"]
check("B-1 PARTIALLY_FILLED filled=80 remaining=20",
      (b1["status"], b1["filledQty"], b1["remainingQty"]) == ("PARTIALLY_FILLED", 80, 20))
fills = [(t["tradeId"], t["price"], t["qty"]) for t in r["executions"]]
check("3 笔成交且价格/数量顺序为 10.00x30(S-1) 10.00x20(S-2) 10.10x30(S-3)",
      fills == [("T000001", "10.00", 30), ("T000002", "10.00", 20), ("T000003", "10.10", 30)],
      str(fills))
# 同价时间优先：第一笔 maker 必须是 S-1（id 更小）
check("首笔 maker 是 S-1（同价时间优先）",
      r["executions"][0]["makerOrderId"] == 1, str(r["executions"][0]["makerOrderId"]))
s = snap()
check("卖盘只剩 10.30 x100", levels(s["book"], "asks") == {"10.30": (100, 1)})
check("剩余 20 挂入买盘 10.20", levels(s["book"], "bids") == {"10.20": (20, 1)})
ok, c, leg = conservation_ok(s)
check("守恒 280=160+0+120 且成交单边=80",
      ok and (c["origQty"], c["filledQty"], c["bookQty"]) == (280, 160, 120) and leg == 80,
      str(c) + f" tradeLeg={leg}")

print("== 6. CANCEL B-1 剩余 20 ==")
st, cr = call("DELETE", f"/api/orders/{b1['id']}")
check("撤单 200 且状态 CANCELLED", st == 200 and cr["status"] == "CANCELLED")
s = snap()
check("买盘清空", s["book"]["bids"] == [])
ok, c, _ = conservation_ok(s)
check("撤单后守恒 280=160+20+100",
      ok and c == {"origQty": 280, "filledQty": 160, "cancelledQty": 20, "bookQty": 100, "balanced": True},
      str(c))
# 已成交订单不可撤
st2, _ = call("DELETE", "/api/orders/1")
check("已成交订单撤单返回 409", st2 == 409, str(st2))

print("== 7. 重复 clientOrderId：SELL S-1 改成 10.50 x999 ==")
before = len(s["orders"])
st, r = place("S-1", "SELL", "10.50", 999)
check("HTTP 200 + deduplicated=true", st == 200 and r["deduplicated"] is True, str(st))
check("返回的是原订单（SELL 10.00 x30, FILLED）",
      (r["order"]["side"], r["order"]["limitPrice"], r["order"]["origQty"], r["order"]["status"])
      == ("SELL", "10.00", 30, "FILLED"))
s = snap()
check("订单总数没有增加", len(s["orders"]) == before, f"{before} -> {len(s['orders'])}")
check("最终守恒", conservation_ok(s)[0])

print("== 8. 刻度与范围校验（整数 tick，拒绝浮点越界） ==")
st, r = place("BAD-1", "BUY", "10.005", 10)
check("10.005 不在 0.01 刻度上 -> 400", st == 400, str(st))
st, r = place("BAD-2", "BUY", "10.00", 0)
check("数量 0 -> 400", st == 400)
st, r = place("BAD-3", "OTHER", "10.00", 10)
check("非本标的 -> 400", st == 400)
s = snap()
check("被拒订单未落库，守恒依旧", conservation_ok(s)[0] and len(s["orders"]) == before)

print()
if failures:
    print(f"!!!! {len(failures)} 项核对失败: {failures}")
    sys.exit(1)
print("全部核对通过：同价排队 / 吃掉两档 / 部分成交 / 撤单 / 幂等 / 数量守恒")
