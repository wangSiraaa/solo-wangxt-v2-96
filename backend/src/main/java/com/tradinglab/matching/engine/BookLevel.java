package com.tradinglab.matching.engine;

/**
 * 订单簿单个价位的聚合深度。
 */
public record BookLevel(long priceTicks, long quantity, long orderCount) {
}
