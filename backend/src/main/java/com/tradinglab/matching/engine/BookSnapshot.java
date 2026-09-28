package com.tradinglab.matching.engine;

import java.util.List;

/**
 * 买卖盘深度快照。bids 已按价格降序，asks 已按价格升序。
 */
public record BookSnapshot(List<BookLevel> bids, List<BookLevel> asks) {
}
