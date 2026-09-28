package com.teaching.match.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TickRulesTest {

    @Test
    void convertsPriceOnGridToIntegerTicks() {
        long tpu = TickRules.ticksPerUnit(new BigDecimal("0.01"));
        assertThat(tpu).isEqualTo(100);
        assertThat(TickRules.priceToTicks(new BigDecimal("10.00"), tpu)).isEqualTo(1000);
        assertThat(TickRules.priceToTicks(new BigDecimal("9.99"), tpu)).isEqualTo(999);
        assertThat(TickRules.priceToTicks(new BigDecimal("0.01"), tpu)).isEqualTo(1);
    }

    @Test
    void rejectsPricesOffGridInsteadOfRounding() {
        long tpu = TickRules.ticksPerUnit(new BigDecimal("0.01"));
        assertThatThrownBy(() -> TickRules.priceToTicks(new BigDecimal("10.005"), tpu))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TickRules.priceToTicks(new BigDecimal("0"), tpu))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rendersTicksBackToDecimalStringWithoutFloatingPoint() {
        long tpu = TickRules.ticksPerUnit(new BigDecimal("0.01"));
        assertThat(TickRules.ticksToDisplay(1001, tpu)).isEqualTo("10.01");
        assertThat(TickRules.ticksToDisplay(1, tpu)).isEqualTo("0.01");
    }

    @Test
    void integerTickComparisonIsExact() {
        long tpu = TickRules.ticksPerUnit(new BigDecimal("0.01"));
        long a = TickRules.priceToTicks(new BigDecimal("10.10"), tpu);
        long b = TickRules.priceToTicks(new BigDecimal("10.09"), tpu);
        assertThat(a).isGreaterThan(b);
        assertThat(a - b).isEqualTo(1);
    }
}
