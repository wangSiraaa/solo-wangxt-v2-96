package com.teaching.match.service;

import com.teaching.match.config.MarketProperties;
import com.teaching.match.repo.ExecutionRepository;
import com.teaching.match.repo.OrderRepository;
import com.teaching.match.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 教学辅助：一键清空订单与成交并重置成交号序列。
 * 合成环境专用，由 app.admin.reset-enabled 控制。
 */
@Service
public class AdminService {

    private final OrderRepository orders;
    private final ExecutionRepository executions;
    private final TransactionTemplate txTemplate;
    private final MarketProperties props;

    public AdminService(OrderRepository orders,
                        ExecutionRepository executions,
                        TransactionTemplate txTemplate,
                        MarketProperties props) {
        this.orders = orders;
        this.executions = executions;
        this.txTemplate = txTemplate;
        this.props = props;
    }

    public Map<String, Object> reset() {
        if (!props.admin().resetEnabled()) {
            throw ApiException.notFound("reset endpoint is disabled");
        }
        return txTemplate.execute(status -> {
            executions.deleteAllInBatch();
            orders.deleteAllInBatch();
            executions.resetTradeSequence();
            orders.restartIdSequence();
            return Map.<String, Object>of(
                    "reset", true,
                    "symbol", props.symbol(),
                    "orders", orders.count(),
                    "executions", executions.count());
        });
    }
}
