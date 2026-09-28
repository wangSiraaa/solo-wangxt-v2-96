package com.tradinglab.matching.repo;

import com.tradinglab.matching.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findBySymbolOrderByIdDesc(String symbol);

    List<Trade> findByMakerOrderIdOrTakerOrderIdOrderByIdAsc(Long makerOrderId, Long takerOrderId);

    long countBySymbol(String symbol);
}
