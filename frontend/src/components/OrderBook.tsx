import type { Book } from '../types';
import { formatPrice } from '../format';

interface Props {
  book: Book;
  scale: number;
}

/**
 * 买卖盘深度。卖盘在上、价格从高到低展示（靠近中间是最低卖价）；
 * 买盘在下、价格从高到低（靠近中间是最高买价）。
 */
export function OrderBook({ book, scale }: Props) {
  const maxQty = Math.max(
    1,
    ...book.bids.map((b) => b.quantity),
    ...book.asks.map((a) => a.quantity),
  );

  const asksTopDown = [...book.asks].reverse();

  const Row = (
    level: { priceTicks: number; quantity: number; orderCount: number },
    cls: string,
  ) => (
    <tr key={`${cls}-${level.priceTicks}`} className={`${cls} depth-bar`}>
      <td className="price l mono">{formatPrice(level.priceTicks, scale)}</td>
      <td>{level.quantity}</td>
      <td>{level.orderCount}</td>
      <td
        className="bar"
        style={{ width: `${(level.quantity / maxQty) * 100}%` }}
        aria-hidden
      />
    </tr>
  );

  const spread =
    book.asks.length > 0 && book.bids.length > 0
      ? book.asks[0].priceTicks - book.bids[0].priceTicks
      : null;

  return (
    <div className="panel">
      <h2>
        买卖盘深度
        {spread !== null && <span className="pill mono">价差 {spread} 刻度</span>}
      </h2>
      <table>
        <thead>
          <tr>
            <th className="l">价格</th>
            <th>数量</th>
            <th>挂单数</th>
            <th style={{ width: '40%' }} />
          </tr>
        </thead>
        <tbody>
          {asksTopDown.length === 0 && (
            <tr><td colSpan={4} className="empty">卖盘为空</td></tr>
          )}
          {asksTopDown.map((l) => Row(l, 'ask-row'))}
          <tr>
            <td colSpan={4} style={{ borderTop: '2px solid var(--border)', padding: 2 }} />
          </tr>
          {book.bids.length === 0 && (
            <tr><td colSpan={4} className="empty">买盘为空</td></tr>
          )}
          {book.bids.map((l) => Row(l, 'bid-row'))}
        </tbody>
      </table>
    </div>
  );
}
