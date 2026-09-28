import type { Trade } from '../types';
import { formatPrice, formatTime } from '../format';

interface Props {
  trades: Trade[];
  scale: number;
}

/** 成交明细，最新在前。 */
export function TradeList({ trades, scale }: Props) {
  return (
    <div className="panel">
      <h2>成交明细 <span className="pill">{trades.length} 笔</span></h2>
      <div className="scrollbox">
        <table>
          <thead>
            <tr>
              <th className="l">时间</th>
              <th>价格</th>
              <th>数量</th>
              <th className="l">主动方</th>
              <th className="l">被动方</th>
            </tr>
          </thead>
          <tbody>
            {trades.length === 0 && (
              <tr><td colSpan={5} className="empty">暂无成交</td></tr>
            )}
            {trades.map((t) => (
              <tr key={t.id}>
                <td className="l mono">{formatTime(t.createdAt)}</td>
                <td className="mono">{formatPrice(t.priceTicks, scale)}</td>
                <td>{t.quantity}</td>
                <td className={`l side-${t.takerSide}`}>
                  {t.takerSide === 'BUY' ? '买' : '卖'} {t.takerClientOrderId}
                </td>
                <td className="l">{t.makerClientOrderId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
