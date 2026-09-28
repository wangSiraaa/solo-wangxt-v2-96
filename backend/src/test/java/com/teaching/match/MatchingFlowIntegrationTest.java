package com.teaching.match;

import com.teaching.match.api.PlaceOrderRequest;
import com.teaching.match.api.PlaceOrderResult;
import com.teaching.match.domain.Side;
import com.teaching.match.service.MatchingService;
import com.teaching.match.service.OrderBookService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全链路集成测试：真实 PostgreSQL（嵌入式发行版）+ 撮合服务 + REST API。
 * 脚本与前端固定回放命令一一对应：同价排队 -> 吃掉两档 -> 部分成交 -> 撤单，
 * 并在每一步核对数量守恒。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchingFlowIntegrationTest {

    static EmbeddedPostgres pg;

    @BeforeAll
    static void startDatabase() throws IOException {
        pg = EmbeddedPostgres.builder().start();
    }

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> pg.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    private MatchingService matching;

    @Autowired
    private OrderBookService book;

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void reset() {
        rest.postForEntity("/api/admin/reset", null, Object.class);
    }

    private PlaceOrderResult place(String coid, Side side, String price, long qty) {
        return matching.placeOrder(
                new PlaceOrderRequest(coid, "SYNTH-0", side, new BigDecimal(price), qty));
    }

    @Test
    void samePriceOrdersQueueInTimeOrder() {
        PlaceOrderResult a = place("S-1", Side.SELL, "10.00", 30);
        PlaceOrderResult b = place("S-2", Side.SELL, "10.00", 20);
        assertThat(a.order().id()).isLessThan(b.order().id());

        // 买单 30：按时间优先先吃 S-1 的 30，S-2 完全不动
        PlaceOrderResult buy = place("B-1", Side.BUY, "10.00", 30);
        assertThat(buy.executions()).hasSize(1);
        assertThat(buy.executions().get(0).makerOrderId()).isEqualTo(a.order().id());
        assertThat(buy.executions().get(0).qty()).isEqualTo(30);

        long s2 = matching.getOrder(b.order().id()).remainingQty();
        assertThat(s2).isEqualTo(20);
        assertThat(book.depth().asks()).singleElement().satisfies(lvl -> {
            assertThat(lvl.price()).isEqualTo("10.00");
            assertThat(lvl.qty()).isEqualTo(20);
            assertThat(lvl.orderCount()).isEqualTo(1);
        });
    }

    @Test
    void marketableBuyEatsTwoPriceLevelsAndPartialFillLeavesRestInBook() {
        PlaceOrderResult s1 = place("S-1", Side.SELL, "10.00", 30);
        place("S-2", Side.SELL, "10.00", 20);            // 同价第二排队
        place("S-3", Side.SELL, "10.10", 30);            // 第二档
        PlaceOrderResult s4 = place("S-4", Side.SELL, "10.30", 100); // 超出限价，不能被吃

        // 买单 100 @10.20：10.00 档 50 + 10.10 档 30 = 80，剩 20 挂入买盘
        PlaceOrderResult buy = place("B-1", Side.BUY, "10.20", 100);
        assertThat(buy.order().status()).isEqualTo("PARTIALLY_FILLED");
        assertThat(buy.order().filledQty()).isEqualTo(80);
        assertThat(buy.order().remainingQty()).isEqualTo(20);

        assertThat(buy.executions()).hasSize(3);
        assertThat(buy.executions().get(0).price()).isEqualTo("10.00");
        assertThat(buy.executions().get(0).qty()).isEqualTo(30);
        assertThat(buy.executions().get(1).price()).isEqualTo("10.00");
        assertThat(buy.executions().get(1).qty()).isEqualTo(20);
        assertThat(buy.executions().get(2).price()).isEqualTo("10.10");
        assertThat(buy.executions().get(2).qty()).isEqualTo(30);
        assertThat(buy.conservation().balanced()).isTrue();

        // 10.30 的卖单原封不动
        assertThat(matching.getOrder(s4.order().id()).filledQty()).isZero();

        // 买盘剩余 20 @10.20；卖盘只剩 10.30 x100
        assertThat(book.depth().bids()).singleElement().satisfies(lvl -> {
            assertThat(lvl.price()).isEqualTo("10.20");
            assertThat(lvl.qty()).isEqualTo(20);
        });
        assertThat(book.depth().asks()).singleElement().satisfies(lvl -> {
            assertThat(lvl.price()).isEqualTo("10.30");
            assertThat(lvl.qty()).isEqualTo(100);
        });

        // 撤掉买单剩余的 20：撤单后买盘清空，守恒仍成立
        var cancelled = matching.cancel(buy.order().id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.remainingQty()).isZero();
        assertThat(book.depth().bids()).isEmpty();

        var global = matching.globalConservation();
        // orig 30+20+30+100+100 = 280；filled = 80*2 = 160；cancelled = 20；book = 100
        assertThat(global.origQty()).isEqualTo(280);
        assertThat(global.filledQty()).isEqualTo(160);
        assertThat(global.cancelledQty()).isEqualTo(20);
        assertThat(global.bookQty()).isEqualTo(100);
        assertThat(global.balanced()).isTrue();

        // 已成交订单不允许撤单
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> matching.cancel(s1.order().id()));
    }

    @Test
    void duplicateClientOrderIdNeverCreatesANewOrderAndReturnsPriorState() {
        PlaceOrderResult first = place("DUP-1", Side.SELL, "11.00", 40);
        assertThat(first.deduplicated()).isFalse();

        // 同 clientOrderId、改了价格数量重提：必须被忽略，不新增订单
        PlaceOrderResult again = place("DUP-1", Side.BUY, "9.00", 999);
        assertThat(again.deduplicated()).isTrue();
        assertThat(again.order().id()).isEqualTo(first.order().id());
        assertThat(again.order().side()).isEqualTo("SELL");
        assertThat(again.order().origQty()).isEqualTo(40);
        assertThat(matching.listOrders()).hasSize(1);

        // REST 层：重复提交返回 200，新订单返回 201
        ResponseEntity<PlaceOrderResult> httpFirst = rest.postForEntity("/api/orders",
                new PlaceOrderRequest("HTTP-1", "SYNTH-0", Side.BUY, new BigDecimal("9.00"), 10),
                PlaceOrderResult.class);
        assertThat(httpFirst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(httpFirst.getBody()).isNotNull();
        assertThat(httpFirst.getBody().deduplicated()).isFalse();

        ResponseEntity<PlaceOrderResult> httpAgain = rest.postForEntity("/api/orders",
                new PlaceOrderRequest("HTTP-1", "SYNTH-0", Side.BUY, new BigDecimal("8.00"), 10),
                PlaceOrderResult.class);
        assertThat(httpAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(httpAgain.getBody()).isNotNull();
        assertThat(httpAgain.getBody().deduplicated()).isTrue();
    }

    @Test
    void offGridPriceAndUnknownSymbolAreRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> matching.placeOrder(new PlaceOrderRequest(
                        "X-1", "SYNTH-0", Side.BUY, new BigDecimal("10.005"), 10)));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> matching.placeOrder(new PlaceOrderRequest(
                        "X-2", "OTHER", Side.BUY, new BigDecimal("10.00"), 10)));
    }

    @Test
    void sellSideAlsoFollowsPricePriorityBestBidFirst() {
        place("B-1", Side.BUY, "9.90", 10);
        place("B-2", Side.BUY, "10.00", 20);   // 最高买价，应先成交

        PlaceOrderResult sell = place("S-1", Side.SELL, "9.80", 15);
        assertThat(sell.executions()).hasSize(1);
        assertThat(sell.executions().get(0).price()).isEqualTo("10.00");
        assertThat(sell.executions().get(0).qty()).isEqualTo(15);
        assertThat(matching.getOrder(sell.order().id()).status()).isEqualTo("FILLED");
        assertThat(matching.globalConservation().balanced()).isTrue();
    }
}
