package com.teaching.match.api;

import java.time.OffsetDateTime;

public record OrderDto(
        long id,
        String clientOrderId,
        String symbol,
        String side,
        long limitPriceTicks,
        String limitPrice,
        long origQty,
        long filledQty,
        long remainingQty,
        String status,
        OffsetDateTime createdAt
) {
}
