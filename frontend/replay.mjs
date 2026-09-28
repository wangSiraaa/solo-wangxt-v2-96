#!/usr/bin/env node
/**
 * 固定命令回放与“页面变化”录制脚本（Node 18+，无第三方依赖）。
 *
 * 用法：
 *   node replay.mjs [--base http://localhost:8080] [--out recording.json]
 *
 * 每一步：
 *   1. 调用下单/撤单 API（这正是页面按钮触发的同一组 HTTP 接口）；
 *   2. 重新抓取 /api/book、/api/orders、/api/trades、/api/conservation，
 *      等同于页面各区块刷新后看到的状态，逐帧记录为“页面变化”；
 *   3. 核对数量守恒（balance 必须为 0、买卖成交量必须相等）。
 * 结束后核对预期总成交量，并打印最终结论；录制结果写入 JSON 文件。
 */

const argv = process.argv.slice(2);
function option(name, fallback) {
  const i = argv.indexOf(name);
  return i >= 0 && i + 1 < argv.length ? argv[i + 1] : fallback;
}
const base = option('--base', 'http://localhost:8080');
const outFile = option('--out', null);

const SYMBOL = 'SYNTH-01';

// 与前端 src/script.ts 完全相同的固定脚本（价格=整数刻度，tickSize 0.01）
const SCRIPT = [
  { type: 'PLACE', clientOrderId: 'S1', side: 'SELL', priceTicks: 1000, quantity: 10, note: '卖单 10.00×10 入簿排队' },
  { type: 'PLACE', clientOrderId: 'S2', side: 'SELL', priceTicks: 1000, quantity: 10, note: '卖单 10.00×10 同价排第二' },
  { type: 'PLACE', clientOrderId: 'S3', side: 'SELL', priceTicks: 1000, quantity: 10, note: '卖单 10.00×10 同价排第三' },
  { type: 'PLACE', clientOrderId: 'B1', side: 'BUY', priceTicks: 1000, quantity: 15, note: '买单 10.00×15：S1全成10、S2部成5（FIFO+部分成交）' },
  { type: 'CANCEL', clientOrderId: 'S2', note: '撤销 S2 剩余5（已成交5保留）' },
  { type: 'CANCEL', clientOrderId: 'S3', note: '撤销 S3 全部10（未成交），10.00档清空' },
  { type: 'PLACE', clientOrderId: 'A1', side: 'SELL', priceTicks: 1001, quantity: 8, note: '卖单 10.01×8 第一档' },
  { type: 'PLACE', clientOrderId: 'A2', side: 'SELL', priceTicks: 1002, quantity: 20, note: '卖单 10.02×20 第二档' },
  { type: 'PLACE', clientOrderId: 'B2', side: 'BUY', priceTicks: 1002, quantity: 18, note: '买单 10.02×18：吃掉10.01全部8、10.02部成10（吃掉两档）' },
  { type: 'PLACE', clientOrderId: 'C1', side: 'BUY', priceTicks: 1000, quantity: 12, note: '买单 10.00×12 入簿' },
  { type: 'PLACE', clientOrderId: 'T1', side: 'SELL', priceTicks: 1000, quantity: 7, note: '卖单 10.00×7：C1部分成交7，剩5' },
  { type: 'CANCEL', clientOrderId: 'C1', note: '撤销 C1 剩余5（已成交7保留）' },
  { type: 'PLACE', clientOrderId: 'S1', side: 'SELL', priceTicks: 999, quantity: 99, expectStatus: 409, note: '重复订单号S1：必须409拒绝，不新增订单' },
];

const fmt = (ticks, scale = 2) => {
  const d = 10 ** scale;
  const w = Math.trunc(ticks / d);
  const f = String(Math.abs(ticks % d)).padStart(scale, '0');
  return `${w}.${f}`;
};

