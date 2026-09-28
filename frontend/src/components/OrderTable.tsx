import type { Order } from '../types';
import { formatPrice } from '../format';

interface Props {
  orders: Order[];
  scale: number;
  onCancel: (clientOrderId: string) => void;
}

const STATUS_TEXT: Record<Order['status'], string> = {
  NEW: '已报入',
  PARTIALLY_FILLED: '部分成交',
  FILLED: '全部成交',
  CANCELLED: '已撤',
};

/** 全部订单状态，最新在前；活跃订单可撤销剩余数量。 */
export function OrderTable({ orders, scale, onCancel }: Props) {
  return (
    <div className="panel">
      <h2>订单状态 <span className="pill">{orders.length} 笔</span></h2>
      <div className="scrollbox">
        <table>
          <thead>
            <tr>
              <th className="l">订单号</th>
              <th>方向</th>
              <th>价格</th>
              <th>总量</th>
              <th>已成</th>
              <th>剩余</th>
              <th>排队</th>
              <th>状态</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {orders.length === 0 && (
              <tr><td colSpan={9} className="empty">暂无订单</td></tr>
            )}
            {orders.map((o) => (
              <tr key={o.id}>
                <td className="l mono">{o.clientOrderId}</td>
                <td className={`side-${o.side}`}>{o.side === 'BUY' ? '买' : '卖'}</td>
                <td className="mono">{formatPrice(o.priceTicks, scale)}</td>
                <td>{o.quantity}</td>
                <td>{o.filledQuantity}</td>
                <td>{o.remainingQuantity}</td>
                <td>{o.seq > 0 && (o.status === 'NEW' || o.status === 'PARTIALLY_FILLED') ? o.seq : '—'}</td>
                <td><span className={`status status-${o.status}`}>{STATUS_TEXT[o.status]}</span></td>
                <td>
                  {(o.status === 'NEW' || o.status === 'PARTIALLY_FILLED') && (
                    <button className="ghost" onClick={() => onCancel(o.clientOrderId)}>撤单</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
