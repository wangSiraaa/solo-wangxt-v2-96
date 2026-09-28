package com.teaching.match.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app")
public record MarketProperties(
        String symbol,
        BigDecimal tickSize,
        long lotSize,
        Admin admin
) {
    public record Admin(boolean resetEnabled) {
    }
}
