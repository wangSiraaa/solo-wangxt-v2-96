export type Side = 'BUY' | 'SELL';
export type OrderStatus = 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED';

export interface Order {
  id: number;
  clientOrderId: string;
  symbol: string;
  side: Side;
  limitPriceTicks: number;
  limitPrice: string;
  origQty: number;
  filledQty: number;
  remainingQty: number;
  status: OrderStatus;
  createdAt: string;
}

export interface Trade {
  id: number;
  tradeId: string;
  symbol: string;
  priceTicks: number;
  price: string;
  qty: number;
  makerOrderId: number;
  takerOrderId: number;
  makerSide: Side;
  executedAt: string;
}

export interface BookLevel {
  priceTicks: number;
  price: string;
  qty: number;
  orderCount: number;
}

export interface OrderBook {
  symbol: string;
  tickSizeDisplay: number;
  bids: BookLevel[];
  asks: BookLevel[];
}

export interface Conservation {
  origQty: number;
  filledQty: number;
  cancelledQty: number;
  bookQty: number;
  balanced: boolean;
}

export interface PlaceOrderResult {
  order: Order;
  executions: Trade[];
  deduplicated: boolean;
  conservation: Conservation;
}

export interface Instrument {
  symbol: string;
  tickSize: string;
  lotSize: number;
}

export interface Snapshot {
  instrument: Instrument;
  book: OrderBook;
  orders: Order[];
  trades: Trade[];
  conservation: Conservation;
}
