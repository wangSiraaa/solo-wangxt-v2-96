package com.tradinglab.matching.domain;

/**
 * 订单生命周期状态：
 * NEW 已报入、未成交；
 * PARTIALLY_FILLED 部分成交、剩余仍在簿；
 * FILLED 全部成交；
 * CANCELLED 剩余数量被撤（含部分成交后撤单）。
 */
public enum OrderStatus {
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
}
