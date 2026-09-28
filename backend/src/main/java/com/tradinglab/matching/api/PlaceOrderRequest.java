package com.tradinglab.matching.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 下单请求。
 *
 * priceTicks 为价格刻度数（非负整数），例如 tickSize=0.01 时 1050 表示 10.50；
 * quantity 为最小交易单位的整数倍。前后端都不提交浮点价格，从源头杜绝浮点比较。
 */
public record PlaceOrderRequest(
        @NotBlank @Size(max = 64) String clientOrderId,
        @NotNull String side,
        @NotNull @Positive Long priceTicks,
        @NotNull @Positive Long quantity
) {
}
