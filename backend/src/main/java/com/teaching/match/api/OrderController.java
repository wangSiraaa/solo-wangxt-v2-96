package com.teaching.match.api;

import com.teaching.match.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final MatchingService matching;

    public OrderController(MatchingService matching) {
        this.matching = matching;
    }

    @PostMapping
    public ResponseEntity<PlaceOrderResult> place(@Valid @RequestBody PlaceOrderRequest request) {
        // 重复提交返回 200 + deduplicated=true；新订单返回 201
        PlaceOrderResult result = matching.placeOrder(request);
        return ResponseEntity.status(result.deduplicated() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public List<OrderDto> list() {
        return matching.listOrders();
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable long id) {
        return matching.getOrder(id);
    }

    @DeleteMapping("/{id}")
    public OrderDto cancel(@PathVariable long id) {
        return matching.cancel(id);
    }
}
