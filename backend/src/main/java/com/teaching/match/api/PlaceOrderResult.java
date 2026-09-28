package com.teaching.match.api;

import java.util.List;

/**
 * 下单结果。deduplicated=true 表示 clientOrderId 重复，系统不会新增订单，
 * 只返回此前那张订单的现状及其已有成交。
 */
public record PlaceOrderResult(
        OrderDto order,
        List<ExecutionDto> executions,
        boolean deduplicated,
        Conservation conservation
) {
    /** 数量守恒核对：origQty == filledQty + remainingQty（在簿）/ cancelledQty */
    public record Conservation(long origQty, long filledQty, long cancelledQty, long bookQty, boolean balanced) {
    }
}
