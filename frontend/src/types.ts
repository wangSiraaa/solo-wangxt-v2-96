export type Side = 'BUY' | 'SELL';
export type OrderStatus = 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED';

export interface Settings {
  symbol: string;
  tickSize: string;
  tickScale: number;
  lotSize: number;
}

export interface BookLevel {
  priceTicks: number;
  quantity: number;
  orderCount: number;
}

export interface Book {
  bids: BookLevel[];
  asks: BookLevel[];
}

export interface Order {
  id: number;
  clientOrderId: string;
  symbol: string;
  side: Side;
  priceTicks: number;
  quantity: number;
  filledQuantity: number;
  remainingQuantity: number;
  status: OrderStatus;
  seq: number;
  createdAt: string;
  updatedAt: string;
}

export interface Trade {
  id: number;
  symbol: string;
  makerOrderId: number;
  makerClientOrderId: string;
  takerOrderId: number;
  takerClientOrderId: string;
  takerSide: Side;
  priceTicks: number;
  quantity: number;
  createdAt: string;
}

export interface PlaceOrderResult {
  order: Order;
  trades: Trade[];
}

export interface Conservation {
  tradeCount: number;
  totalTradeVolume: number;
  totalFilledQuantity: number;
  buyFilledQuantity: number;
  sellFilledQuantity: number;
  balance: number;
  conserved: boolean;
  activeOrderCount: number;
  totalOrderCount: number;
}

/** 回放命令：下单或撤单。 */
export interface ReplayCommand {
  /** 命令类型 */
  type: 'PLACE' | 'CANCEL';
  /** PLACE：客户端订单号；CANCEL：要撤的订单号 */
  clientOrderId: string;
  side?: Side;
  priceTicks?: number;
  quantity?: number;
  /** 教学注释，会显示在录制结果里 */
  note?: string;
  /** 该命令预期被拒绝（如重复订单号），真的被拒绝时该帧视为符合预期 */
  expectReject?: boolean;
  /** 预期的拒绝错误码 */
  expectErrorCode?: string;
}

/** 每执行一条命令后的页面快照（录制结果）。 */
export interface ReplayFrame {
  index: number;
  command: ReplayCommand;
  /** 命令结果是否符合脚本预期（预期拒绝且真的被拒绝也为 true） */
  ok: boolean;
  /** 是否是被服务端拒绝的一帧（含重复订单号等） */
  rejected: boolean;
  error?: string;
  order: Order | null;
  newTrades: Trade[];
  book: Book;
  conservation: Conservation;
}
