package com.tradinglab.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 限价订单。
 *
 * 价格以“刻度数”整数存储（priceTicks = 实际价格 / tickSize），
 * 数量以最小交易单位的整数倍存储，撮合过程全程整数比较，不出现浮点。
 *
 * seq 单调递增，代表进入撮合簿的先后顺序，用于实现同价时间优先（FIFO）。
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_symbol_side_status_price", columnList = "symbol,side,status,price_ticks"),
        @Index(name = "idx_orders_seq", columnList = "seq")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户端订单号，重复提交不能新增订单。 */
    @Column(name = "client_order_id", nullable = false, unique = true, length = 64)
    private String clientOrderId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Side side;

    /** 限价 = priceTicks * tickSize。 */
    @Column(name = "price_ticks", nullable = false)
    private long priceTicks;

    /** 下单总量。 */
    @Column(nullable = false)
    private long quantity;

    /** 累计成交量。 */
    @Column(name = "filled_quantity", nullable = false)
    private long filledQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;

    /** 簿内排队序号（仅进入订单簿的订单需要），越小越早。 */
    @Column(nullable = false)
    private long seq;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** 剩余有效数量：撤单后剩余已离开撮合簿，对外视为 0。 */
    public long remainingQuantity() {
        if (status == OrderStatus.CANCELLED) {
            return 0;
        }
        return quantity - filledQuantity;
    }

    /** 撤单/成交发生前的原始剩余（quantity - filledQuantity），不区分状态。 */
    public long uncancelledRemaining() {
        return quantity - filledQuantity;
    }

    public boolean isActive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public long getPriceTicks() {
        return priceTicks;
    }

    public void setPriceTicks(long priceTicks) {
        this.priceTicks = priceTicks;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getFilledQuantity() {
        return filledQuantity;
    }

    public void setFilledQuantity(long filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
