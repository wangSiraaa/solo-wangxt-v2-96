package com.teaching.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 一笔成交（taker 的一次撮合动作与 maker 产生一条记录；maker 与 taker 的成交量相同）。
 */
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true, updatable = false)
    private String tradeId;

    @Column(nullable = false, updatable = false)
    private String symbol;

    @Column(name = "price_ticks", nullable = false, updatable = false)
    private long priceTicks;

    @Column(nullable = false, updatable = false)
    private long qty;

    @Column(name = "maker_order_id", nullable = false, updatable = false)
    private long makerOrderId;

    @Column(name = "taker_order_id", nullable = false, updatable = false)
    private long takerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "maker_side", nullable = false, updatable = false)
    private Side makerSide;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private OffsetDateTime executedAt;

    public Execution() {
    }

    public Execution(String tradeId, String symbol, long priceTicks, long qty,
                     long makerOrderId, long takerOrderId, Side makerSide) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.priceTicks = priceTicks;
        this.qty = qty;
        this.makerOrderId = makerOrderId;
        this.takerOrderId = takerOrderId;
        this.makerSide = makerSide;
    }

    public Long getId() {
        return id;
    }

    public String getTradeId() {
        return tradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getPriceTicks() {
        return priceTicks;
    }

    public long getQty() {
        return qty;
    }

    public long getMakerOrderId() {
        return makerOrderId;
    }

    public long getTakerOrderId() {
        return takerOrderId;
    }

    public Side getMakerSide() {
        return makerSide;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }
}
