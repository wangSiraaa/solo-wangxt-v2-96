/**
 * 价格刻度换算（纯整数/字符串运算，刻意不使用 Number 做价格运算）：
 * 输入形如 "10.00" 的字符串与刻度 "0.01"，输出 tick 整数。
 * 不落在刻度网格上的价格直接拒绝，与服务端规则一致。
 */

function toIntegerAndScale(decimal: string): { num: bigint; scale: number } {
  let s = decimal.trim();
  if (s === '') throw new Error('价格不能为空');
  let neg = false;
  if (s.startsWith('-')) {
    neg = true;
    s = s.slice(1);
  }
  if (!/^\d+(\.\d+)?$/.test(s)) throw new Error(`价格格式不正确: ${decimal}`);
  const [intPart, fracPart = ''] = s.split('.');
  const scale = fracPart.length;
  const num = BigInt(intPart + fracPart);
  return { num: neg ? -num : num, scale };
}

/** ticksPerUnit: tickSize=0.01 -> 100；同时做整除性校验。 */
export function ticksPerUnit(tickSize: string): bigint {
  const { num, scale } = toIntegerAndScale(tickSize);
  if (num <= 0n) throw new Error('tickSize 必须为正');
  const unit = 10n ** BigInt(scale);
  if (unit % num !== 0n) throw new Error('tickSize 必须是 10 的整数分之一');
  return unit / num;
}

/** 价格字符串 -> tick 整数；不允许越刻度。 */
export function priceToTicks(price: string, tickSize: string): bigint {
  const tpu = ticksPerUnit(tickSize);
  const { num: priceNum, scale: priceScale } = toIntegerAndScale(price);
  if (priceNum <= 0n) throw new Error('价格必须大于 0');
  const scaled = priceNum * tpu;
  const divisor = 10n ** BigInt(priceScale);
  if (scaled % divisor !== 0n) {
    throw new Error(`价格 ${price} 没有落在刻度网格上（最小变动 ${tickSize}）`);
  }
  return scaled / divisor;
}
