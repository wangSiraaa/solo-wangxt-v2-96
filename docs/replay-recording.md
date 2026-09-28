# 固定命令回放 · 页面变化录制

标的 `SYNTH-0`（合成订单，不接真实市场），tick=0.01。
下列每一步对应前端「固定命令回放」面板的同一条命令；数据来自对运行中后端的真实 HTTP 调用。

## 步骤 0 · RESET

```
卖盘(高→低): 空
买盘(高→低): 空
订单: 空
成交: 空
守恒: orig 0 = filled(双边) 0 + cancelled 0 + book 0  | 成交单边合计 0（×2=0） => ✓
```

## 步骤 1 · SELL S-1 10.00 x30

接口结果：HTTP 201，新订单 #1，本单成交 0 笔

```
卖盘(高→低): 10.00 x30 (1单)
买盘(高→低): 空
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=0 bookRem=30 NEW
成交: 空
守恒: orig 30 = filled(双边) 0 + cancelled 0 + book 30  | 成交单边合计 0（×2=0） => ✓
```

## 步骤 2 · SELL S-2 10.00 x20

接口结果：HTTP 201，新订单 #2，本单成交 0 笔

```
卖盘(高→低): 10.00 x50 (2单)
买盘(高→低): 空
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=0 bookRem=30 NEW; #2 S-2 SELL 10.00 orig=20 filled=0 bookRem=20 NEW
成交: 空
守恒: orig 50 = filled(双边) 0 + cancelled 0 + book 50  | 成交单边合计 0（×2=0） => ✓
```

## 步骤 3 · SELL S-3 10.10 x30

接口结果：HTTP 201，新订单 #3，本单成交 0 笔

```
卖盘(高→低): 10.10 x30 (1单) | 10.00 x50 (2单)
买盘(高→低): 空
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=0 bookRem=30 NEW; #2 S-2 SELL 10.00 orig=20 filled=0 bookRem=20 NEW; #3 S-3 SELL 10.10 orig=30 filled=0 bookRem=30 NEW
成交: 空
守恒: orig 80 = filled(双边) 0 + cancelled 0 + book 80  | 成交单边合计 0（×2=0） => ✓
```

## 步骤 4 · SELL S-4 10.30 x100

接口结果：HTTP 201，新订单 #4，本单成交 0 笔

```
卖盘(高→低): 10.30 x100 (1单) | 10.10 x30 (1单) | 10.00 x50 (2单)
买盘(高→低): 空
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=0 bookRem=30 NEW; #2 S-2 SELL 10.00 orig=20 filled=0 bookRem=20 NEW; #3 S-3 SELL 10.10 orig=30 filled=0 bookRem=30 NEW; #4 S-4 SELL 10.30 orig=100 filled=0 bookRem=100 NEW
成交: 空
守恒: orig 180 = filled(双边) 0 + cancelled 0 + book 180  | 成交单边合计 0（×2=0） => ✓
```

## 步骤 5 · BUY B-1 10.20 x100

接口结果：HTTP 201，新订单 #5，本单成交 3 笔

```
卖盘(高→低): 10.30 x100 (1单)
买盘(高→低): 10.20 x20 (1单)
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=30 bookRem=0 FILLED; #2 S-2 SELL 10.00 orig=20 filled=20 bookRem=0 FILLED; #3 S-3 SELL 10.10 orig=30 filled=30 bookRem=0 FILLED; #4 S-4 SELL 10.30 orig=100 filled=0 bookRem=100 NEW; #5 B-1 BUY 10.20 orig=100 filled=80 bookRem=20 PARTIALLY_FILLED
成交(时间正序): T000001 10.00x30 maker=#1 taker=#5; T000002 10.00x20 maker=#2 taker=#5; T000003 10.10x30 maker=#3 taker=#5
守恒: orig 280 = filled(双边) 160 + cancelled 0 + book 120  | 成交单边合计 80（×2=160） => ✓
```

## 步骤 6 · CANCEL B-1 剩余 20

接口结果：HTTP 200，撤销 #5 的剩余 20

```
卖盘(高→低): 10.30 x100 (1单)
买盘(高→低): 空
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=30 bookRem=0 FILLED; #2 S-2 SELL 10.00 orig=20 filled=20 bookRem=0 FILLED; #3 S-3 SELL 10.10 orig=30 filled=30 bookRem=0 FILLED; #4 S-4 SELL 10.30 orig=100 filled=0 bookRem=100 NEW; #5 B-1 BUY 10.20 orig=100 filled=80 bookRem=0 CANCELLED
成交(时间正序): T000001 10.00x30 maker=#1 taker=#5; T000002 10.00x20 maker=#2 taker=#5; T000003 10.10x30 maker=#3 taker=#5
守恒: orig 280 = filled(双边) 160 + cancelled 20 + book 100  | 成交单边合计 80（×2=160） => ✓
```

## 步骤 7 · 重复提交 S-1（10.50 x999）幂等拦截

接口结果：HTTP 200，deduplicated=true，未新增订单

```
卖盘(高→低): 10.30 x100 (1单)
买盘(高→低): 空
订单(旧→新): #1 S-1 SELL 10.00 orig=30 filled=30 bookRem=0 FILLED; #2 S-2 SELL 10.00 orig=20 filled=20 bookRem=0 FILLED; #3 S-3 SELL 10.10 orig=30 filled=30 bookRem=0 FILLED; #4 S-4 SELL 10.30 orig=100 filled=0 bookRem=100 NEW; #5 B-1 BUY 10.20 orig=100 filled=80 bookRem=0 CANCELLED
成交(时间正序): T000001 10.00x30 maker=#1 taker=#5; T000002 10.00x20 maker=#2 taker=#5; T000003 10.10x30 maker=#3 taker=#5
守恒: orig 280 = filled(双边) 160 + cancelled 20 + book 100  | 成交单边合计 80（×2=160） => ✓
```

> 每一步均满足：`orig = filled(双边) + cancelled + book`；成交明细单边总量 ×2 与 filled 相等；盘口聚合量与 LIVE 订单剩余量相等。
