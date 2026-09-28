package com.tradinglab.matching;

import com.tradinglab.matching.api.ConservationResponse;
import com.tradinglab.matching.api.PlaceOrderRequest;
import com.tradinglab.matching.api.PlaceOrderResponse;
import com.tradinglab.matching.api.TradeResponse;
import com.tradinglab.matching.engine.BookLevel;
import com.tradinglab.matching.engine.BookSnapshot;
import com.tradinglab.matching.service.DuplicateOrderException;
import com.tradinglab.matching.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 撮合规则端到端测试（H2 内存库，验证服务+引擎+持久化全链路）。
 */
@SpringBootTest
class MatchingRulesIntegrationTest {

    @Autowired
    private OrderService service;

    @BeforeEach
    void reset() {
        service.reset();
    }

    private PlaceOrderResponse place(String clientId, String side, long priceTicks, long qty) {
        return service.place(new PlaceOrderRequest(clientId, side, priceTicks, qty));
    }

    @Test
    @DisplayName("同价排队：同价位订单按时间优先 FIFO 成交")
    void samePriceFifoQueue() {
        place("S1", "SELL", 1000, 10);
        place("S2", "SELL", 1000, 10);
        place("S3", "SELL", 1000, 10);

        PlaceOrderResponse buy = place("B1", "BUY", 1000, 15);

        // 两笔成交：S1 全成 10，S2 部分成交 5
        List<TradeResponse> trades = buy.trades();
        assertEquals(2, trades.size());
        assertEquals("S1", trades.get(0).makerClientOrderId());
        assertEquals(10, trades.get(0).quantity());
        assertEquals("S2", trades.get(1).makerClientOrderId());
        assertEquals(5, trades.get(1).quantity());
        assertEquals("FILLED", buy.order().status());
        assertEquals(15, buy.order().filledQuantity());

        // 簿内同价位剩余 S2=5 + S3=10 = 15
        BookSnapshot book = service.book();
        assertEquals(1, book.asks().size());
        BookLevel ask = book.asks().get(0);
        assertEquals(1000, ask.priceTicks());
        assertEquals(15, ask.quantity());
        assertEquals(2, ask.orderCount());

        ConservationResponse c = service.conservation();
        assertTrue(c.conserved(), "数量应守恒: " + c);
    }

    @Test
    @DisplayName("吃掉两档报价：主动买单依次成交最低两个价位，第三档部分成交")
    void buyEatsTwoLevels() {
        place("S1", "SELL", 1000, 5);
        place("S2", "SELL", 1001, 5);
        place("S3", "SELL", 1002, 20);

        PlaceOrderResponse buy = place("B1", "BUY", 1002, 15);

        List<TradeResponse> trades = buy.trades();
        assertEquals(3, trades.size());
        // 价格优先：1000 -> 1001 -> 1002；成交价取 maker 价（买单获得价格改善）
        assertEquals(1000, trades.get(0).priceTicks());
        assertEquals(5, trades.get(0).quantity());
        assertEquals(1001, trades.get(1).priceTicks());
        assertEquals(5, trades.get(1).quantity());
        assertEquals(1002, trades.get(2).priceTicks());
        assertEquals(5, trades.get(2).quantity());
        assertEquals("S3", trades.get(2).makerClientOrderId());
        assertEquals("FILLED", buy.order().status());

        // 第三档剩余 15 留在卖一
        BookSnapshot book = service.book();
        assertEquals(1, book.asks().size());
        assertEquals(1002, book.asks().get(0).priceTicks());
        assertEquals(15, book.asks().get(0).quantity());
        assertTrue(service.conservation().conserved());
    }

    @Test
    @DisplayName("部分成交后撤单：保留已成交部分，撤销剩余数量")
    void partialFillThenCancel() {
        place("B1", "BUY", 990, 10);
        PlaceOrderResponse sell = place("S1", "SELL", 990, 4);

        assertEquals(1, sell.trades().size());
        assertEquals(4, sell.trades().get(0).quantity());
        assertEquals("FILLED", sell.order().status());

        var orders = service.listOrders();
        var buyOrder = orders.stream().filter(o -> o.clientOrderId().equals("B1")).findFirst().orElseThrow();
        assertEquals("PARTIALLY_FILLED", buyOrder.status());
        assertEquals(4, buyOrder.filledQuantity());
        assertEquals(6, buyOrder.remainingQuantity());

        var cancelled = service.cancelByClientOrderId("B1");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(4, cancelled.filledQuantity());
        assertEquals(0, cancelled.remainingQuantity());

        // 撤单后撮合簿清空，无法再与它成交
        PlaceOrderResponse sell2 = place("S2", "SELL", 990, 6);
        assertTrue(sell2.trades().isEmpty());
        assertEquals("NEW", sell2.order().status());

        ConservationResponse c = service.conservation();
        assertEquals(4, c.totalTradeVolume());
        assertEquals(4, c.buyFilledQuantity());
        assertEquals(4, c.sellFilledQuantity());
        assertTrue(c.conserved());
    }

    @Test
    @DisplayName("订单号重复提交不能新增订单，返回冲突")
    void duplicateClientOrderIdRejected() {
        place("DUP1", "BUY", 1000, 3);
        long before = service.listOrders().size();

        assertThrows(DuplicateOrderException.class,
                () -> place("DUP1", "BUY", 1001, 5));

        long after = service.listOrders().size();
        assertEquals(before, after, "重复提交不得新增订单");
        // 原订单内容不变
        var original = service.listOrders().stream()
                .filter(o -> o.clientOrderId().equals("DUP1")).findFirst().orElseThrow();
        assertEquals(1000, original.priceTicks());
        assertEquals(3, original.quantity());
    }

    @Test
    @DisplayName("价格不交叉不成交，订单进入对应盘口排队")
    void noCrossNoTrade() {
        place("B1", "BUY", 999, 7);
        place("S1", "SELL", 1000, 8);

        assertTrue(service.listTrades().isEmpty(), "价格不交叉不应有成交");
        BookSnapshot book = service.book();
        assertEquals(999, book.bids().get(0).priceTicks());
        assertEquals(7, book.bids().get(0).quantity());
        assertEquals(1000, book.asks().get(0).priceTicks());
        assertEquals(8, book.asks().get(0).quantity());
    }

    @Test
    @DisplayName("不能撤销已全部成交的订单")
    void cannotCancelFilledOrder() {
        place("S1", "SELL", 1000, 2);
        place("B1", "BUY", 1000, 2);
        assertThrows(IllegalArgumentException.class, () -> service.cancelByClientOrderId("S1"));
        assertTrue(service.conservation().conserved());
    }
}
