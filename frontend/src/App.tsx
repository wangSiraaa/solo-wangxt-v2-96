import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { cancelOrder, fetchSnapshot, placeOrder, resetAll } from './api';
import ConservationBar from './components/ConservationBar';
import OrderBookView from './components/OrderBookView';
import OrderForm from './components/OrderForm';
import OrdersTable from './components/OrdersTable';
import ReplayPanel from './components/ReplayPanel';
import TradesTable from './components/TradesTable';
import { reconcile, describePlaceResult } from './reconcile';
import { REPLAY_SCRIPT, type ReplayStep } from './replay';
import type { Snapshot } from './types';

interface LogLine {
  at: string;
  text: string;
  tone: 'info' | 'ok' | 'warn' | 'error';
}

const REFRESH_MS = 3000;

export default function App() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [logs, setLogs] = useState<LogLine[]>([
    { at: now(), text: '系统就绪：合成标的，无真实市场连接。', tone: 'info' },
  ]);

  const snapshotRef = useRef<Snapshot | null>(null);
  snapshotRef.current = snapshot;

  const pushLog = useCallback((text: string, tone: LogLine['tone'] = 'info') => {
    setLogs((prev) => [{ at: now(), text, tone }, ...prev].slice(0, 60));
  }, []);

  const refresh = useCallback(async (): Promise<Snapshot | null> => {
    try {
      const s = await fetchSnapshot();
      setSnapshot(s);
      setError(null);
      return s;
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      return null;
    }
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, REFRESH_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  const handlePlace = useCallback(async (input: {
    clientOrderId: string; symbol: string; side: 'BUY' | 'SELL';
    limitPrice: string; quantity: number;
  }) => {
    setBusy(true);
    try {
      const result = await placeOrder(input);
      pushLog(describePlaceResult(result.order, result.executions, result.deduplicated),
             result.deduplicated ? 'warn' : 'ok');
      await refresh();
    } catch (e) {
      pushLog(`下单被拒绝：${msg(e)}`, 'error');
      throw e;
    } finally {
      setBusy(false);
    }
  }, [pushLog, refresh]);

  const handleCancel = useCallback(async (id: number) => {
    setBusy(true);
    try {
      await cancelOrder(id);
      pushLog(`订单 #${id} 剩余已撤销，状态 CANCELLED`, 'warn');
      await refresh();
    } catch (e) {
      pushLog(`撤单失败：${msg(e)}`, 'error');
    } finally {
      setBusy(false);
    }
  }, [pushLog, refresh]);

  /* ----------------------------- 固定命令回放 ----------------------------- */

  const runStep = useCallback(async (step: ReplayStep): Promise<void> => {
    pushLog(`▶ ${step.command}`, 'info');
    const a = step.action;
    if (a.kind === 'reset') {
      await resetAll();
      pushLog('已重置：订单与成交清空，成交号归位。', 'ok');
    } else if (a.kind === 'place') {
      const result = await placeOrder({
        clientOrderId: a.clientOrderId,
        symbol: snapshotRef.current?.instrument.symbol ?? 'SYNTH-0',
        side: a.side,
        limitPrice: a.limitPrice,
        quantity: a.quantity,
      });
      pushLog(describePlaceResult(result.order, result.executions, result.deduplicated),
             result.deduplicated ? 'warn' : 'ok');
    } else if (a.kind === 'cancel') {
      const target = snapshotRef.current?.orders.find((o) => o.clientOrderId === a.ref);
      if (!target) {
        throw new Error(`回放引用 ${a.ref} 未找到`);
      }
      const cancelled = await cancelOrder(target.id);
      pushLog(`#${target.id}（${a.ref}）剩余 ${target.remainingQty} 已撤销，状态 ${cancelled.status}。`, 'warn');
    }
    const s = await refresh();
    if (s) {
      const r = reconcile(s);
      pushLog(
        `守恒核对：原始 ${s.conservation.origQty} = 成交双边 ${s.conservation.filledQty}`
        + ` + 撤 ${s.conservation.cancelledQty} + 在簿 ${s.conservation.bookQty}`
        + `（${r.allBalanced ? '全部一致 ✓' : '存在失衡 ✗'}）`,
        r.allBalanced ? 'ok' : 'error',
      );
    }
  }, [pushLog, refresh]);

  const stepOnce = useCallback(async () => {
    if (stepIndex >= REPLAY_SCRIPT.length) return;
    setBusy(true);
    try {
      await runStep(REPLAY_SCRIPT[stepIndex]);
      setStepIndex((i) => i + 1);
    } catch (e) {
      pushLog(`回放中断：${msg(e)}`, 'error');
      setPlaying(false);
    } finally {
      setBusy(false);
    }
  }, [stepIndex, runStep, pushLog]);

  const playAll = useCallback(async () => {
    setPlaying(true);
    for (let i = stepIndex; i < REPLAY_SCRIPT.length; i++) {
      try {
        await runStep(REPLAY_SCRIPT[i]);
        setStepIndex(i + 1);
        await delay(900); // 让页面变化可被观察/录屏
      } catch (e) {
        pushLog(`回放中断：${msg(e)}`, 'error');
        break;
      }
    }
    setPlaying(false);
  }, [stepIndex, runStep, pushLog]);

  const resetScript = useCallback(async () => {
    setBusy(true);
    try {
      await resetAll();
      setStepIndex(0);
      pushLog('脚本已复位并清空数据。', 'info');
      await refresh();
    } finally {
      setBusy(false);
    }
  }, [pushLog, refresh]);

  const reconciliation = useMemo(() => (snapshot ? reconcile(snapshot) : null), [snapshot]);

  return (
    <div className="app">
      <header>
        <h1>限价订单撮合教学台</h1>
        <span className="tag">连续竞价 · 价格优先 / 同价时间优先 · 部分成交 · 剩余撤单 · 仅合成订单</span>
        {error && <span className="conn-error">后端连接失败：{error}</span>}
      </header>

      {snapshot && reconciliation && (
        <main>
          <ConservationBar conservation={snapshot.conservation} reconciliation={reconciliation} />

          <div className="grid">
            <div className="col-main">
              <OrderForm
                symbol={snapshot.instrument.symbol}
                tickSize={snapshot.instrument.tickSize}
                busy={busy}
                onPlace={handlePlace}
              />
              <OrderBookView book={snapshot.book} />
              <TradesTable trades={snapshot.trades} />
              <OrdersTable orders={snapshot.orders} onCancel={handleCancel} busy={busy} />
            </div>
            <div className="col-side">
              <ReplayPanel
                steps={REPLAY_SCRIPT}
                current={stepIndex}
                playing={playing}
                onPlay={playAll}
                onStep={stepOnce}
                onReset={resetScript}
              />
              <section className="card">
                <h2>操作 / 撮合日志</h2>
                <ul className="logs">
                  {logs.map((l, i) => (
                    <li key={i} className={`log-${l.tone}`}>
                      <span className="log-at">{l.at}</span> {l.text}
                    </li>
                  ))}
                </ul>
              </section>
            </div>
          </div>
        </main>
      )}
    </div>
  );
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function now(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false });
}

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}
