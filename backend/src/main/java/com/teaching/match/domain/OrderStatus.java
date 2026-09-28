package com.teaching.match.domain;

public enum OrderStatus {
    /** 未成交（在簿） */
    NEW,
    /** 部分成交（剩余在簿） */
    PARTIALLY_FILLED,
    /** 全部成交 */
    FILLED,
    /** 剩余撤单 */
    CANCELLED
}
