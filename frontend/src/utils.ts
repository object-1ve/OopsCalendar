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
