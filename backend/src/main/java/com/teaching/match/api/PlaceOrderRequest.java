package com.teaching.match.api;

import com.teaching.match.domain.Side;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 下单请求。价格用字符串传输（如 "10.00"），由服务端按 tick 刻度转成整数，
 * 避免任何浮点解析路径。
 */
public record PlaceOrderRequest(
        @NotBlank @Size(max = 64) String clientOrderId,
        @NotBlank @Size(max = 32) String symbol,
        @NotNull Side side,
        @NotNull @DecimalMin(value = "0.000000001", inclusive = false) BigDecimal limitPrice,
        @Positive long quantity
) {
}
