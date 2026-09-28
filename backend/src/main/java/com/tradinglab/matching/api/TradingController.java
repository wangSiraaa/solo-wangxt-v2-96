package com.tradinglab.matching.api;

import com.tradinglab.matching.engine.BookLevel;
import com.tradinglab.matching.engine.BookSnapshot;
import com.tradinglab.matching.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TradingController {

    private final OrderService orderService;
    private final String symbol;
    private final BigDecimal tickSize;

    public TradingController(OrderService orderService,
                             @Value("${matching.symbol}") String symbol,
                             @Value("${matching.tick-size:0.01}") BigDecimal tickSize) {
        this.orderService = orderService;
        this.symbol = symbol;
        this.tickSize = tickSize;
    }

    @GetMapping("/settings")
    public SettingsResponse settings() {
        // tickScale：价格刻度转显示小数时的位数，例如 0.01 -> 2
        int scale = tickSize.stripTrailingZeros().scale();
        return new SettingsResponse(symbol, tickSize.toPlainString(), Math.max(scale, 0), 1L);
    }

    @PostMapping("/orders")
    public ResponseEntity<PlaceOrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.ok(orderService.place(request));
    }

    @PostMapping("/orders/{clientOrderId}/cancel")
    public OrderResponse cancel(@PathVariable String clientOrderId) {
        return orderService.cancelByClientOrderId(clientOrderId);
    }

    @GetMapping("/orders")
    public List<OrderResponse> orders() {
        return orderService.listOrders();
    }

    @GetMapping("/trades")
    public List<TradeResponse> trades() {
        return orderService.listTrades();
    }

    /** 买卖盘深度（按价位聚合，单位均为整数刻度/数量）。 */
    @GetMapping("/book")
    public Map<String, List<BookLevel>> book() {
        BookSnapshot snapshot = orderService.book();
        return Map.of("bids", snapshot.bids(), "asks", snapshot.asks());
    }

    /** 数量守恒核对。 */
    @GetMapping("/conservation")
    public ConservationResponse conservation() {
        return orderService.conservation();
    }

    /** 固定命令回放前清空全部合成数据。 */
    @PostMapping("/reset")
    public Map<String, String> reset() {
        orderService.reset();
        return Map.of("status", "reset");
    }
}
