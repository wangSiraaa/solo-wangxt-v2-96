import type {
  Book,
  Conservation,
  Order,
  PlaceOrderResult,
  ReplayCommand,
  Settings,
  Side,
  Trade,
} from './types';

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    let code = `HTTP_${res.status}`;
    let message = res.statusText;
    try {
      const body = await res.json();
      code = body.error ?? code;
      message = body.message ?? message;
    } catch {
      /* 非 JSON 错误体 */
    }
    throw new ApiError(res.status, code, message);
  }
  return res.json() as Promise<T>;
}

export const api = {
  settings: () => request<Settings>('/api/settings'),
  book: () => request<Book>('/api/book'),
  orders: () => request<Order[]>('/api/orders'),
  trades: () => request<Trade[]>('/api/trades'),
  conservation: () => request<Conservation>('/api/conservation'),
  reset: () => request<{ status: string }>('/api/reset', { method: 'POST' }),
  place: (
    clientOrderId: string,
    side: Side,
    priceTicks: number,
    quantity: number,
  ) =>
    request<PlaceOrderResult>('/api/orders', {
      method: 'POST',
      body: JSON.stringify({ clientOrderId, side, priceTicks, quantity }),
    }),
  cancel: (clientOrderId: string) =>
    request<Order>(`/api/orders/${encodeURIComponent(clientOrderId)}/cancel`, {
      method: 'POST',
    }),
  runCommand: async (cmd: ReplayCommand): Promise<PlaceOrderResult | Order> =>
    cmd.type === 'PLACE'
      ? api.place(cmd.clientOrderId, cmd.side!, cmd.priceTicks!, cmd.quantity!)
      : api.cancel(cmd.clientOrderId),
};
