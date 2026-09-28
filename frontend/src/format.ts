/**
 * 整数刻度 <-> 显示字符串。
 * 价格比较永远发生在整数刻度层面；这里只负责“画给人看”。
 */
export function formatPrice(priceTicks: number, scale: number): string {
  const divisor = 10 ** scale;
  const whole = Math.trunc(priceTicks / divisor);
  const frac = Math.abs(priceTicks % divisor);
  if (scale === 0) return String(whole);
  return `${whole}.${String(frac).padStart(scale, '0')}`;
}

/** 把用户输入的十进制价格字符串严格转换为整数刻度（不做浮点运算）。 */
export function parsePriceToTicks(input: string, scale: number): number | null {
  const trimmed = input.trim();
  if (!/^\d+$|^\d+\.\d+$/.test(trimmed)) return null;
  const [intPart = '', fracRaw = ''] = trimmed.split('.');
  if (fracRaw.length > scale) return null;
  const frac = fracRaw.padEnd(scale, '0');
  return Number(intPart) * 10 ** scale + (scale === 0 ? 0 : Number(frac || '0'));
}

export function formatTime(iso: string): string {
  const d = new Date(iso);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  const ms = String(d.getMilliseconds()).padStart(3, '0');
  return `${hh}:${mm}:${ss}.${ms}`;
}
