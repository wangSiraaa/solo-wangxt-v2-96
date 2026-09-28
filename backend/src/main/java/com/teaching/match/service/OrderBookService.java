package com.teaching.match.service;

import com.teaching.match.api.BookLevelDto;
import com.teaching.match.api.DtoMapper;
import com.teaching.match.api.OrderBookDto;
import com.teaching.match.config.MarketProperties;
import com.teaching.match.repo.OrderBookQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderBookService {

    private static final int DEPTH = 20;

    private final OrderBookQueryRepository bookRepo;
    private final DtoMapper mapper;
    private final MarketProperties props;

    public OrderBookService(OrderBookQueryRepository bookRepo, DtoMapper mapper, MarketProperties props) {
        this.bookRepo = bookRepo;
        this.mapper = mapper;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public OrderBookDto depth() {
        String symbol = props.symbol();
        List<BookLevelDto> bids = bookRepo.aggregatedBids(symbol, "BUY", DEPTH).stream()
                .map(mapper::level).toList();
        List<BookLevelDto> asks = bookRepo.aggregatedAsks(symbol, "SELL", DEPTH).stream()
                .map(mapper::level).toList();
        return new OrderBookDto(symbol, mapper.ticksPerUnit(), bids, asks);
    }
}
