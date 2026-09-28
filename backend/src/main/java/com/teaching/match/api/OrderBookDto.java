package com.teaching.match.api;

import java.util.List;

public record OrderBookDto(
        String symbol,
        long tickSizeDisplay,
        List<BookLevelDto> bids,
        List<BookLevelDto> asks
) {
}
