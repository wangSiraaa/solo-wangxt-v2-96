package com.teaching.match.api;

import com.teaching.match.service.MatchingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final MatchingService matching;

    public TradeController(MatchingService matching) {
        this.matching = matching;
    }

    @GetMapping
    public List<ExecutionDto> list() {
        return matching.listExecutions();
    }
}
