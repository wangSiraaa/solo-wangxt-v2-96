import type { ReplayCommand } from './types';

/**
 * 固定命令脚本（全部使用合成订单，单标的 SYNTH-01，价格单位为整数刻度 0.01）。
 *
 * 脚本设计的教学目标：
 *  - S1/S2/S3 在 10.00 同价排队，演示“同价时间优先 FIFO”；
 *  - B1 买入 15：S1 全成 10、S2 部分成交 5，演示“部分成交”；
 *  - 撤掉 S2/S3 剩余，清空 10.00 档，演示“剩余撤单（已成交部分保留）”；
 *  - A1/A2 挂 10.01/10.02，B2 买入 18 依次吃掉 10.01、10.02 两档并部分成交，
 *    演示“价格优先、吃掉两档报价”，成交价取 maker 价；
 *  - C1 买单部分成交后撤剩余，再次演示“部分成交 + 剩余撤单”；
 *  - 末尾重复提交 S1，验证“订单号重复提交不能新增订单”。
 */
export const REPLAY_SCRIPT: ReplayCommand[] = [
  { type: 'PLACE', clientOrderId: 'S1', side: 'SELL', priceTicks: 1000, quantity: 10, note: '卖单 10.00 ×10 入簿排队' },
  { type: 'PLACE', clientOrderId: 'S2', side: 'SELL', priceTicks: 1000, quantity: 10, note: '卖单 10.00 ×10 同价排第二' },
  { type: 'PLACE', clientOrderId: 'S3', side: 'SELL', priceTicks: 1000, quantity: 10, note: '卖单 10.00 ×10 同价排第三' },
  { type: 'PLACE', clientOrderId: 'B1', side: 'BUY', priceTicks: 1000, quantity: 15, note: '买单 10.00 ×15：S1 全成10、S2 部成5（FIFO+部分成交）' },
  { type: 'CANCEL', clientOrderId: 'S2', note: '撤销 S2 剩余 5（已成交 5 保留）' },
  { type: 'CANCEL', clientOrderId: 'S3', note: '撤销 S3 全部 10（未成交），10.00 档清空' },
  { type: 'PLACE', clientOrderId: 'A1', side: 'SELL', priceTicks: 1001, quantity: 8, note: '卖单 10.01 ×8 第一档' },
  { type: 'PLACE', clientOrderId: 'A2', side: 'SELL', priceTicks: 1002, quantity: 20, note: '卖单 10.02 ×20 第二档' },
  { type: 'PLACE', clientOrderId: 'B2', side: 'BUY', priceTicks: 1002, quantity: 18, note: '买单 10.02 ×18：吃掉 10.01 全部8、10.02 部成10（吃掉两档）' },
  { type: 'PLACE', clientOrderId: 'C1', side: 'BUY', priceTicks: 1000, quantity: 12, note: '买单 10.00 ×12 入簿' },
  { type: 'PLACE', clientOrderId: 'T1', side: 'SELL', priceTicks: 1000, quantity: 7, note: '卖单 10.00 ×7：C1 部分成交 7，剩余 5' },
  { type: 'CANCEL', clientOrderId: 'C1', note: '撤销 C1 剩余 5（已成交 7 保留）' },
  { type: 'PLACE', clientOrderId: 'S1', side: 'SELL', priceTicks: 999, quantity: 99, expectReject: true, expectErrorCode: 'DUPLICATE_ORDER', note: '重复订单号 S1：必须被拒绝，不得新增订单' },
];

/** 脚本结束时人工预期的核对值（数量守恒恒为 0）。 */
export const EXPECTED_END_STATE = {
  totalTradeVolume: 40, // 15 + 18 + 7
  buyFilledQuantity: 40,
  sellFilledQuantity: 40,
};
