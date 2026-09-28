import type { Trade } from '../types';

export default function TradesTable({ trades }: { trades: Trade[] }) {
  return (
    <section className="card">
      <h2>成交明细</h2>
      <table className="data">
        <thead>
          <tr>
            <th>成交号</th><th>价格</th><th className="num">数量</th>
            <th className="num">吃单方</th><th className="num">挂单方订单</th><th>时间</th>
          </tr>
        </thead>
        <tbody>
          {trades.map((t) => (
            <tr key={t.id}>
              <td className="mono">{t.tradeId}</td>
              <td className="strong">{t.price}</td>
              <td className="num">{t.qty}</td>
              <td className="num">{t.makerSide === 'BUY' ? 'SELL ↑' : 'BUY ↓'}</td>
              <td className="num mono">#{t.makerOrderId} ↔ #{t.takerOrderId}</td>
              <td className="dim">{new Date(t.executedAt).toLocaleTimeString('zh-CN')}</td>
            </tr>
          ))}
          {trades.length === 0 && (
            <tr><td colSpan={6} className="empty">尚无成交 —— 运行固定命令回放即可看到撮合</td></tr>
          )}
        </tbody>
      </table>
    </section>
  );
}
