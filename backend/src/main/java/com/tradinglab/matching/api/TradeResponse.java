package com.tradinglab.matching.api;

import java.time.Instant;

/**
 * 成交明细视图。
 */
public record TradeResponse(
        Long id,
        String symbol,
        Long makerOrderId,
        String makerClientOrderId,
        Long takerOrderId,
        String takerClientOrderId,
        String takerSide,
        long priceTicks,
        long quantity,
        Instant createdAt
) {
}
