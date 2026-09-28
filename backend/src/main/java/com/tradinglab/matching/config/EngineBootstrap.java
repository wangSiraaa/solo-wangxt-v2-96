package com.tradinglab.matching.config;

import com.tradinglab.matching.service.OrderService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后用数据库中仍活跃的订单重建内存撮合簿，使重启不丢排队状态。
 */
@Component
public class EngineBootstrap implements ApplicationRunner {

    private final OrderService orderService;

    public EngineBootstrap(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void run(ApplicationArguments args) {
        orderService.rebuildEngine();
    }
}
