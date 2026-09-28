import { useState } from 'react';
import { priceToTicks } from '../tick';
import type { Side } from '../types';

interface Props {
  symbol: string;
  tickSize: string;
  busy: boolean;
  onPlace: (input: {
    clientOrderId: string;
    symbol: string;
    side: Side;
    limitPrice: string;
    quantity: number;
  }) => Promise<void>;
}

function nextDefaultId(): string {
  return `M-${Date.now().toString(36).toUpperCase()}`;
}

export default function OrderForm({ symbol, tickSize, busy, onPlace }: Props) {
  const [clientOrderId, setClientOrderId] = useState(nextDefaultId());
  const [side, setSide] = useState<Side>('BUY');
  const [price, setPrice] = useState('10.00');
  const [qty, setQty] = useState('10');
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    const quantity = Number(qty);
    if (!Number.isInteger(quantity) || quantity <= 0) {
      setError('数量必须是正整数');
      return;
    }
    if (!clientOrderId.trim()) {
      setError('clientOrderId 不能为空（幂等键）');
      return;
    }
    try {
      // 提交前用纯整数刻度规则预校验，避免越刻度价格发出
      const ticks = priceToTicks(price, tickSize);
      if (ticks <= 0n) throw new Error('价格必须大于 0');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      return;
    }
    try {
      await onPlace({ clientOrderId: clientOrderId.trim(), symbol, side, limitPrice: price, quantity });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <section className="card">
      <h2>下单（合成限价单）</h2>
      <p className="hint">
        标的 <code>{symbol}</code> · 最小变动 <code>{tickSize}</code> · 价格按刻度转整数 tick，不使用浮点比较
      </p>
      <form className="order-form" onSubmit={submit}>
        <label>
          clientOrderId（幂等键，重复提交不会新增订单）
          <input value={clientOrderId} onChange={(e) => setClientOrderId(e.target.value)} maxLength={64} />
        </label>
        <div className="row">
          <div className="seg">
            <button
              type="button"
              className={side === 'BUY' ? 'seg-btn buy active' : 'seg-btn buy'}
              onClick={() => setSide('BUY')}
            >
              买入 BUY
            </button>
            <button
              type="button"
              className={side === 'SELL' ? 'seg-btn sell active' : 'seg-btn sell'}
              onClick={() => setSide('SELL')}
            >
              卖出 SELL
            </button>
          </div>
          <label>
            限价
            <input value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" />
          </label>
          <label>
            数量
            <input value={qty} onChange={(e) => setQty(e.target.value)} inputMode="numeric" />
          </label>
          <button className="primary" disabled={busy} type="submit">
            {busy ? '撮合中…' : '提交限价单'}
          </button>
        </div>
        {error && <div className="error">⚠ {error}</div>}
      </form>
    </section>
  );
}
