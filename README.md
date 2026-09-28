# 限价订单连续竞价撮合教学系统（合成订单 · 单标的）

一个可验证的限价订单撮合全栈应用，**只处理合成订单，不接入任何真实市场**。首版仅支持一个交易标的 `SYNTH-01`。

- **前端**：React 18 + TypeScript + Vite —— 下单表单、买卖盘深度、成交明细、订单状态（可撤单）、数量守恒面板、**固定命令回放与页面变化录制**。
- **后端**：Java 17 + Spring Boot 3 —— 下单、撤单、成交/订单/盘口查询、守恒核对、回放重置 API。
- **存储**：PostgreSQL 15 —— `orders` 与 `trades` 两张表。
- **撮合**：内存订单簿（`TreeMap` 价格优先 + 同价位 `Deque` FIFO 时间优先），**价格/数量全程用整数刻度（`long`）比较，不使用浮点**。

## 撮合规则

1. **价格优先**：买单价高者先成交，卖单价低者先成交；
2. **同价时间优先**：同价位订单按入簿序号 `seq` FIFO；
3. **连续竞价 + 部分成交**：新订单立即与对手盘成交，一次可吃多档；
4. **剩余撤单**：可撤销剩余数量，已成交部分保留；
5. **订单号去重**：`clientOrderId` 唯一（DB 唯一约束 + 应用检查），重复提交返回 `409 DUPLICATE_ORDER`，不新增订单；
6. **成交价取 maker 报价**（taker 获得价格改善）；
7. 重启后从 PostgreSQL 中仍活跃的订单**重建撮合簿**。

### 整数刻度（不用浮点比较）

`tickSize = 0.01`，数据库与引擎中价格一律存整数刻度数：`10.00 -> 1000`、`10.02 -> 1002`。
撮合条件是整数比较：买单 `priceTicks >= 最低卖价刻度`；卖单反之。
前端用字符串严格解析输入（`parsePriceToTicks`），超刻度位数直接拒绝；小数只用于展示。

## 目录结构

```
backend/    Spring Boot 工程（Maven）
  src/main/java/com/tradinglab/matching/
    domain/    Order / Trade / Side / OrderStatus（JPA 实体与枚举）
    engine/    MatchingEngine（撮合核心）、BookSnapshot/BookLevel、MatchResult
    service/   OrderService（事务、去重、撮合编排、守恒核对）
    api/       REST 控制器、请求/响应 DTO、全局异常处理
  src/test/    6 个撮合规则集成测试（H2）
frontend/   React + TS 工程
  src/script.ts        固定命令脚本（与 replay.mjs 同一份 13 步脚本）
  src/components/      OrderForm / OrderBook / TradeList / OrderTable /
                       ConservationPanel / ReplayPanel
  replay.mjs           可独立运行的命令行录制与核对脚本
start-dev.sh
```

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/settings` | 标的与刻度（tickSize/scale/lotSize） |
| POST | `/api/orders` | 下单并立即撮合，返回订单最终状态 + 本次成交 |
| POST | `/api/orders/{clientOrderId}/cancel` | 撤销剩余数量 |
| GET | `/api/orders` | 全部订单 |
| GET | `/api/trades` | 成交明细（最新在前） |
| GET | `/api/book` | 买卖盘聚合深度（整数刻度） |
| GET | `/api/conservation` | 数量守恒核对 |
| POST | `/api/reset` | 清空订单/成交并重置撮合簿（回放用） |

下单请求体：

```json
{ "clientOrderId": "S1", "side": "SELL", "priceTicks": 1000, "quantity": 10 }
```

## 本地运行

### 后端（需要 JDK17、Maven、PostgreSQL）

```bash
# 建库
createdb -U match matchingdb        # 或 psql -c "CREATE DATABASE matchingdb;"
# 配置见 backend/src/main/resources/application.properties（用户名 match、无密码、localhost:5432）
cd backend
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 ，/api 代理到 8080
```

或直接 `./start-dev.sh`（本仓库是在无 root 的 Debian arm64 环境中，用用户态
JDK/Maven/PostgreSQL 搭建的，脚本按该布局启动）。

## 固定命令回放与“页面变化”录制

页面顶部的**固定命令回放**面板提供「自动回放 / 一次性执行 / 清空重置 / 导出录制 JSON」。
13 步脚本覆盖四个教学场景：

| 步骤 | 命令 | 演示点 |
| --- | --- | --- |
| 1–4 | S1/S2/S3 同挂 10.00，B1 买 15 | 同价 FIFO：S1 全成 10、S2 部成 5 |
| 5–6 | 撤 S2 剩 5、撤 S3 全 10 | 剩余撤单（已成交保留） |
| 7–9 | A1 挂 10.01×8、A2 挂 10.02×20，B2 买 18 | 吃掉两档：10.01×8 + 10.02×10 |
| 10–12 | C1 买 12、T1 卖 7、撤 C1 | 部分成交 7 后撤销剩余 5 |
| 13 | 重复提交 S1 | 409 拒绝，订单数不增加 |

每一步都会重新抓取盘口/订单/成交/守恒形成一帧**录制**（页面各区块看到的就是这些数据），可导出 JSON。

命令行等价脚本（驱动与页面相同的 HTTP API，逐帧录制并断言）：

```bash
cd frontend
node replay.mjs --out recording.json
```

预期结尾：

```
========== 核对结论 ==========
✓ 全部通过：同价FIFO、吃掉两档、部分成交、剩余撤单、重复拒单、数量守恒（balance=0）均验证一致
```

## 数量守恒核对

每笔成交恰好让一个买单和一个卖单各减少相同的剩余量，因此恒有：

```
订单累计成交量之和 = 2 × 成交总量
买方累计成交量 = 卖方累计成交量 = 成交总量
balance = (订单累计成交量 - 2×成交总量) + |买方成交 - 卖方成交| = 0
```

脚本终态：成交总量 **40**（15+18+7），买卖各 **40**，订单累计 **80**，`balance=0`。
该指标同时在页面面板、`/api/conservation`、录制脚本与数据库 SQL 四个层面核对。

## 自动化测试

```bash
cd backend && mvn test
```

覆盖：同价 FIFO、吃掉两档（价格优先 + 成交价取 maker 价）、部分成交后撤剩余、
重复订单号 409 且不新增、价格不交叉不成交、已成交订单不可撤——每个用例都附带守恒断言。
