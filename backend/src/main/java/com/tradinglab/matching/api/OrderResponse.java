package com.tradinglab.matching.api;

import java.time.Instant;

/**
 * 订单视图。
 */
public record OrderResponse(
        Long id,
        String clientOrderId,
        String symbol,
        String side,
        long priceTicks,
        long quantity,
        long filledQuantity,
        long remainingQuantity,
        String status,
        long seq,
        Instant createdAt,
        Instant updatedAt
) {
}
