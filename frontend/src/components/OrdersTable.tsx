import type { Order, OrderStatus } from '../types';

const STATUS_LABEL: Record<OrderStatus, string> = {
  NEW: '待成交',
  PARTIALLY_FILLED: '部分成交',
  FILLED: '全部成交',
  CANCELLED: '已撤单',
};

interface Props {
  orders: Order[];
  onCancel: (id: number) => Promise<void>;
  busy: boolean;
}

export default function OrdersTable({ orders, onCancel, busy }: Props) {
  return (
    <section className="card">
      <h2>订单状态</h2>
      <table className="data">
        <thead>
          <tr>
            <th className="num">#</th>
            <th>clientOrderId</th>
            <th>方向</th>
            <th>限价 (tick)</th>
            <th className="num">原始量</th>
            <th className="num">已成交</th>
            <th className="num">在簿剩余</th>
            <th>状态</th>
            <th>时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {orders.map((o) => {
            const live = o.status === 'NEW' || o.status === 'PARTIALLY_FILLED';
            return (
              <tr key={o.id} className={`status-${o.status.toLowerCase()}`}>
                <td className="num mono">{o.id}</td>
                <td className="mono">{o.clientOrderId}</td>
                <td className={o.side === 'BUY' ? 'buy-text' : 'sell-text'}>
                  {o.side === 'BUY' ? '买入' : '卖出'}
                </td>
                <td>
                  {o.limitPrice} <span className="dim mono">({o.limitPriceTicks})</span>
                </td>
                <td className="num">{o.origQty}</td>
                <td className="num">{o.filledQty}</td>
                <td className="num">{o.remainingQty}</td>
                <td><span className={`badge ${o.status.toLowerCase()}`}>{STATUS_LABEL[o.status]}</span></td>
                <td className="dim">{new Date(o.createdAt).toLocaleTimeString('zh-CN')}</td>
                <td>
                  {live && (
                    <button className="mini" disabled={busy} onClick={() => onCancel(o.id)}>
                      撤剩余 {o.remainingQty}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
          {orders.length === 0 && (
            <tr><td colSpan={10} className="empty">暂无订单</td></tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
