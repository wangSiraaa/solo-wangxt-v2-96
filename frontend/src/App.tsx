import { useCallback, useEffect, useState } from 'react';
import type { Book, Conservation, Order, Settings, Trade } from './types';
import { api } from './api';
import { OrderForm } from './components/OrderForm';
import { OrderBook } from './components/OrderBook';
import { TradeList } from './components/TradeList';
import { OrderTable } from './components/OrderTable';
import { ConservationPanel } from './components/ConservationPanel';
import { ReplayPanel } from './components/ReplayPanel';

export default function App() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [book, setBook] = useState<Book>({ bids: [], asks: [] });
  const [orders, setOrders] = useState<Order[]>([]);
  const [trades, setTrades] = useState<Trade[]>([]);
  const [conservation, setConservation] = useState<Conservation | null>(null);

  const refresh = useCallback(async () => {
    const [b, o, t, c] = await Promise.all([
      api.book(),
      api.orders(),
      api.trades(),
      api.conservation(),
    ]);
    setBook(b);
    setOrders(o);
    setTrades(t);
    setConservation(c);
  }, []);

  useEffect(() => {
    api.settings().then(setSettings);
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 2500);
    return () => clearInterval(timer);
  }, [refresh]);

  const cancel = async (clientOrderId: string) => {
    await api.cancel(clientOrderId);
    refresh();
  };

  const scale = settings?.tickScale ?? 2;

  return (
    <div className="app">
      <div className="app-header">
        <h1>限价订单撮合教学台</h1>
        <span className="symbol">{settings?.symbol ?? '…'}</span>
        <span className="pill">连续竞价 · 合成订单 · 不接真实市场</span>
      </div>
      <p className="subtitle">
        价格优先、同价时间优先；支持部分成交与剩余撤单；价格与数量按整数刻度（tickSize={settings?.tickSize ?? '…'}）比较，不使用浮点。
      </p>

      <ReplayPanel scale={scale} onTick={refresh} />

      <div className="layout">
        <div className="col-left">
          <OrderForm
            scale={scale}
            tickSize={settings?.tickSize ?? '0.01'}
            onChanged={refresh}
          />
          <ConservationPanel conservation={conservation} />
        </div>

        <div className="col-center">
          <OrderBook book={book} scale={scale} />
          <TradeList trades={trades} scale={scale} />
        </div>

        <div className="col-right">
          <OrderTable orders={orders} scale={scale} onCancel={cancel} />
        </div>
      </div>
    </div>
  );
}
