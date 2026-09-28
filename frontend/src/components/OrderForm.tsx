import { useState } from 'react';
import type { Side } from '../types';
import { api, ApiError } from '../api';
import { formatPrice, parsePriceToTicks } from '../format';

interface Props {
  scale: number;
  tickSize: string;
  onChanged: () => void;
}

export function OrderForm({ scale, tickSize, onChanged }: Props) {
  const [side, setSide] = useState<Side>('BUY');
  const [clientOrderId, setClientOrderId] = useState('');
  const [priceInput, setPriceInput] = useState('10.00');
  const [quantity, setQuantity] = useState('5');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const ticks = parsePriceToTicks(priceInput, scale);
  const qty = Number(quantity);
  const priceInvalid = ticks === null;
  const qtyInvalid = !Number.isInteger(qty) || qty <= 0;

  async function submit() {
    setError(null);
    setSuccess(null);
    if (priceInvalid) {
      setError(`价格必须是不超过 ${scale} 位小数的非负数（最小变动价位 ${tickSize}）`);
      return;
    }
    if (qtyInvalid) {
      setError('数量必须是正整数（最小交易单位的整数倍）');
      return;
    }
    if (!clientOrderId.trim()) {
      setError('请填写客户端订单号（重复提交不会新增订单）');
      return;
    }
    setSubmitting(true);
    try {
      const result = await api.place(clientOrderId.trim(), side, ticks!, qty);
      const tradeQty = result.trades.reduce((s, t) => s + t.quantity, 0);
      setSuccess(
        tradeQty > 0
          ? `已成交 ${tradeQty}，订单状态 ${result.order.status}`
          : `已报入排队，状态 ${result.order.status}`,
      );
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? `${e.code}: ${e.message}` : String(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="panel">
      <h2>
        下单（合成订单）
        <span className="pill">整数刻度</span>
      </h2>

      <div className="side-toggle">
        <button className={side === 'BUY' ? 'active-buy' : ''} onClick={() => setSide('BUY')}>
          买入 BUY
        </button>
        <button className={side === 'SELL' ? 'active-sell' : ''} onClick={() => setSide('SELL')}>
          卖出 SELL
        </button>
      </div>

      <label>客户端订单号 clientOrderId</label>
      <input
        value={clientOrderId}
        onChange={(e) => setClientOrderId(e.target.value)}
        placeholder="例如 S1"
        maxLength={64}
      />

      <label>限价（元，刻度 {tickSize}）</label>
      <input
        value={priceInput}
        onChange={(e) => setPriceInput(e.target.value)}
        inputMode="decimal"
        placeholder="10.00"
      />
      <div className="hint">
        {priceInvalid
          ? '⚠ 非法价格刻度'
          : `= ${ticks} 个价格刻度（撮合按整数 ${ticks} 比较，不用浮点）`}
        {ticks !== null ? `，显示 ${formatPrice(ticks, scale)}` : ''}
      </div>

      <label>数量（最小交易单位整数倍）</label>
      <input value={quantity} onChange={(e) => setQuantity(e.target.value)} inputMode="numeric" />

      <div className="row-buttons">
        <button className={side === 'SELL' ? 'danger' : ''} disabled={submitting} onClick={submit}>
          {side === 'BUY' ? '买入提交' : '卖出提交'}
        </button>
      </div>

      {error && <div className="error">{error}</div>}
      {success && <div className="success">{success}</div>}
    </div>
  );
}
