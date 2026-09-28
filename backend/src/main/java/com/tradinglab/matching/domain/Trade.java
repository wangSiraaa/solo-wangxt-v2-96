package com.tradinglab.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 一笔成交（taker 与 maker 之间的一次撮合）。
 * 成交价以刻度数存储；成交量恒等于 taker 减少的剩余量，也恒等于 maker 减少的剩余量，
 * 这是“数量守恒”核对的数据基础。
 */
@Entity
@Table(name = "trades", indexes = {
        @Index(name = "idx_trades_symbol", columnList = "symbol"),
        @Index(name = "idx_trades_maker_order", columnList = "maker_order_id"),
        @Index(name = "idx_trades_taker_order", columnList = "taker_order_id")
})
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    /** 被动方：早已挂在簿内、提供流动性的订单。 */
    @Column(name = "maker_order_id", nullable = false)
    private Long makerOrderId;

    @Column(name = "maker_client_order_id", nullable = false, length = 64)
    private String makerClientOrderId;

    /** 主动方：本次提交、立即吃单的订单。 */
    @Column(name = "taker_order_id", nullable = false)
    private Long takerOrderId;

    @Column(name = "taker_client_order_id", nullable = false, length = 64)
    private String takerClientOrderId;

    @Column(name = "taker_side", nullable = false, length = 8)
    private String takerSide;

    /** 成交价 = priceTicks * tickSize，沿用 maker 的报价（价格优先的价格改善）。 */
    @Column(name = "price_ticks", nullable = false)
    private long priceTicks;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Trade() {
    }

    public Trade(String symbol, Order maker, Order taker, long priceTicks, long quantity) {
        this.symbol = symbol;
        this.makerOrderId = maker.getId();
        this.makerClientOrderId = maker.getClientOrderId();
        this.takerOrderId = taker.getId();
        this.takerClientOrderId = taker.getClientOrderId();
        this.takerSide = taker.getSide().name();
        this.priceTicks = priceTicks;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getMakerOrderId() {
        return makerOrderId;
    }

    public void setMakerOrderId(Long makerOrderId) {
        this.makerOrderId = makerOrderId;
    }

    public String getMakerClientOrderId() {
        return makerClientOrderId;
    }

    public void setMakerClientOrderId(String makerClientOrderId) {
        this.makerClientOrderId = makerClientOrderId;
    }

    public Long getTakerOrderId() {
        return takerOrderId;
    }

    public void setTakerOrderId(Long takerOrderId) {
        this.takerOrderId = takerOrderId;
    }

    public String getTakerClientOrderId() {
        return takerClientOrderId;
    }

    public void setTakerClientOrderId(String takerClientOrderId) {
        this.takerClientOrderId = takerClientOrderId;
    }

    public String getTakerSide() {
        return takerSide;
    }

    public void setTakerSide(String takerSide) {
        this.takerSide = takerSide;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
