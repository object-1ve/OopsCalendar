import type {
  EarningsResponse,
  FavoritesResponse,
  HealthResponse,
  NewsFavoritesResponse,
  NewsItem,
  NewsPreferences,
  NewsResponse,
  NewsSourceMeta,
} from './types'

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

/** 按月拉取财报日历;refresh=true 绕过后端缓存强制取最新。 */
export function fetchEarnings(year: number, month: number, signal?: AbortSignal, refresh = false): Promise<EarningsResponse> {
  const { from, to } = monthRange(year, month)
  const q = refresh ? '&refresh=true' : ''
  return getJson<EarningsResponse>(`/api/earnings?from=${from}&to=${to}${q}`, signal)
}

/** 单独拉取/刷新某一天(refresh=true 绕过后端缓存)。 */
export function fetchEarningsDay(date: string, refresh: boolean, signal?: AbortSignal): Promise<EarningsResponse> {
  const q = refresh ? '&refresh=true' : ''
  return getJson<EarningsResponse>(`/api/earnings?from=${date}&to=${date}${q}`, signal)
}

/** 获取数据源健康状态。 */
export function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return getJson<HealthResponse>('/api/health', signal)
}

export interface ValuationResponse {
  date: string
  count: number
  values: Record<string, number>
}

/** 某日财报公司的市盈率(仅知名公司)。 */
export function fetchValuation(date: string, signal?: AbortSignal): Promise<ValuationResponse> {
  return getJson<ValuationResponse>(`/api/valuation?date=${date}`, signal)
}

/** 财经快讯;sources 为逗号分隔的数据源 key,缺省拉全部。 */
export function fetchNews(sources?: string, signal?: AbortSignal): Promise<NewsResponse> {
  const q = sources && sources.trim() ? `?sources=${encodeURIComponent(sources.trim())}` : ''
  return getJson<NewsResponse>(`/api/news${q}`, signal)
}

/** 可用快讯数据源列表(供"全部"的显示配置使用)。 */
export function fetchNewsSources(signal?: AbortSignal): Promise<NewsSourceMeta[]> {
  return getJson<NewsSourceMeta[]>('/api/news/sources', signal)
}

export interface NewsCountResponse {
  count: number
}

/** 数据库中已入库快讯的总条数(全部数据源,不受筛选影响)。 */
export function fetchNewsCount(signal?: AbortSignal): Promise<NewsCountResponse> {
  return getJson<NewsCountResponse>('/api/news/count', signal)
}

export interface NewsHistoryResponse {
  items: NewsItem[]
  total: number
  offset: number
  limit: number
}

/** 已入库快讯历史分页(时间倒序,供无限滚动);sources 逗号分隔,search 模糊匹配标题/摘要。 */
export function fetchNewsHistory(
  offset: number,
  limit: number,
  sources?: string,
  search?: string,
  signal?: AbortSignal,
): Promise<NewsHistoryResponse> {
  const p = new URLSearchParams({ offset: String(offset), limit: String(limit) })
  if (sources && sources.trim()) p.set('sources', sources.trim())
  if (search && search.trim()) p.set('search', search.trim())
  return getJson<NewsHistoryResponse>(`/api/news/history?${p.toString()}`, signal)
}

/** 读取服务端保存的数据源偏好(全项目共享一份)。 */
export function fetchNewsPreferences(signal?: AbortSignal): Promise<NewsPreferences> {
  return getJson<NewsPreferences>('/api/news/preferences', signal)
}

/** 保存数据源偏好到服务端(全项目共享一份,持久化,换浏览器/清缓存可恢复)。 */
export async function saveNewsPreferences(sources: string[]): Promise<NewsPreferences> {
  const resp = await fetch('/api/news/preferences', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sources }),
    cache: 'no-store',
  })
  if (!resp.ok) {
    throw new Error(`保存失败 (HTTP ${resp.status})`)
  }
  return (await resp.json()) as NewsPreferences
}

/** 读取服务端保存的收藏(全项目共享一份)。 */
export function fetchFavorites(signal?: AbortSignal): Promise<FavoritesResponse> {
  return getJson<FavoritesResponse>('/api/favorites', signal)
}

/** 保存收藏到服务端(全项目共享一份,持久化,换浏览器/清缓存/换端口可恢复)。 */
export async function saveFavorites(symbols: string[]): Promise<FavoritesResponse> {
  const resp = await fetch('/api/favorites', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ symbols }),
    cache: 'no-store',
  })
  if (!resp.ok) {
    throw new Error(`保存失败 (HTTP ${resp.status})`)
  }
  return (await resp.json()) as FavoritesResponse
}

/** 读取服务端保存的快讯收藏(全项目共享一份,整条快讯快照)。 */
export function fetchNewsFavorites(signal?: AbortSignal): Promise<NewsFavoritesResponse> {
  return getJson<NewsFavoritesResponse>('/api/news/favorites', signal)
}

/** 保存快讯收藏到服务端(全项目共享一份,整表替换,持久化到 SQLite 数据库)。 */
export async function saveNewsFavorites(items: NewsItem[]): Promise<NewsFavoritesResponse> {
  const resp = await fetch('/api/news/favorites', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ items }),
    cache: 'no-store',
  })
  if (!resp.ok) {
    throw new Error(`保存失败 (HTTP ${resp.status})`)
  }
  return (await resp.json()) as NewsFavoritesResponse
}
