-- 限价订单撮合教学项目：首版仅一个合成标的
-- 所有价格以 tick 整数存储（1 tick = 0.01），比较与撮合只用 BIGINT，不使用浮点。

CREATE SEQUENCE trade_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY,
    client_order_id    VARCHAR(64)  NOT NULL,
    symbol             VARCHAR(32)  NOT NULL,
    side               VARCHAR(4)   NOT NULL,
    limit_price_ticks  BIGINT       NOT NULL,
    orig_qty           BIGINT       NOT NULL,
    filled_qty         BIGINT       NOT NULL DEFAULT 0,
    status             VARCHAR(16)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_client_order_id UNIQUE (client_order_id),
    CONSTRAINT ck_orders_side   CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_orders_status CHECK (status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED')),
    CONSTRAINT ck_orders_price  CHECK (limit_price_ticks > 0),
    CONSTRAINT ck_orders_qty    CHECK (orig_qty > 0),
    CONSTRAINT ck_orders_filled CHECK (filled_qty >= 0 AND filled_qty <= orig_qty)
);

-- 同价排队：价格 + 时间（created_at, id 兜底同毫秒）
CREATE INDEX idx_orders_book ON orders (symbol, side, limit_price_ticks, created_at, id)
    WHERE status IN ('NEW', 'PARTIALLY_FILLED');

CREATE TABLE executions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trade_id         VARCHAR(24) NOT NULL,
    symbol           VARCHAR(32) NOT NULL,
    price_ticks      BIGINT      NOT NULL,
    qty              BIGINT      NOT NULL,
    maker_order_id   BIGINT      NOT NULL,
    taker_order_id   BIGINT      NOT NULL,
    maker_side       VARCHAR(4)  NOT NULL,
    executed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_executions_trade_id UNIQUE (trade_id),
    CONSTRAINT ck_executions_qty   CHECK (qty > 0),
    CONSTRAINT ck_executions_price CHECK (price_ticks > 0),
    CONSTRAINT ck_executions_maker_side CHECK (maker_side IN ('BUY', 'SELL')),
    CONSTRAINT fk_executions_maker FOREIGN KEY (maker_order_id) REFERENCES orders (id),
    CONSTRAINT fk_executions_taker FOREIGN KEY (taker_order_id) REFERENCES orders (id),
    CONSTRAINT ck_executions_distinct_sides CHECK (maker_order_id <> taker_order_id)
);

CREATE INDEX idx_executions_time ON executions (executed_at, id);
