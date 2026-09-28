package com.teaching.match.repo;

import com.teaching.match.domain.Order;
import com.teaching.match.domain.OrderStatus;
import com.teaching.match.domain.Side;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByClientOrderId(String clientOrderId);

    Optional<Order> findByClientOrderId(String clientOrderId);

    /**
     * 买盘对手方（SELL）：报价不超过买单限价，价格优先（最低卖价），同价时间优先。
     */
    @Query("select o from Order o " +
            "where o.symbol = :symbol and o.side = :side and o.status in :statuses " +
            "and o.limitPriceTicks <= :maxPriceTicks " +
            "order by o.limitPriceTicks asc, o.createdAt asc, o.id asc")
    List<Order> findAskCandidates(@Param("symbol") String symbol,
                                  @Param("side") Side side,
                                  @Param("statuses") List<OrderStatus> statuses,
                                  @Param("maxPriceTicks") long maxPriceTicks,
                                  Pageable pageable);

    /**
     * 卖盘对手方（BUY）：报价不低于卖单限价，价格优先（最高买价），同价时间优先。
     */
    @Query("select o from Order o " +
            "where o.symbol = :symbol and o.side = :side and o.status in :statuses " +
            "and o.limitPriceTicks >= :minPriceTicks " +
            "order by o.limitPriceTicks desc, o.createdAt asc, o.id asc")
    List<Order> findBidCandidates(@Param("symbol") String symbol,
                                  @Param("side") Side side,
                                  @Param("statuses") List<OrderStatus> statuses,
                                  @Param("minPriceTicks") long minPriceTicks,
                                  Pageable pageable);

    List<Order> findBySymbolOrderByCreatedAtDescIdDesc(String symbol);

    /** 教学重置：订单自增主键归位（DELETE 不会自动重置 IDENTITY）。 */
    @Modifying
    @Query(value = "ALTER SEQUENCE orders_id_seq RESTART WITH 1", nativeQuery = true)
    void restartIdSequence();
}
