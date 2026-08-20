import type { EarningsEvent } from './types'

/**
 * 按营收降序排序:已公布用实际营收,未公布用预估;两者都没有排最后(按代码升序)。
 * 日历格子与详情弹窗共用,保证两处排序一致。
 */
export function sortByRevenue(events: EarningsEvent[]): EarningsEvent[] {
  events.sort((a, b) => {
    const ra = a.revenue ?? a.revenueEstimated
    const rb = b.revenue ?? b.revenueEstimated
    if (ra == null && rb == null) return a.symbol.localeCompare(b.symbol)
    if (ra == null) return 1
    if (rb == null) return -1
    return rb - ra
  })
  return events
}

/**
 * 营收预估是否与口径不对齐(存疑)。
 * 已公布时实际/预估差距超过 5 倍(或不足 1/5),说明预估大概率不是同一口径:
 * 典型如 BN(控股集团),分析师营收一致预期只覆盖部分口径(资管费+净投资收益),
 * 而公司披露的 GAAP 总营收包含并表的运营业务与投资收益,两者相差一个数量级。
 * 此时前端不展示该预估数字,仅显示"口径存疑"提示,避免误导对比。
 */
export function isRevenueEstimateSuspicious(event: EarningsEvent): boolean {
  const actual = event.revenue
  const estimated = event.revenueEstimated
  if (actual == null || estimated == null || estimated === 0) return false
  const ratio = actual / estimated
  return ratio > 5 || ratio < 0.2
}

/** 快讯时间显示:统一北京时间(快讯源均为北京时间发布),精确到秒。
 *  始终显示完整年月日,格式 YYYY-MM-DD HH:mm:ss。 */
export function formatNewsTime(ms: number | null): string {
  if (ms == null) return ''
  const fmt = (t: Date) =>
    new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(t)
  const get = (parts: Intl.DateTimeFormatPart[], type: string) => parts.find((p) => p.type === type)?.value ?? ''
  const p = fmt(new Date(ms))
  const y = get(p, 'year')
  const mo = get(p, 'month')
  const day = get(p, 'day')
  const hh = get(p, 'hour')
  const mm = get(p, 'minute')
  const ss = get(p, 'second')
  return `${y}-${mo}-${day} ${hh}:${mm}:${ss}`
}

/** 字节数格式化为可读大小(B/KB/MB/GB)。 */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let v = bytes
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i += 1
  }
  return `${i === 0 ? v : v.toFixed(1)} ${units[i]}`
}
