import type { Reconciliation } from '../reconcile';
import type { Conservation } from '../types';

interface Props {
  conservation: Conservation;
  reconciliation: Reconciliation;
}

function Check({ ok, label, detail }: { ok: boolean; label: string; detail: string }) {
  return (
    <div className={`check ${ok ? 'ok' : 'bad'}`}>
      <span className="check-icon">{ok ? '✓' : '✗'}</span>
      <span className="check-label">{label}</span>
      <span className="check-detail">{detail}</span>
    </div>
  );
}

/**
 * 数量守恒核对条：
 *  原始下单总量 = 已成交（双边计数，一笔成交买卖各记一次）+ 已撤剩余 + 在簿剩余；
 *  另外交叉核对：成交记录单边总量 ×2 = 已成交量；
 *  订单明细 LIVE 剩余量 = 深度聚合量。
 */
export default function ConservationBar({ conservation: c, reconciliation: r }: Props) {
  const eq = c.filledQty + c.cancelledQty + c.bookQty === c.origQty;
  return (
    <section className={`card conservation ${r.allBalanced ? '' : 'alarm'}`}>
      <h2>数量守恒核对</h2>
      <div className="equation">
        原始下单总量 <b>{c.origQty}</b>
        {' = '}
        已成交(双边) <b>{c.filledQty}</b>
        {' + '}
        已撤剩余 <b>{c.cancelledQty}</b>
        {' + '}
        在簿剩余 <b>{c.bookQty}</b>
        <span className="eq-result">{eq ? '✓ 守恒' : '✗ 失衡'}</span>
      </div>
      <div className="checks">
        <Check ok={r.serverBalanced} label="服务端守恒式"
               detail={`${c.origQty} = ${c.filledQty} + ${c.cancelledQty} + ${c.bookQty}`} />
        <Check ok={r.tradeLegMatches} label="成交记录对账"
               detail={`成交明细单边 ${r.tradeLegQty} × 2 = ${c.filledQty}`} />
        <Check ok={r.bidDepthMatches} label="买盘深度一致"
               detail={`聚合档 ${r.bidDepthQty} vs 订单 LIVE 剩余 ${r.liveBidFromOrders}`} />
        <Check ok={r.askDepthMatches} label="卖盘深度一致"
               detail={`聚合档 ${r.askDepthQty} vs 订单 LIVE 剩余 ${r.liveAskFromOrders}`} />
      </div>
    </section>
  );
}
