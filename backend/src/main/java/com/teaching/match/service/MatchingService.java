package com.teaching.match.service;

import com.teaching.match.api.DtoMapper;
import com.teaching.match.api.ExecutionDto;
import com.teaching.match.api.OrderDto;
import com.teaching.match.api.PlaceOrderRequest;
import com.teaching.match.api.PlaceOrderResult;
import com.teaching.match.config.MarketProperties;
import com.teaching.match.domain.Execution;
import com.teaching.match.domain.Order;
import com.teaching.match.domain.OrderStatus;
import com.teaching.match.domain.Side;
import com.teaching.match.repo.ExecutionRepository;
import com.teaching.match.repo.OrderRepository;
import com.teaching.match.support.TickRules;
import com.teaching.match.web.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 连续竞价撮合引擎（首版单标的、合成订单）。
 *
 * 规则：
 *  - 价格优先：买单先吃最低卖价，卖单先吃最高买价；
 *  - 同价时间优先：created_at（id 兜底同毫秒）升序；
 *  - 支持部分成交；剩余可撤单；
 *  - clientOrderId 唯一：重复提交直接返回原订单，不会新增订单；
 *  - 价格/数量全部为 long 整数（tick 刻度），不做任何浮点比较。
 *
 * 并发：单标的下所有写动作经过同一把公平锁串行化；每个动作独立事务提交。
 */
@Service
public class MatchingService {

    private static final List<OrderStatus> LIVE = List.of(OrderStatus.NEW, OrderStatus.PARTIALLY_FILLED);

    private final OrderRepository orders;
    private final ExecutionRepository executions;
    private final TransactionTemplate txTemplate;
    private final DtoMapper mapper;
    private final MarketProperties props;

    private final long ticksPerUnit;
    /** 撮合轮询每页拉取的对手方数量，足以跨越两档（教学场景深度很小）。 */
    private static final int MATCH_PAGE = 100;
    private final java.util.concurrent.locks.Lock lock =
            new java.util.concurrent.locks.ReentrantLock(true);

    public MatchingService(OrderRepository orders,
                           ExecutionRepository executions,
                           TransactionTemplate txTemplate,
                           DtoMapper mapper,
                           MarketProperties props) {
        this.orders = orders;
        this.executions = executions;
        this.txTemplate = txTemplate;
        this.mapper = mapper;
        this.props = props;
        this.ticksPerUnit = TickRules.ticksPerUnit(props.tickSize());
    }

    /* ============================== 下单 / 撮合 ============================== */

