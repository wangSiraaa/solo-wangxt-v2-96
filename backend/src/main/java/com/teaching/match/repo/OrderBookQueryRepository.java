package com.teaching.match.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderBookQueryRepository
        extends org.springframework.data.repository.Repository<com.teaching.match.domain.Order, Long> {

    @Query(value = """
            SELECT limit_price_ticks AS "priceTicks",
                   SUM(orig_qty - filled_qty) AS "totalQty",
                   COUNT(*) AS "orderCount"
            FROM orders
            WHERE symbol = :symbol AND side = :side
              AND status IN ('NEW', 'PARTIALLY_FILLED')
            GROUP BY limit_price_ticks
            ORDER BY limit_price_ticks DESC
            LIMIT :depth
            """, nativeQuery = true)
    List<BookLevelProjection> aggregatedBids(@Param("symbol") String symbol,
                                             @Param("side") String side,
                                             @Param("depth") int depth);

    @Query(value = """
            SELECT limit_price_ticks AS "priceTicks",
                   SUM(orig_qty - filled_qty) AS "totalQty",
                   COUNT(*) AS "orderCount"
            FROM orders
            WHERE symbol = :symbol AND side = :side
              AND status IN ('NEW', 'PARTIALLY_FILLED')
            GROUP BY limit_price_ticks
            ORDER BY limit_price_ticks ASC
            LIMIT :depth
            """, nativeQuery = true)
    List<BookLevelProjection> aggregatedAsks(@Param("symbol") String symbol,
                                             @Param("side") String side,
                                             @Param("depth") int depth);
}
