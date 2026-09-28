import type { Order, PlaceOrderResult, Side, Snapshot } from './types';

const BASE = '/api';

async function handle<T>(resp: Response): Promise<T> {
  if (!resp.ok) {
    let message = `HTTP ${resp.status}`;
    try {
      const body = await resp.json();
      message = body.message || message;
    } catch {
      /* ignore */
    }
    throw new Error(message);
  }
  return resp.json() as Promise<T>;
}

export function fetchSnapshot(): Promise<Snapshot> {
  return fetch(`${BASE}/snapshot`).then(handle<Snapshot>);
}

export function placeOrder(input: {
  clientOrderId: string;
  symbol: string;
  side: Side;
  limitPrice: string;
  quantity: number;
}): Promise<PlaceOrderResult> {
  return fetch(`${BASE}/orders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  }).then(handle<PlaceOrderResult>);
}

export function cancelOrder(id: number): Promise<Order> {
  return fetch(`${BASE}/orders/${id}`, { method: 'DELETE' }).then(handle<Order>);
}

export function resetAll(): Promise<unknown> {
  return fetch(`${BASE}/admin/reset`, { method: 'POST' }).then(handle);
}
