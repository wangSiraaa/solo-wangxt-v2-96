package com.tradinglab.matching.api;

import java.util.List;

/**
 * 下单接口的返回：订单最终状态 + 本次提交直接产生的成交。
 */
public record PlaceOrderResponse(OrderResponse order, List<TradeResponse> trades) {
}
