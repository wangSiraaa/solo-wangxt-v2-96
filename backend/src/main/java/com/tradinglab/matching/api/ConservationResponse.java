package com.tradinglab.matching.api;

/**
 * 数量守恒核对结果。
 *
 * balance = totalVolume - totalFilled，正常恒为 0：
 * 每笔成交在 taker 与 maker 上各计一次 filled，所以总成交量等于全部订单累计成交量之和。
 */
public record ConservationResponse(
        long tradeCount,
        long totalTradeVolume,
        long totalFilledQuantity,
        long buyFilledQuantity,
        long sellFilledQuantity,
        long balance,
        boolean conserved,
        long activeOrderCount,
        long totalOrderCount
) {
}
