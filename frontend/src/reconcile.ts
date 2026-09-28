import type { Order, Side, Snapshot, Trade } from './types';

export interface Reconciliation {
  /** 服务端守恒：原始下单总量 = 已成交(双边) + 已撤剩余 + 在簿剩余 */
  serverBalanced: boolean;
  /** 成交记录单边总量应等于已成交量的一半（一笔成交同时消耗买卖各一份） */
  tradeLegQty: number;
  tradeLegMatches: boolean;
  /** 深度汇总的在簿量应与订单明细里 LIVE 剩余量一致 */
  bidDepthQty: number;
  askDepthQty: number;
  liveBidFromOrders: number;
  liveAskFromOrders: number;
  bidDepthMatches: boolean;
  askDepthMatches: boolean;
  allBalanced: boolean;
}

export function reconcile(s: Snapshot): Reconciliation {
  const live = s.orders.filter((o) => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED');
  const liveBidFromOrders = sum(live.filter((o) => o.side === 'BUY'), remaining);
  const liveAskFromOrders = sum(live.filter((o) => o.side === 'SELL'), remaining);

  const bidDepthQty = sum(s.book.bids, (l) => l.qty);
  const askDepthQty = sum(s.book.asks, (l) => l.qty);

  const tradeLegQty = sum(s.trades, (t) => t.qty);
  const tradeLegMatches = tradeLegQty * 2 === s.conservation.filledQty;

  const bidDepthMatches = bidDepthQty === liveBidFromOrders;
  const askDepthMatches = askDepthQty === liveAskFromOrders;

  return {
    serverBalanced: s.conservation.balanced,
    tradeLegQty,
    tradeLegMatches,
    bidDepthQty,
    askDepthQty,
    liveBidFromOrders,
    liveAskFromOrders,
    bidDepthMatches,
    askDepthMatches,
    allBalanced:
      s.conservation.balanced && tradeLegMatches && bidDepthMatches && askDepthMatches,
  };
}

export function describePlaceResult(order: Order, trades: Trade[], deduplicated: boolean): string {
  const parts: string[] = [];
  parts.push(deduplicated ? '幂等拦截，未新增订单' : '订单已受理');
  parts.push(`#${order.id} ${order.side} ${order.limitPrice} x${order.origQty}`);
  parts.push(`状态 ${order.status}，已成交 ${order.filledQty}，在簿剩余 ${order.remainingQty}`);
  if (trades.length > 0) {
    parts.push(
      `产生 ${trades.length} 笔成交：` +
        trades.map((t) => `${t.tradeId}@${t.price}x${t.qty}`).join('、'),
    );
  }
  return parts.join('；');
}

function sum<T>(items: T[], f: (x: T) => number): number {
  return items.reduce((acc, x) => acc + f(x), 0);
}

function remaining(o: Order): number {
  return o.remainingQty;
}

export function sideLabel(side: Side): string {
  return side === 'BUY' ? '买入' : '卖出';
}
