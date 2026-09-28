package com.tradinglab.matching.repo;

import com.tradinglab.matching.domain.Order;
import com.tradinglab.matching.domain.OrderStatus;
import com.tradinglab.matching.domain.Side;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByClientOrderId(String clientOrderId);

    Optional<Order> findByClientOrderId(String clientOrderId);

    /** 启动时重建撮合簿：只取仍在簿内的订单，按价格优先、同价时间优先排序。 */
    List<Order> findBySymbolAndStatusInOrderByPriceTicksAscSeqAsc(String symbol, List<OrderStatus> statuses);

    /** 管理页查看全部订单，按创建先后倒序（立即成交未入簿的订单 seq=0，故用 id）。 */
    List<Order> findBySymbolOrderByIdDesc(String symbol);

    /** 同方向簿内活跃订单数（回放清空后用于核对）。 */
    long countBySymbolAndSideAndStatusIn(String symbol, Side side, List<OrderStatus> statuses);
}
