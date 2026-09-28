package com.teaching.match.api;

import com.teaching.match.service.MatchingService;
import com.teaching.match.service.OrderBookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/book")
public class BookController {

    private final OrderBookService bookService;

    public BookController(OrderBookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public OrderBookDto depth() {
        return bookService.depth();
    }
}
