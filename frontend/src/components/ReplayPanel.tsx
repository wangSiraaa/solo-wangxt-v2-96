import { useState } from 'react';
import type { Book, Conservation, Order, ReplayCommand, ReplayFrame, Trade } from '../types';
import { api, ApiError } from '../api';
import { REPLAY_SCRIPT, EXPECTED_END_STATE } from '../script';
import { formatPrice } from '../format';

interface Props {
  scale: number;
  onTick: () => void;
}

/**
 * 固定命令回放台。
 * 每执行一条命令都重新抓取成交/盘口/守恒，形成一帧“页面变化”录制；
 * 全部命令执行完后与预期值核对数量守恒。
 */
export function ReplayPanel({ scale, onTick }: Props) {
  const [frames, setFrames] = useState<ReplayFrame[]>([]);
  const [running, setRunning] = useState(false);
  const [verified, setVerified] = useState<null | { pass: boolean; message: string }>(null);

  async function snapshotState(): Promise<{ book: Book; conservation: Conservation }> {
    const [book, conservation] = await Promise.all([api.book(), api.conservation()]);
    return { book, conservation };
  }

  async function stepFrom(index: number, prevTradeIds: Set<number>) {
    const cmd: ReplayCommand = REPLAY_SCRIPT[index];
    let order: Order | null = null;
    let ok = true;
    let rejected = false;
    let error: string | undefined;
    let newTrades: Trade[] = [];

    try {
      if (cmd.type === 'PLACE') {
        const r = await api.place(cmd.clientOrderId, cmd.side!, cmd.priceTicks!, cmd.quantity!);
        order = r.order;
        // 预期拒绝却成功了，才是真正的失败
        if (cmd.expectReject) ok = false;
      } else {
        order = await api.cancel(cmd.clientOrderId);
      }
      const allTrades = await api.trades();
      newTrades = allTrades
        .filter((t) => !prevTradeIds.has(t.id))
        .reverse(); // 按成交发生先后
      allTrades.forEach((t) => prevTradeIds.add(t.id));
    } catch (e) {
      error = e instanceof ApiError ? `${e.code} (${e.status}): ${e.message}` : String(e);
      if (cmd.expectReject) {
        // 预期被拒绝：拒绝行为符合脚本
        rejected = true;
        ok = !cmd.expectErrorCode || e instanceof ApiError && e.code === cmd.expectErrorCode;
      } else {
        ok = false;
      }
    }

    const { book, conservation } = await snapshotState();
    const frame: ReplayFrame = { index, command: cmd, ok, rejected, error, order, newTrades, book, conservation };
    onTick();
    return frame;
  }

  async function runAll(auto: boolean) {
    setRunning(true);
    setVerified(null);
    setFrames([]);
    await api.reset();
    onTick();
    const seen = new Set<number>();
    const collected: ReplayFrame[] = [];

    for (let i = 0; i < REPLAY_SCRIPT.length; i++) {
      const frame = await stepFrom(i, seen);
      collected.push(frame);
      setFrames([...collected]);
      if (auto) await new Promise((r) => setTimeout(r, 700));
    }

    const last = collected[collected.length - 1];
    const allAsExpected = collected.every((f) => f.ok);
    const pass =
      allAsExpected &&
      last.rejected && /DUPLICATE_ORDER/.test(last.error ?? '') &&
      last.conservation.conserved &&
      last.conservation.totalTradeVolume === EXPECTED_END_STATE.totalTradeVolume &&
      last.conservation.buyFilledQuantity === EXPECTED_END_STATE.buyFilledQuantity &&
      last.conservation.sellFilledQuantity === EXPECTED_END_STATE.sellFilledQuantity;
    setVerified({
      pass,
      message: pass
        ? `通过：总成交量 ${last.conservation.totalTradeVolume}，买卖各 ${last.conservation.buyFilledQuantity}，重复订单号被拒绝`
        : '未通过：请检查各帧明细',
    });
    setRunning(false);
  }

  async function resetOnly() {
    await api.reset();
    setFrames([]);
    setVerified(null);
    onTick();
  }

  function exportJson() {
    const blob = new Blob([JSON.stringify({ frames }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `matching-replay-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="panel col-replay">
      <h2>
        固定命令回放（{REPLAY_SCRIPT.length} 步：同价排队 / 吃掉两档 / 部分成交 / 撤单 / 重复拒单）
        <span className="pill">已录制 {frames.length} 帧</span>
      </h2>

      <div className="replay-controls">
        <button disabled={running} onClick={() => runAll(true)}>▶ 自动回放</button>
        <button className="ghost" disabled={running} onClick={() => runAll(false)}>⏩ 一次性执行并录制</button>
        <button className="ghost" disabled={running} onClick={resetOnly}>↺ 清空重置</button>
        <button className="ghost" disabled={frames.length === 0} onClick={exportJson}>导出录制 JSON</button>
      </div>

      {verified && (
        <div className={verified.pass ? 'success' : 'error'} style={{ marginBottom: 10 }}>
          {verified.pass ? '✓ ' : '✗ '}{verified.message}
        </div>
      )}

      <div className="frames">
        {frames.length === 0 && <div className="empty">点击回放后，此处逐步录制页面变化</div>}
        {frames.map((f) => (
          <FrameView key={f.index} frame={f} scale={scale} />
        ))}
      </div>
    </div>
  );
}

function FrameView({ frame, scale }: { frame: ReplayFrame; scale: number }) {
  const f = frame;
  const cmdDesc =
    f.command.type === 'PLACE'
      ? `${f.command.side === 'BUY' ? '买入' : '卖出'} ${f.command.clientOrderId} @${formatPrice(f.command.priceTicks!, scale)} ×${f.command.quantity}`
      : `撤单 ${f.command.clientOrderId}`;

  const c = f.conservation;
  return (
    <div className={`frame${f.ok ? '' : ' fail'}`}>
      <div className="cmd">
        <span>#{f.index + 1} {cmdDesc}</span>
        {f.rejected
          ? <span className="badge-ok">拒绝（符合预期）</span>
          : <span className={f.ok ? 'badge-ok' : 'badge-bad'}>{f.ok ? 'OK' : '失败'}</span>}
      </div>
      <div className="note">{f.command.note}</div>
      {f.order && (
        <div className="kv">
          订单 {f.order.clientOrderId}：{f.order.status}，已成 {f.order.filledQuantity}/{f.order.quantity}，剩余 {f.order.remainingQuantity}
          {f.order.seq > 0 ? `，排队号 ${f.order.seq}` : ''}
        </div>
      )}
      {f.newTrades.map((t) => (
        <div className="trade-line mono" key={t.id}>
          成交：{t.makerClientOrderId} ↔ {t.takerClientOrderId} @{formatPrice(t.priceTicks, scale)} ×{t.quantity}
        </div>
      ))}
      {f.error && <div className={f.rejected ? 'trade-line' : 'err-line'}>{f.error}</div>}
      <div className="kv">
        盘口 买{topLevel(f.book.bids)} / 卖{topLevel(f.book.asks)}｜
        总成交 {c.totalTradeVolume}（买 {c.buyFilledQuantity} / 卖 {c.sellFilledQuantity}）｜
        balance {c.balance} {c.conserved ? '✓' : '✗'}
      </div>
    </div>
  );
}

function topLevel(levels: { priceTicks: number; quantity: number }[]): string {
  if (levels.length === 0) return '空';
  return `${formatPrice(levels[0].priceTicks, 2)}×${levels[0].quantity}(${levels.length}档)`;
}
