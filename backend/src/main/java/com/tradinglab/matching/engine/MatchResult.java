package com.tradinglab.matching.engine;

import com.tradinglab.matching.domain.Order;
import com.tradinglab.matching.domain.Trade;

import java.util.List;

/**
 * 一次撮合的结果：成交列表 + 状态被改动的 maker 订单列表（调用方需持久化）。
 */
public record MatchResult(List<Trade> trades, List<Order> touchedMakers) {
}
