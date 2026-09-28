import type { Conservation } from '../types';

interface Props {
  conservation: Conservation | null;
}

/**
 * 数量守恒核对：
 *   总成交量（按成交记录） == 买方累计成交量 == 卖方累计成交量
 * 每笔成交让买卖双方各减少相同剩余量，balance 恒应为 0。
 */
export function ConservationPanel({ conservation }: Props) {
  if (!conservation) return null;
  const c = conservation;
  return (
    <div className="panel">
      <h2>
        数量守恒核对
        <span className={c.conserved ? 'badge-ok' : 'badge-bad'}>
          {c.conserved ? '✓ 守恒 balance=0' : `✗ 不平衡 balance=${c.balance}`}
        </span>
      </h2>
      <div className="conservation">
        <div className="item"><span>成交笔数</span><b>{c.tradeCount}</b></div>
        <div className="item"><span>订单总数</span><b>{c.totalOrderCount}</b></div>
        <div className="item"><span>总成交量(成交记录)</span><b>{c.totalTradeVolume}</b></div>
        <div className="item"><span>订单累计成交量</span><b>{c.totalFilledQuantity}</b></div>
        <div className="item"><span>买方累计成交</span><b>{c.buyFilledQuantity}</b></div>
        <div className="item"><span>卖方累计成交</span><b>{c.sellFilledQuantity}</b></div>
        <div className="item"><span>活跃簿内订单</span><b>{c.activeOrderCount}</b></div>
        <div className="item"><span>校验值 balance</span><b className={c.conserved ? 'badge-ok' : 'badge-bad'}>{c.balance}</b></div>
      </div>
      <div className="hint">
        恒等式：订单累计成交量 = 2 × 总成交量；买方成交 = 卖方成交 = 总成交量。
      </div>
    </div>
  );
}
