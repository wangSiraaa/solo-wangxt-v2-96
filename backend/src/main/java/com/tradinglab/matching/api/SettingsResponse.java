package com.tradinglab.matching.api;

/**
 * 交易标的与刻度规则。前端据此渲染价格/数量输入提示，保证前后端使用同一套整数刻度定义。
 */
public record SettingsResponse(
        String symbol,
        String tickSize,
        int tickScale,
        long lotSize
) {
}
