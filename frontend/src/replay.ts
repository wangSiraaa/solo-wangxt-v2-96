import type { Side } from './types';

/**
 * 固定命令回放脚本（仅合成订单，不接真实市场）。
 *
 * 教学顺序：
 *  1) S-1/S-2 同价 10.00 排队 —— 演示同价时间优先；
 *  2) S-3 挂 10.10、S-4 挂 10.30 —— 造两档可成交报价与一档超价报价；
 *  3) B-1 @10.20 x100 —— 吃掉两档：先 10.00 的 S-1(30)+S-2(20)，
 *     再 10.10 的 S-3(30)，共成交 80，剩余 20 部分成交挂簿；
 *  4) 撤销 B-1 的剩余 20；
 *  5) 用同一个 clientOrderId=S-1 改价改量重提 —— 必须被幂等拦截，不新增订单。
 *
 * 撤单步骤通过 ref 引用此前订单的 clientOrderId，运行时从快照里解析 id。
 */
export type ReplayAction =
  | { kind: 'reset' }
  | { kind: 'place'; clientOrderId: string; side: Side; limitPrice: string; quantity: number }
  | { kind: 'cancel'; ref: string };

export interface ReplayStep {
  command: string;
  narration: string;
  expect: string;
  action: ReplayAction;
}

export const REPLAY_SCRIPT: ReplayStep[] = [
  {
    command: 'RESET',
    narration: '清空订单与成交，成交号序列归位，保证录制可重复。',
    expect: '订单 0、成交 0、守恒成立（0=0+0+0）。',
    action: { kind: 'reset' },
  },
  {
    command: 'SELL S-1 10.00 x30',
    narration: '第一张卖单进入卖盘第一档。',
    expect: '卖盘 10.00 档 30（1 单）。',
    action: { kind: 'place', clientOrderId: 'S-1', side: 'SELL', limitPrice: '10.00', quantity: 30 },
  },
  {
    command: 'SELL S-2 10.00 x20',
    narration: '同价第二张：价格相同，按时间排在 S-1 后面（同价时间优先）。',
    expect: '卖盘 10.00 档 50（2 单），队列顺序 S-1 → S-2。',
    action: { kind: 'place', clientOrderId: 'S-2', side: 'SELL', limitPrice: '10.00', quantity: 20 },
  },
  {
    command: 'SELL S-3 10.10 x30',
    narration: '卖出第二档报价。',
    expect: '卖盘出现两档：10.00 x50、10.10 x30。',
    action: { kind: 'place', clientOrderId: 'S-3', side: 'SELL', limitPrice: '10.10', quantity: 30 },
  },
  {
    command: 'SELL S-4 10.30 x100',
    narration: '超出稍后买单限价的第三档，不应被吃到。',
    expect: '卖盘三档：10.00 / 10.10 / 10.30。',
    action: { kind: 'place', clientOrderId: 'S-4', side: 'SELL', limitPrice: '10.30', quantity: 100 },
  },
  {
    command: 'BUY B-1 10.20 x100',
    narration: '主动买单进场：价格优先先吃最低卖价；10.00 档内按时间优先，'
      + '先吃 S-1 的 30，再吃 S-2 的 20；随后吃掉 10.10 档 S-3 的 30。'
      + '10.30 超过限价 10.20，停止撮合；剩余 20 挂入买盘（部分成交）。',
    expect: '新增 3 笔成交 T000001/2/3，价格依次 10.00/10.00/10.10；'
      + 'B-1 成交 80 剩 20，状态 PARTIALLY_FILLED；卖盘只剩 10.30 x100。',
    action: { kind: 'place', clientOrderId: 'B-1', side: 'BUY', limitPrice: '10.20', quantity: 100 },
  },
  {
    command: 'CANCEL B-1 (剩余 20)',
    narration: '撤销 B-1 尚未成交的 20 股（剩余撤单）。',
    expect: 'B-1 状态 CANCELLED；买盘清空；S-4 的 10.30 x100 原封不动。',
    action: { kind: 'cancel', ref: 'B-1' },
  },
  {
    command: 'SELL S-1 10.50 x999（重复 clientOrderId）',
    narration: '故意用已存在的 S-1 改价改量重提，验证幂等：系统不得新增订单。',
    expect: '返回 deduplicated=true，订单总数不变，原 S-1 仍是 10.00 x30、FILLED。',
    action: { kind: 'place', clientOrderId: 'S-1', side: 'SELL', limitPrice: '10.50', quantity: 999 },
  },
];
