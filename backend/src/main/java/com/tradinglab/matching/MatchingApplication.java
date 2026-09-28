package com.tradinglab.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 限价订单连续竞价撮合教学系统。
 * 仅处理合成订单，首版只支持单一交易标的，不接入任何真实市场。
 */
@SpringBootApplication
public class MatchingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingApplication.class, args);
    }
}
