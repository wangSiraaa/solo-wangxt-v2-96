package com.teaching.match.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价格刻度规则：外部用 {@link BigDecimal}（如 10.50），内部一律换算成 tick 整数。
 * 比较、排序、撮合只使用 long，绝不使用 double/float 比较价格。
 */
public final class TickRules {

    private TickRules() {
    }

    /** 把刻度大小（如 0.01）换算成每个 tick 对应的缩放倍数（100）。 */
    public static long ticksPerUnit(BigDecimal tickSize) {
        if (tickSize.signum() <= 0) {
            throw new IllegalArgumentException("tickSize must be positive: " + tickSize);
        }
        BigDecimal stripped = tickSize.stripTrailingZeros();
        int scale = Math.max(0, stripped.scale());
        return BigDecimal.ONE.movePointRight(scale)
                .divide(stripped.movePointRight(scale), 0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    /** 价格 -> tick 整数；必须恰好落在刻度上，否则拒绝。 */
    public static long priceToTicks(BigDecimal price, long ticksPerUnit) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        BigDecimal scaled = price.multiply(BigDecimal.valueOf(ticksPerUnit));
        long ticks;
        try {
            ticks = scaled.longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "price " + price.toPlainString() + " is not on tick grid (tick = 1/" + ticksPerUnit + ")");
        }
        if (ticks <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        return ticks;
    }

    /** tick 整数 -> 展示用价格字符串（2 位小数，不使用浮点）。 */
    public static String ticksToDisplay(long ticks, long ticksPerUnit) {
        BigDecimal price = BigDecimal.valueOf(ticks)
                .divide(BigDecimal.valueOf(ticksPerUnit), decimalScale(ticksPerUnit), RoundingMode.UNNECESSARY);
        return price.toPlainString();
    }

    private static int decimalScale(long ticksPerUnit) {
        int scale = 0;
        long v = ticksPerUnit;
        while (v > 1) {
            if (v % 10 != 0) {
                // 非 10 的整次幂时退化为 BigDecimal 自行处理精度
                return BigDecimal.valueOf(ticksPerUnit).precision();
            }
            v /= 10;
            scale++;
        }
        return scale;
    }
}
