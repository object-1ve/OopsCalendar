import type { EarningsResponse, HealthResponse } from './types'

/** 生成 YYYY-MM-DD。 */
function fmt(d: Date): string {
  const y = d.getUTCFullYear()
  const m = String(d.getUTCMonth() + 1).padStart(2, '0')
  const day = String(d.getUTCDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 某月的第一天与最后一天(month 为 0 基)。 */
export function monthRange(year: number, month: number): { from: string; to: string } {
  const first = new Date(Date.UTC(year, month, 1))
  const last = new Date(Date.UTC(year, month + 1, 0))
  return { from: fmt(first), to: fmt(last) }
}

async function getJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  // no-store:财报数据/数据源状态需要保持新鲜,禁用浏览器 HTTP 缓存
  const resp = await fetch(url, { signal, cache: 'no-store' })
  if (!resp.ok) {
    let message = `请求失败 (HTTP ${resp.status})`
    try {
      const body = (await resp.json()) as { message?: string }
      if (body.message) message = body.message
    } catch {
      // 非 JSON 错误体,保留默认信息
    }
    throw new Error(message)
  }
  return (await resp.json()) as T
}

/** 按月拉取财报日历。 */
export function fetchEarnings(year: number, month: number, signal?: AbortSignal): Promise<EarningsResponse> {
  const { from, to } = monthRange(year, month)
  return getJson<EarningsResponse>(`/api/earnings?from=${from}&to=${to}`, signal)
}

/** 获取数据源健康状态。 */
export function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return getJson<HealthResponse>('/api/health', signal)
}
