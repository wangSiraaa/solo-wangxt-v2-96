import type { OrderBook as OrderBookType } from '../types';

interface Props {
  book: OrderBookType;
}

function maxQty(book: OrderBookType): number {
  return Math.max(1, ...book.bids.map((l) => l.qty), ...book.asks.map((l) => l.qty));
}

/**
 * 买卖盘深度：卖盘按价格升序自上而下（最优卖价在底部、靠近中点），
 * 买盘按价格降序（最优买价在顶部、靠近中点）。每行同时显示累计量与订单数。
 */
export default function OrderBookView({ book }: Props) {
  const asksTopDown = [...book.asks].reverse();
  const max = maxQty(book);

  return (
    <section className="card">
      <h2>买卖盘深度 · {book.symbol}</h2>
      <div className="book-grid">
        <table className="book asks">
          <thead>
            <tr><th>卖价</th><th className="num">数量</th><th className="num">累计</th><th className="num">单数</th></tr>
          </thead>
          <tbody>
            {asksTopDown.map((lvl, i) => (
              <DepthRow key={lvl.priceTicks} price={lvl.price} qty={lvl.qty} count={lvl.orderCount}
                        max={max} kind="ask" cum={cumQty(asksTopDown.slice(i))} />
            ))}
            {asksTopDown.length === 0 && <tr><td colSpan={4} className="empty">卖盘为空</td></tr>}
          </tbody>
        </table>
        <div className="spread">── 价格优先 / 同价时间优先 ──</div>
        <table className="book bids">
          <tbody>
            {book.bids.map((lvl, i) => (
              <DepthRow key={lvl.priceTicks} price={lvl.price} qty={lvl.qty} count={lvl.orderCount}
                        max={max} kind="bid" cum={cumQty(book.bids.slice(0, i + 1))} />
            ))}
            {book.bids.length === 0 && <tr><td colSpan={4} className="empty">买盘为空</td></tr>}
          </tbody>
          <tfoot>
            <tr><th>买价</th><th className="num">数量</th><th className="num">累计</th><th className="num">单数</th></tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}

function cumQty(levels: { qty: number }[]): number {
  return levels.reduce((a, l) => a + l.qty, 0);
}

function DepthRow(props: {
  price: string; qty: number; count: number; max: number; kind: 'bid' | 'ask'; cum: number;
}) {
  const width = Math.round((props.qty / props.max) * 100);
  return (
    <tr className={props.kind}>
      <td>
        <span className="bar-wrap">
          <span className={`bar ${props.kind}`} style={{ width: `${width}%` }} />
          {props.price}
        </span>
      </td>
      <td className="num strong">{props.qty}</td>
      <td className="num dim">{props.cum}</td>
      <td className="num dim">{props.count}</td>
    </tr>
  );
}
