package com.teaching.match.api;

import com.teaching.match.config.MarketProperties;
import com.teaching.match.service.MatchingService;
import com.teaching.match.service.OrderBookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 单次拉取页面所需的全部快照（回放脚本逐步调用同一接口核对变化）。
 */
@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final OrderBookService bookService;
    private final MatchingService matching;
    private final MarketProperties props;

    public SnapshotController(OrderBookService bookService, MatchingService matching, MarketProperties props) {
        this.bookService = bookService;
        this.matching = matching;
        this.props = props;
    }

    @GetMapping("/instrument")
    public Map<String, Object> instrument() {
        return Map.of(
                "symbol", props.symbol(),
                "tickSize", props.tickSize().toPlainString(),
                "lotSize", props.lotSize());
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        return Map.of(
                "instrument", instrument(),
                "book", bookService.depth(),
                "orders", matching.listOrders(),
                "trades", matching.listExecutions(),
                "conservation", matching.globalConservation()
        );
    }
}
