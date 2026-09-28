package com.teaching.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 调用方提供的幂等键；重复提交不能新增订单 */
    @Column(name = "client_order_id", nullable = false, unique = true, updatable = false)
    private String clientOrderId;

    @Column(nullable = false, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Side side;

    @Column(name = "limit_price_ticks", nullable = false, updatable = false)
    private long limitPriceTicks;

    @Column(name = "orig_qty", nullable = false, updatable = false)
    private long origQty;

    @Column(name = "filled_qty", nullable = false)
    private long filledQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    private long version;

    public Order() {
    }

    public Order(String clientOrderId, String symbol, Side side, long limitPriceTicks, long origQty) {
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.limitPriceTicks = limitPriceTicks;
        this.origQty = origQty;
        this.status = OrderStatus.NEW;
    }

    public long remainingQty() {
        return origQty - filledQty;
    }

    /** 增加成交数量并按是否填满更新状态，返回本次成交后剩余量。 */
    public long applyFill(long qty) {
        if (qty <= 0 || qty > remainingQty()) {
            throw new IllegalStateException("invalid fill qty: " + qty);
        }
        filledQty += qty;
        if (filledQty == origQty) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
        return remainingQty();
    }

    public void cancel() {
        if (status == OrderStatus.FILLED || status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("order " + id + " not cancellable: " + status);
        }
        status = OrderStatus.CANCELLED;
    }

    public boolean isLive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    public Long getId() {
        return id;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Side getSide() {
        return side;
    }

    public long getLimitPriceTicks() {
        return limitPriceTicks;
    }

    public long getOrigQty() {
        return origQty;
    }

    public long getFilledQty() {
        return filledQty;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