async function call(path, init) {
  const res = await fetch(base + path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  return { status: res.status, body };
}

const assert = (cond, message, failures) => {
  if (!cond) { failures.push(message); console.log('  ✗ ' + message); }
};

async function main() {
  const failures = [];
  console.log(`# 限价撮合固定命令回放  base=${base}  symbol=${SYMBOL}\n`);

  const settings = await call('/api/settings');
  if (settings.status !== 200) throw new Error('后端未就绪：' + settings.status);
  console.log(`刻度设置 tickSize=${settings.body.tickSize} scale=${settings.body.tickScale}\n`);

  // 回放前清空（与页面“清空重置”按钮一致）
  await call('/api/reset', { method: 'POST' });

  const frames = [];
  const seenTradeIds = new Set();

  for (let i = 0; i < SCRIPT.length; i++) {
    const cmd = SCRIPT[i];
    let response;
    let ok = true;
    let rejected = false;
    let errorCode = null;

    if (cmd.type === 'PLACE') {
      response = await call('/api/orders', {
        method: 'POST',
        body: JSON.stringify({
          clientOrderId: cmd.clientOrderId, side: cmd.side,
          priceTicks: cmd.priceTicks, quantity: cmd.quantity,
        }),
      });
      if (cmd.expectStatus) {
        // 预期就是被拒绝：拒绝行为符合脚本预期，ok=true 并标记 rejected
        rejected = response.status === cmd.expectStatus;
        ok = rejected;
        errorCode = response.body?.error;
      } else if (response.status !== 200) {
        ok = false; errorCode = response.body?.error || `HTTP_${response.status}`;
      }
    } else {
      response = await call(`/api/orders/${cmd.clientOrderId}/cancel`, { method: 'POST' });
      ok = response.status === 200;
      if (!ok) errorCode = response.body?.error || `HTTP_${response.status}`;
    }

    // —— 这一帧“页面各区块”的状态（与浏览器轮询看到的数据一致）——
    const [book, orders, trades, conservation] = await Promise.all([
      call('/api/book'), call('/api/orders'), call('/api/trades'), call('/api/conservation'),
    ]);

    const newTrades = trades.body
      .filter((t) => !seenTradeIds.has(t.id))
      .sort((a, b) => a.id - b.id);
    trades.body.forEach((t) => seenTradeIds.add(t.id));

    const topBid = book.body.bids[0];
    const topAsk = book.body.asks[0];
    const action = cmd.type === 'PLACE'
      ? `${cmd.side.padEnd(4)} ${cmd.clientOrderId.padEnd(3)} @${fmt(cmd.priceTicks)}×${cmd.quantity}`
      : `CANCEL ${cmd.clientOrderId}`;

    console.log(`#${String(i + 1).padStart(2)} ${action}`);
    console.log(`     ${cmd.note}`);
    if (cmd.type === 'PLACE' && !cmd.expectStatus && response.body?.trades?.length) {
      for (const t of response.body.trades) {
        console.log(`     成交: ${t.makerClientOrderId} ↔ ${t.takerClientOrderId} @${fmt(t.priceTicks)}×${t.quantity}`);
      }
      console.log(`     订单结果: ${response.body.order.status} 已成${response.body.order.filledQuantity}/${response.body.order.quantity}`);
    }
    if (cmd.type === 'CANCEL' && response.status === 200) {
      console.log(`     撤单结果: ${response.body.status} 已成${response.body.filledQuantity}/${response.body.quantity}（剩余已离簿）`);
    }
    if (cmd.expectStatus) {
      console.log(`     重复提交响应: HTTP ${response.status} ${response.body?.error ?? ''} ${ok ? '✓' : '✗'}`);
    }
    console.log(`     页面: 买一 ${topBid ? fmt(topBid.priceTicks) + '×' + topBid.quantity : '空'} / 卖一 ${topAsk ? fmt(topAsk.priceTicks) + '×' + topAsk.quantity : '空'} | 总成交 ${conservation.body.totalTradeVolume}（买${conservation.body.buyFilledQuantity}/卖${conservation.body.sellFilledQuantity}）balance=${conservation.body.balance} ${conservation.body.conserved ? '✓' : '✗'}`);

    frames.push({
      step: i + 1, command: cmd, httpStatus: response.status, ok, rejected, errorCode,
      orderResponse: cmd.expectStatus ? undefined : response.body?.order ?? response.body,
      newTrades,
      page: {
        book: book.body,
        orders: orders.body,
        trades: trades.body,
        conservation: conservation.body,
      },
    });

    // 每帧都核对守恒
    assert(conservation.body.conserved, `第${i + 1}步数量不守恒 balance=${conservation.body.balance}`, failures);
    assert(conservation.body.buyFilledQuantity === conservation.body.sellFilledQuantity,
      `第${i + 1}步买卖成交量不等`, failures);
    if (cmd.expectStatus) {
      assert(ok, `第${i + 1}步应返回 ${cmd.expectStatus}，实际 ${response.status}`, failures);
      assert(errorCode === 'DUPLICATE_ORDER', `第${i + 1}步应返回 DUPLICATE_ORDER`, failures);
    } else {
      assert(ok, `第${i + 1}步命令执行失败: ${errorCode}`, failures);
    }
    console.log('');
  }

  // —— 重点场景断言（录制的页面变化必须与教学预期一致）——
  const byId = (cid) => frames.flatMap((f) => f.page.orders).filter((o) => o.clientOrderId === cid).slice(-1)[0];
  const orderAfter = (step, cid) => frames[step - 1].page.orders.find((o) => o.clientOrderId === cid);

  // 1. 同价排队 FIFO：第4步后 S1 全成10，S2 部成5剩5，S3 未动10
  const s1 = orderAfter(4, 'S1'), s2 = orderAfter(4, 'S2'), s3 = orderAfter(4, 'S3');
  assert(s1.status === 'FILLED' && s1.filledQuantity === 10, '同价排队: S1 应全部成交10', failures);
  assert(s2.status === 'PARTIALLY_FILLED' && s2.filledQuantity === 5 && s2.remainingQuantity === 5, '同价排队: S2 应部分成交5剩5', failures);
  assert(s3.status === 'NEW' && s3.remainingQuantity === 10, '同价排队: S3 应保持10未成交', failures);

  // 2. 撤剩余：第5步 S2 撤单（已成5保留、剩余0），第6步 S3 全撤
  const s2c = orderAfter(5, 'S2'), s3c = orderAfter(6, 'S3');
  assert(s2c.status === 'CANCELLED' && s2c.filledQuantity === 5 && s2c.remainingQuantity === 0, '剩余撤单: S2 撤后保留已成5、剩余0', failures);
  assert(s3c.status === 'CANCELLED' && s3c.filledQuantity === 0, '撤单: S3 应零成交全撤', failures);

  // 3. 吃掉两档：第9步 B2 成交 18 = 8(10.01) + 10(10.02)，成交价分别取 maker 价
  const b2Trades = frames[8].newTrades;
  assert(b2Trades.length === 2, `吃掉两档: B2 应产生2笔成交，实际${b2Trades.length}`, failures);
  if (b2Trades.length === 2) {
    assert(b2Trades[0].priceTicks === 1001 && b2Trades[0].quantity === 8, '吃掉两档: 第一笔 10.01×8', failures);
    assert(b2Trades[1].priceTicks === 1002 && b2Trades[1].quantity === 10, '吃掉两档: 第二笔 10.02×10（部分）', failures);
  }
  const a1 = orderAfter(9, 'A1'), a2 = orderAfter(9, 'A2');
  assert(a1.status === 'FILLED', '吃掉两档: A1 全部成交', failures);
  assert(a2.status === 'PARTIALLY_FILLED' && a2.remainingQuantity === 10, '吃掉两档: A2 剩10', failures);

  // 4. 部分成交 + 撤单：第11步 C1 成7剩5；第12步撤单后剩0、已成7保留
  const c1afterT1 = orderAfter(11, 'C1');
  assert(c1afterT1.status === 'PARTIALLY_FILLED' && c1afterT1.filledQuantity === 7 && c1afterT1.remainingQuantity === 5,
    '部分成交: C1 应成7剩5', failures);
  const c1 = byId('C1');
  assert(c1.status === 'CANCELLED' && c1.filledQuantity === 7 && c1.remainingQuantity === 0,
    '剩余撤单: C1 撤单后应保留已成7、剩余0', failures);

  // 5. 重复订单号：订单总数不增加（唯一订单号共 9 个）
  const finalConservation = frames.at(-1).page.conservation;
  assert(finalConservation.totalOrderCount === 9,
    `重复拒单: 订单总数应为9，实际${finalConservation.totalOrderCount}`, failures);

  // 5. 数量守恒终态：总成交量 15 + 18 + 7 = 40，买卖各40
  assert(finalConservation.totalTradeVolume === 40, `总成交量应为40，实际${finalConservation.totalTradeVolume}`, failures);
  assert(finalConservation.buyFilledQuantity === 40 && finalConservation.sellFilledQuantity === 40,
    '买卖累计成交应各为40', failures);
  assert(finalConservation.totalFilledQuantity === 80, '订单累计成交量应为 2×40=80', failures);
  assert(finalConservation.balance === 0 && finalConservation.conserved, '终态 balance 必须为0', failures);

  if (outFile) {
    const { writeFileSync } = await import('node:fs');
    writeFileSync(outFile, JSON.stringify({ symbol: SYMBOL, tickSize: settings.body.tickSize, frames, finalConservation }, null, 2));
    console.log(`录制结果已写入 ${outFile}`);
  }

  console.log('\n========== 核对结论 ==========');
  if (failures.length === 0) {
    console.log('✓ 全部通过：同价FIFO、吃掉两档、部分成交、剩余撤单、重复拒单、数量守恒（balance=0）均验证一致');
    process.exit(0);
  } else {
    console.log(`✗ ${failures.length} 项未通过：`);
    failures.forEach((m) => console.log('  - ' + m));
    process.exit(1);
  }
}

main().catch((e) => { console.error(e); process.exit(2); });
