package com.tradinglab.matching.service;

/**
 * 客户端订单号重复：重复提交不能新增订单，返回 409。
 */
public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String clientOrderId) {
        super("clientOrderId 已存在，重复提交被拒绝: " + clientOrderId);
    }
}
