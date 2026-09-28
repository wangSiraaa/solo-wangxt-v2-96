package com.teaching.match.api;

import java.time.OffsetDateTime;

public record ExecutionDto(
        long id,
        String tradeId,
        String symbol,
        long priceTicks,
        String price,
        long qty,
        long makerOrderId,
        long takerOrderId,
        String makerSide,
        OffsetDateTime executedAt
) {
}
