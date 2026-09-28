package com.tradinglab.matching.service;

/**
 * 请求引用了无法撮合的订单（如撤单不存在的订单号），返回 404/400。
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String ref) {
        super("订单不存在: " + ref);
    }
}
