package com.teaching.match.api;

public record BookLevelDto(
        long priceTicks,
        String price,
        long qty,
        long orderCount
) {
}
