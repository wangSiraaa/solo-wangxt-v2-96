package com.teaching.match.repo;

/**
 * 买卖盘聚合行：按价格汇总在簿剩余量（SQL 端求和，不把全部订单拉进内存）。
 */
public interface BookLevelProjection {
    long getPriceTicks();
    long getTotalQty();
    long getOrderCount();
}
