package com.teaching.match.repo;

import com.teaching.match.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    List<Execution> findBySymbolOrderByExecutedAtDescIdDesc(String symbol);

    List<Execution> findByMakerOrderIdOrTakerOrderIdOrderByExecutedAtAscIdAsc(long makerOrderId, long takerOrderId);

    @Query(value = "SELECT nextval('trade_id_seq')", nativeQuery = true)
    Long nextTradeSequence();

    @Query(value = "SELECT setval('trade_id_seq', 1, false)", nativeQuery = true)
    Long resetTradeSequence();

    long countBySymbol(String symbol);
}
