package com.teaching.match.api;

import com.teaching.match.domain.Execution;
import com.teaching.match.domain.Order;
import com.teaching.match.support.TickRules;
import org.springframework.stereotype.Component;

/**
 * 领域对象 -> API DTO。价格同时给出 tick 整数与刻度换算后的字符串，
 * 前端展示走字符串，排序/比较走整数。
 */
@Component
public class DtoMapper {

    private final long ticksPerUnit;

    public DtoMapper(com.teaching.match.config.MarketProperties props) {
        this.ticksPerUnit = TickRules.ticksPerUnit(props.tickSize());
    }

    public long ticksPerUnit() {
        return ticksPerUnit;
    }

    public OrderDto order(Order o) {
        return new OrderDto(
                o.getId(),
                o.getClientOrderId(),
                o.getSymbol(),
                o.getSide().name(),
                o.getLimitPriceTicks(),
                TickRules.ticksToDisplay(o.getLimitPriceTicks(), ticksPerUnit),
                o.getOrigQty(),
                o.getFilledQty(),
                o.isLive() ? o.remainingQty() : 0L,
                o.getStatus().name(),
                o.getCreatedAt()
        );
    }

    public ExecutionDto execution(Execution e) {
        return new ExecutionDto(
                e.getId(),
                e.getTradeId(),
                e.getSymbol(),
                e.getPriceTicks(),
                TickRules.ticksToDisplay(e.getPriceTicks(), ticksPerUnit),
                e.getQty(),
                e.getMakerOrderId(),
                e.getTakerOrderId(),
                e.getMakerSide().name(),
                e.getExecutedAt()
        );
    }

    public BookLevelDto level(com.teaching.match.repo.BookLevelProjection p) {
        return new BookLevelDto(
                p.getPriceTicks(),
                TickRules.ticksToDisplay(p.getPriceTicks(), ticksPerUnit),
                p.getTotalQty(),
                p.getOrderCount()
        );
    }
}
