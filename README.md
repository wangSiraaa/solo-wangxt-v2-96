# 限价订单撮合教学全栈应用

一个**仅使用合成订单、不接真实市场**的连续竞价限价单撮合演示系统。

- 前端：React 18 + TypeScript（Vite）—— 下单表单、买卖盘深度、成交明细、订单状态、固定命令回放、数量守恒核对。
- 后端：Java 17 + Spring Boot 3 —— 下单 / 撤单 / 成交查询 / 盘口快照 API。
- 存储：PostgreSQL 16，Flyway 建表；订单、成交持久化。
- **价格与数量一律按明确刻度转成整数（tick），撮合只做整数比较，全程不使用浮点。**
- 首版只支持一个交易标的 `SYNTH-0`。

## 撮合规则

- 连续竞价：订单到达即与对手盘撮合；未成交剩余留在订单簿。
- **价格优先**：买单先成交最低卖价；卖单先成交最高买价。
- **同价时间优先**：同一价格按到达时间排队（`created_at`，`id` 兜底同毫秒）。
- 成交价取被动挂单方（maker）的限价。
- 支持**部分成交**：吃单量不足时，maker/taker 各自剩余继续排队。
- 支持**剩余撤单**：`DELETE /api/orders/{id}` 撤销尚未成交的部分。
- **幂等下单**：`clientOrderId` 唯一约束；重复提交不会新增订单，返回原订单当前状态与已有成交（HTTP 200 + `deduplicated=true`）。
- 越刻度价格（如 tick=0.01 时的 `10.005`）直接拒绝，不做隐式取整。

## 数量守恒口径

系统持续核对（前端页面顶部常驻核对条，后端 `/api/snapshot` 直接返回）：

```
原始下单总量 ΣorigQty = 已成交 ΣfilledQty(双边) + 已撤剩余 ΣcancelledQty + 在簿剩余 ΣbookQty
```

说明：一笔成交同时消耗买方与卖方各一份数量，因此 `ΣfilledQty` 是双边计数；
交叉核对「成交明细单边总量 × 2 == ΣfilledQty」，以及「盘口聚合量 == LIVE 订单剩余量」。

## 目录结构

```
backend/                 Spring Boot 应用
  src/main/java/com/teaching/match/
    domain/              Order / Execution 实体、状态枚举
    support/TickRules    价格字符串 <-> tick 整数（BigDecimal 换算，无浮点）
    repo/                JPA 仓库（对手方按价格/时间排序、盘口聚合）
    service/             MatchingService 撮合引擎、OrderBookService、AdminService
    api/                 REST 控制器与 DTO
  src/main/resources/db/migration/V1__init.sql
  src/test/              单测 + 真实 PostgreSQL（嵌入式发行版）集成测试
frontend/                React + TS
  src/replay.ts          固定命令回放脚本
  src/tick.ts            前端整数刻度换算（bigint，不用 Number 做价格运算）
  src/reconcile.ts       前端守恒/对账核对
  src/components/        下单表单 / 深度 / 成交 / 订单 / 回放 / 守恒条
scripts/
  replay_verify.py       HTTP 端到端核对（25 项断言）
  record_session.py      逐步录制页面状态 -> docs/replay-recording.md
docs/replay-recording.md 固定脚本的逐步页面变化录制（真实 HTTP 调用产物）
docker-compose.yml       PostgreSQL 16
```

## 启动方式

### 1. 启动 PostgreSQL

```bash
docker compose up -d postgres
```

或使用任意 PG16 实例，用环境变量覆盖连接信息：
`DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD`（默认 `localhost:5432/matchdb/match/match`）。
Flyway 在应用启动时自动建表。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

> 无 Docker 时，测试用的嵌入式 PG 也可直接起库：
> `mvn test-compile` 后运行测试类 `com.teaching.match.EmbedPgServer`（默认端口 5439），
> 再以 `DB_PORT=5439 DB_USER=postgres DB_PASSWORD=postgres` 启动（该模式下应用会用
> postgres 超级库连接；正式使用请走 docker-compose）。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 （Vite 已把 `/api` 代理到 8080）。

## 固定命令回放（课堂演示 / 可录制）

页面右侧「固定命令回放脚本」面板可自动或单步执行：

| # | 命令 | 演示点 |
|---|------|--------|
| 0 | `RESET` | 清空订单/成交、序列归位，保证可重复录制 |
| 1–2 | `SELL S-1/S-2 10.00 x30/x20` | **同价排队**，S-1 先于 S-2 |
| 3–4 | `SELL S-3 10.10 x30`、`S-4 10.30 x100` | 造出第二档与超价档 |
| 5 | `BUY B-1 10.20 x100` | **吃掉两档**：T000001 10.00×30(S-1)、T000002 10.00×20(S-2)、T000003 10.10×30(S-3)；共成交 80，剩 20 **部分成交**挂簿；10.30 不被吃 |
| 6 | `CANCEL B-1` | **剩余撤单**，撤掉在簿的 20 |
| 7 | 用 `S-1` 改价改量重提 | **幂等拦截**，订单数不增加 |

终局守恒：`280 = 160(双边成交) + 20(撤单) + 100(在簿 S-4)`，成交单边合计 80。
逐步页面状态录制见 [`docs/replay-recording.md`](docs/replay-recording.md)。

命令行复核（后端运行时）：

```bash
python3 scripts/replay_verify.py        # 25 项 PASS
python3 scripts/record_session.py       # 重新生成录制文档
```

## API 摘要

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 下单（body: clientOrderId, symbol, side, limitPrice(字符串), quantity）；重复键返回 200+`deduplicated` |
| DELETE | `/api/orders/{id}` | 撤销剩余；已成交/已撤返回 409 |
| GET | `/api/orders` / `/api/orders/{id}` | 订单列表 / 详情 |
| GET | `/api/book` | 买卖盘聚合深度 |
| GET | `/api/trades` | 成交明细（新→旧） |
| GET | `/api/snapshot` | 一次拿齐盘口/订单/成交/守恒（页面与回放都用它核对变化） |
| POST | `/api/admin/reset` | 教学重置（可用 `ADMIN_RESET_ENABLED=false` 关闭） |

价格字段同时返回 `limitPriceTicks`（整数）与 `limitPrice`（刻度换算字符串），
前端展示用字符串、比较排序用整数。

## 测试

```bash
cd backend && mvn test
```

- `TickRulesTest`：刻度换算的精确性（越刻度拒绝、整数 tick 比较）。
- `MatchingFlowIntegrationTest`：用**真实 PostgreSQL**（zonky 嵌入式发行版）做全链路验证：
  同价时间优先、买单吃两档+部分成交、卖单价格优先、撤单、重复 clientOrderId、守恒核对。