    public PlaceOrderResult placeOrder(PlaceOrderRequest req) {
        if (!props.symbol().equals(req.symbol())) {
            throw ApiException.badRequest(
                    "only symbol " + props.symbol() + " is supported, got: " + req.symbol());
        }
        long priceTicks;
        try {
            priceTicks = TickRules.priceToTicks(req.limitPrice(), ticksPerUnit);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        long qty = req.quantity();
        if (qty <= 0) {
            throw ApiException.badRequest("quantity must be positive");
        }
        if (props.lotSize() > 1 && qty % props.lotSize() != 0) {
            throw ApiException.badRequest("quantity must be a multiple of lot size " + props.lotSize());
        }
        if (req.clientOrderId() == null || req.clientOrderId().isBlank()) {
            throw ApiException.badRequest("clientOrderId is required");
        }

        lock.lock();
        try {
            return txTemplate.execute(status -> doPlace(req, priceTicks, qty));
        } finally {
            lock.unlock();
        }
    }

    private PlaceOrderResult doPlace(PlaceOrderRequest req, long priceTicks, long qty) {
        // 幂等：重复的 clientOrderId 不新增订单，只回传原订单与已有成交
        Order existing = orders.findByClientOrderId(req.clientOrderId()).orElse(null);
        if (existing != null) {
            List<ExecutionDto> prior = executions
                    .findByMakerOrderIdOrTakerOrderIdOrderByExecutedAtAscIdAsc(existing.getId(), existing.getId())
                    .stream().map(mapper::execution).toList();
            return new PlaceOrderResult(mapper.order(existing), prior, true, conservationFor(existing));
        }

        Order taker = new Order(req.clientOrderId(), req.symbol(), req.side(), priceTicks, qty);
        orders.saveAndFlush(taker);

        List<Execution> fills = match(taker);

        // 重新读取以拿到 @CreationTimestamp
        Order refreshed = orders.findById(taker.getId()).orElseThrow();
        List<ExecutionDto> fillDtos = fills.stream().map(mapper::execution).toList();
        return new PlaceOrderResult(mapper.order(refreshed), fillDtos, false, conservationFor(refreshed));
    }

    private List<Execution> match(Order taker) {
        List<Execution> created = new ArrayList<>();
        while (taker.remainingQty() > 0) {
            List<Order> candidates = taker.getSide() == Side.BUY
                    ? orders.findAskCandidates(taker.getSymbol(), Side.SELL, LIVE,
                            taker.getLimitPriceTicks(), PageRequest.of(0, MATCH_PAGE))
                    : orders.findBidCandidates(taker.getSymbol(), Side.BUY, LIVE,
                            taker.getLimitPriceTicks(), PageRequest.of(0, MATCH_PAGE));
            if (candidates.isEmpty()) {
                break;
            }
            boolean progressed = false;
            for (Order maker : candidates) {
                if (taker.remainingQty() == 0) {
                    break;
                }
                if (!canCross(taker, maker)) {
                    break; // 已按价格排序，后面更不可能成交
                }
                long fillQty = Math.min(taker.remainingQty(), maker.remainingQty());
                long fillPrice = maker.getLimitPriceTicks(); // 成交价取挂单价（被动方价格）
                String tradeId = nextTradeId();

                taker.applyFill(fillQty);
                maker.applyFill(fillQty);

                Execution execution = new Execution(
                        tradeId, taker.getSymbol(), fillPrice, fillQty,
                        maker.getId(), taker.getId(), maker.getSide());
                executions.save(execution);
                created.add(execution);
                progressed = true;
            }
            orders.flush();
            if (!progressed) {
                break;
            }
        }
        return created;
    }

    private boolean canCross(Order taker, Order maker) {
        return taker.getSide() == Side.BUY
                ? taker.getLimitPriceTicks() >= maker.getLimitPriceTicks()
                : taker.getLimitPriceTicks() <= maker.getLimitPriceTicks();
    }

    /* ============================== 撤单 ============================== */

    public OrderDto cancel(long orderId) {
        lock.lock();
        try {
            return txTemplate.execute(status -> {
                Order order = orders.findById(orderId)
                        .orElseThrow(() -> ApiException.notFound("order not found: " + orderId));
                if (order.getStatus() == OrderStatus.FILLED) {
                    throw ApiException.conflict("order " + orderId + " already FILLED, cannot cancel");
                }
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    throw ApiException.conflict("order " + orderId + " already CANCELLED");
                }
                order.cancel();
                orders.saveAndFlush(order);
                return mapper.order(order);
            });
        } finally {
            lock.unlock();
        }
    }

    /* ============================== 查询 ============================== */

    @Transactional(readOnly = true)
    public List<OrderDto> listOrders() {
        return orders.findBySymbolOrderByCreatedAtDescIdDesc(props.symbol())
                .stream().map(mapper::order).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(long id) {
        return orders.findById(id).map(mapper::order)
                .orElseThrow(() -> ApiException.notFound("order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ExecutionDto> listExecutions() {
        return executions.findBySymbolOrderByExecutedAtDescIdDesc(props.symbol())
                .stream().map(mapper::execution).toList();
    }

    @Transactional(readOnly = true)
    public PlaceOrderResult.Conservation globalConservation() {
        List<Order> all = orders.findAll();
        long orig = all.stream().mapToLong(Order::getOrigQty).sum();
        long filled = all.stream().mapToLong(Order::getFilledQty).sum();
        long cancelled = all.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .mapToLong(Order::remainingQty).sum();
        long book = all.stream().filter(Order::isLive).mapToLong(Order::remainingQty).sum();
        boolean balanced = orig == filled + cancelled + book;
        return new PlaceOrderResult.Conservation(orig, filled, cancelled, book, balanced);
    }

    private PlaceOrderResult.Conservation conservationFor(Order o) {
        long cancelledQty = o.getStatus() == OrderStatus.CANCELLED ? o.remainingQty() : 0;
        long bookQty = o.isLive() ? o.remainingQty() : 0;
        boolean balanced = o.getOrigQty() == o.getFilledQty() + cancelledQty + bookQty;
        return new PlaceOrderResult.Conservation(o.getOrigQty(), o.getFilledQty(), cancelledQty, bookQty, balanced);
    }

    private String nextTradeId() {
        Long seq = executions.nextTradeSequence();
        return String.format("T%06d", seq);
    }
}
