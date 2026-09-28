package com.tradinglab.matching.engine;

import com.tradinglab.matching.domain.Order;
import com.tradinglab.matching.domain.OrderStatus;
import com.tradinglab.matching.domain.Side;
import com.tradinglab.matching.domain.Trade;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 连续竞价撮合引擎（单标的、内存撮合簿）。
 *
 * 规则：
 * 1. 价格优先：买单价高者先成交，卖单价低者先成交；
 * 2. 同价时间优先：同一价位的订单按 seq（入簿顺序）FIFO 排队；
 * 3. 支持部分成交：taker 与队首 maker 按 min(双方剩余) 成交；
 * 4. taker 剩余可继续吃掉下一档，直到无可成交价位或自身耗尽；
 * 5. 未成交（或部分成交后剩余）的订单留在簿内排队；
 * 6. 成交价取 maker 报价。
 *
 * 全部比较基于 long 型价格刻度与数量，不存在浮点比较。
 */
@Component
public class MatchingEngine {

    /** 买单：价格降序。 */
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    /** 卖单：价格升序。 */
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();

    /** 簿内订单的唯一内存引用，orderId -> Order。 */
    private final Map<Long, Order> activeById = new HashMap<>();

    /** 入簿单调序号，重建后从历史最大 seq 继续。 */
    private long maxSeq = 0;

    /**
     * 用数据库中仍活跃的订单重建撮合簿（应用启动时调用一次）。
     */
    public synchronized void rebuild(List<Order> activeOrders) {
        bids.clear();
        asks.clear();
        activeById.clear();
        maxSeq = 0;
        for (Order o : activeOrders) {
            maxSeq = Math.max(maxSeq, o.getSeq());
            activeById.put(o.getId(), o);
            bookOf(o).computeIfAbsent(o.getPriceTicks(), k -> new ArrayDeque<>()).addLast(o);
        }
    }

    /**
     * 分配下一个排队序号。
     */
    public synchronized long nextSeq() {
        return ++maxSeq;
    }

    /**
     * 撮合一笔新进入的 taker 订单。
     *
     * @return MatchResult：产生的成交（可能为空）+ 本次被改动的 maker 订单。
     *         taker 与被成交 maker 的状态/数量在对象上被原地修改，调用方必须把它们一并持久化。
     */
    public synchronized MatchResult match(Order taker, String symbol) {
        List<Trade> trades = new ArrayList<>();
        List<Order> touchedMakers = new ArrayList<>();
        TreeMap<Long, Deque<Order>> opposite = taker.getSide() == Side.BUY ? asks : bids;

        while (taker.uncancelledRemaining() > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, Deque<Order>> best = opposite.firstEntry();
            long bestPrice = best.getKey();

            // 价格优先的成交条件（整数刻度比较）：
            // 买单限价必须 >= 最低卖价；卖单限价必须 <= 最高买价。
            boolean priceCrosses = taker.getSide() == Side.BUY
                    ? taker.getPriceTicks() >= bestPrice
                    : taker.getPriceTicks() <= bestPrice;
            if (!priceCrosses) {
                break;
            }

            Deque<Order> queue = best.getValue();
            // 同价时间优先：取队首（防御式跳过已不在簿的订单）
            while (!queue.isEmpty() && !queue.peekFirst().isActive()) {
                queue.pollFirst();
            }
            if (queue.isEmpty()) {
                opposite.remove(bestPrice);
                continue;
            }
            Order maker = queue.peekFirst();

            long fillQty = Math.min(taker.uncancelledRemaining(), maker.uncancelledRemaining());
            fill(taker, maker, fillQty);
            touchedMakers.add(maker);
            // 成交价用 maker 报价
            trades.add(new Trade(symbol, maker, taker, maker.getPriceTicks(), fillQty));

            if (maker.uncancelledRemaining() == 0) {
                queue.pollFirst();
                activeById.remove(maker.getId());
                if (queue.isEmpty()) {
                    opposite.remove(bestPrice);
                }
            }
        }

        // taker 仍有剩余 -> 进入自己一方的撮合簿排队
        if (taker.uncancelledRemaining() > 0) {
            taker.setSeq(nextSeq());
            activeById.put(taker.getId(), taker);
            bookOf(taker).computeIfAbsent(taker.getPriceTicks(), k -> new ArrayDeque<>()).addLast(taker);
        }
        return new MatchResult(trades, touchedMakers);
    }

    private void fill(Order taker, Order maker, long qty) {
        taker.setFilledQuantity(taker.getFilledQuantity() + qty);
        maker.setFilledQuantity(maker.getFilledQuantity() + qty);
        Instant now = Instant.now();
        taker.setUpdatedAt(now);
        maker.setUpdatedAt(now);
        taker.setStatus(taker.uncancelledRemaining() == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
        maker.setStatus(maker.uncancelledRemaining() == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
    }

    /**
     * 撤销订单的剩余数量。撤单成功返回该订单（已置 CANCELLED），否则返回 null。
     * 允许对部分成交后的订单撤剩余，已成交部分保留。
     */
    public synchronized Order cancel(Order order) {
        return cancelById(order.getId());
    }

    /**
     * 按订单 id 撤销簿内订单的剩余数量。
     */
    public synchronized Order cancelById(Long orderId) {
        Order active = activeById.get(orderId);
        if (active == null || !active.isActive()) {
            return null;
        }
        active.setStatus(OrderStatus.CANCELLED);
        active.setUpdatedAt(Instant.now());
        activeById.remove(active.getId());
        Deque<Order> queue = bookOf(active).get(active.getPriceTicks());
        if (queue != null) {
            queue.remove(active);
            if (queue.isEmpty()) {
                bookOf(active).remove(active.getPriceTicks());
            }
        }
        return active;
    }

    /**
     * 撮簿快照：按价位聚合，买单高价在前、卖单低价在前。
     */
    public synchronized BookSnapshot snapshot() {
        List<BookLevel> bidLevels = aggregate(bids);
        List<BookLevel> askLevels = aggregate(asks);
        return new BookSnapshot(bidLevels, askLevels);
    }

    private List<BookLevel> aggregate(TreeMap<Long, Deque<Order>> sideBook) {
        List<BookLevel> levels = new ArrayList<>();
        for (Map.Entry<Long, Deque<Order>> e : sideBook.entrySet()) {
            long total = 0;
            long orderCount = 0;
            for (Order o : e.getValue()) {
                if (o.isActive()) {
                    total += o.uncancelledRemaining();
                    orderCount++;
                }
            }
            if (total > 0) {
                levels.add(new BookLevel(e.getKey(), total, orderCount));
            }
        }
        return levels;
    }

    /**
     * 清空撮合簿（固定命令回放前调用）。
     */
    public synchronized void clear() {
        bids.clear();
        asks.clear();
        activeById.clear();
        maxSeq = 0;
    }

    public synchronized long activeCount() {
        return activeById.size();
    }

    private TreeMap<Long, Deque<Order>> bookOf(Order o) {
        return o.getSide() == Side.BUY ? bids : asks;
    }
}
