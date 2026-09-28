package com.tradinglab.matching.service;

import com.tradinglab.matching.api.ConservationResponse;
import com.tradinglab.matching.api.OrderResponse;
import com.tradinglab.matching.api.PlaceOrderRequest;
import com.tradinglab.matching.api.PlaceOrderResponse;
import com.tradinglab.matching.api.TradeResponse;
import com.tradinglab.matching.domain.Order;
import com.tradinglab.matching.domain.OrderStatus;
import com.tradinglab.matching.domain.Side;
import com.tradinglab.matching.domain.Trade;
import com.tradinglab.matching.engine.BookSnapshot;
import com.tradinglab.matching.engine.MatchResult;
import com.tradinglab.matching.engine.MatchingEngine;
import com.tradinglab.matching.repo.OrderRepository;
import com.tradinglab.matching.repo.TradeRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED);

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final MatchingEngine engine;
    private final EntityManager entityManager;

    private final String symbol;
    private final long lotSize;

    public OrderService(OrderRepository orderRepository,
                        TradeRepository tradeRepository,
                        MatchingEngine engine,
                        EntityManager entityManager,
                        @Value("${matching.symbol}") String symbol,
                        @Value("${matching.lot-size:1}") long lotSize) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.engine = engine;
        this.entityManager = entityManager;
        this.symbol = symbol;
        this.lotSize = lotSize;
    }

    /**
     * 应用启动后用数据库内仍活跃的订单重建内存撮合簿。
     */
    @Transactional(readOnly = true)
    public void rebuildEngine() {
        List<Order> active = orderRepository
                .findBySymbolAndStatusInOrderByPriceTicksAscSeqAsc(symbol, ACTIVE);
        engine.rebuild(active);
    }

    /**
     * 下单并立即参与连续竞价。整个方法串行执行，保证同价时间优先的顺序确定。
     */
    @Transactional
    public synchronized PlaceOrderResponse place(PlaceOrderRequest req) {
        // 1. 客户端订单号去重：重复提交不能新增订单（DB 唯一约束兜底）。
        if (orderRepository.existsByClientOrderId(req.clientOrderId())) {
            throw new DuplicateOrderException(req.clientOrderId());
        }

        Side side;
        try {
            side = Side.valueOf(req.side());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("side 必须为 BUY 或 SELL");
        }
        if (req.quantity() % lotSize != 0) {
            throw new IllegalArgumentException("quantity 必须是最小交易单位 " + lotSize + " 的整数倍");
        }

        // 2. 先落库拿到订单 ID（此时状态 NEW），再进引擎撮合。
        //    先清空持久化上下文：引擎在 maker 对象上原地更新成交，事务提交时
        //    不能让一级缓存里的旧快照把这些更新覆盖回去。
        entityManager.clear();
        Order taker = new Order();
        taker.setClientOrderId(req.clientOrderId());
        taker.setSymbol(symbol);
        taker.setSide(side);
        taker.setPriceTicks(req.priceTicks());
        taker.setQuantity(req.quantity());
        taker.setFilledQuantity(0);
        taker.setStatus(OrderStatus.NEW);
        taker.setSeq(0L);
        taker = orderRepository.saveAndFlush(taker);

        // 3. 内存撮合（整数价格比较；价格优先、同价时间优先；允许部分成交）。
        MatchResult result = engine.match(taker, symbol);

        // 4. 成交与订单最终状态落库（taker + 所有被成交的 maker 都需保存）。
        tradeRepository.saveAll(result.trades());
        orderRepository.saveAll(result.touchedMakers());
        taker = orderRepository.save(taker);
        return new PlaceOrderResponse(toResponse(taker),
                result.trades().stream().map(this::toTradeResponse).toList());
    }

    /**
     * 撤销订单剩余数量。已成交部分保留；全部成交或已撤订单不可再撤。
     */
    @Transactional
    public synchronized OrderResponse cancelByClientOrderId(String clientOrderId) {
        Order order = orderRepository.findByClientOrderId(clientOrderId)
                .orElseThrow(() -> new OrderNotFoundException(clientOrderId));
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("订单当前状态为 " + order.getStatus() + "，不可撤销");
        }
        // 以引擎内的权威订单对象为准（含最新已成交数量），先清除其持久化快照再合并落库
        entityManager.clear();
        Order cancelled = engine.cancelById(order.getId());
        if (cancelled == null) {
            throw new IllegalArgumentException("订单不在撮合簿中，无法撤销");
        }
        return toResponse(orderRepository.save(cancelled));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders() {
        return orderRepository.findBySymbolOrderByIdDesc(symbol).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> listTrades() {
        return tradeRepository.findBySymbolOrderByIdDesc(symbol).stream()
                .map(this::toTradeResponse)
                .toList();
    }

    public BookSnapshot book() {
        return engine.snapshot();
    }

    /**
     * 数量守恒核对：
     * 每笔成交恰好让一个买单与一个卖单各减少相同的剩余量，
     * 因此所有订单累计成交量之和 = 2 × 成交总量，且买方成交量 = 卖方成交量 = 成交总量。
     */
    @Transactional(readOnly = true)
    public ConservationResponse conservation() {
        List<Order> orders = orderRepository.findBySymbolOrderByIdDesc(symbol);
        List<Trade> trades = tradeRepository.findBySymbolOrderByIdDesc(symbol);

        long totalTradeVolume = trades.stream().mapToLong(Trade::getQuantity).sum();
        long buyFilled = orders.stream().filter(o -> o.getSide() == Side.BUY)
                .mapToLong(Order::getFilledQuantity).sum();
        long sellFilled = orders.stream().filter(o -> o.getSide() == Side.SELL)
                .mapToLong(Order::getFilledQuantity).sum();
        long totalFilled = buyFilled + sellFilled;
        long activeCount = orders.stream().filter(Order::isActive).count();

        // balance 为 0 即守恒（两侧均与成交总量相等）
        long balance = (totalFilled - 2 * totalTradeVolume)
                + Math.abs(buyFilled - sellFilled);
        boolean conserved = balance == 0;

        return new ConservationResponse(
                trades.size(), totalTradeVolume, totalFilled,
                buyFilled, sellFilled, balance, conserved,
                activeCount, orders.size()
        );
    }

    /**
     * 清空订单与成交并重置撮合簿，供固定命令回放反复使用（仅限教学合成环境）。
     */
    @Transactional
    public synchronized void reset() {
        tradeRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        engine.clear();
    }

    private OrderResponse toResponse(Order o) {
        return new OrderResponse(
                o.getId(), o.getClientOrderId(), o.getSymbol(), o.getSide().name(),
                o.getPriceTicks(), o.getQuantity(), o.getFilledQuantity(),
                o.remainingQuantity(), o.getStatus().name(), o.getSeq(),
                o.getCreatedAt(), o.getUpdatedAt()
        );
    }

    private TradeResponse toTradeResponse(Trade t) {
        return new TradeResponse(
                t.getId(), t.getSymbol(), t.getMakerOrderId(), t.getMakerClientOrderId(),
                t.getTakerOrderId(), t.getTakerClientOrderId(), t.getTakerSide(),
                t.getPriceTicks(), t.getQuantity(), t.getCreatedAt()
        );
    }
}
