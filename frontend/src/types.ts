/**
 * 与后端 /api 契约对齐的类型定义。
 */

/** 财报发布时段:盘前 / 盘后 / 盘中 / 待定 */
export type Session = 'BMO' | 'AMC' | 'DNH' | 'UNKNOWN'

export interface EarningsEvent {
  date: string // YYYY-MM-DD
  symbol: string
  name: string | null
  /** 公司中文名称(知名公司提供) */
  nameZh: string | null
  /** 行业分类(中文),知名公司内置表提供 */
  industry: string | null
  session: Session
  /** true = 已公布(有实际 EPS/营收);false = 未公布(仅预估) */
  confirmed: boolean
  eps: number | null
  epsEstimated: number | null
  revenue: number | null
  revenueEstimated: number | null
  /** 数据源:finnhub / fmp / mock */
  source: 'finnhub' | 'fmp' | 'mock'
}

export interface EarningsResponse {
  from: string
  to: string
  count: number
  source: 'finnhub' | 'fmp' | 'mock'
  events: EarningsEvent[]
}

export interface HealthResponse {
  status: string
  provider: 'finnhub' | 'fmp' | 'mock'
  message: string
  timestamp: string
}

export const SESSION_LABEL: Record<Session, string> = {
  BMO: '盘前',
  AMC: '盘后',
  DNH: '盘中',
  UNKNOWN: '待定',
}

/** 一条财经快讯 */
export interface NewsItem {
  id: string
  title: string
  url: string
  /** 发布时间 epoch 毫秒;未知为 null */
  pubDate: number | null
  /** 数据源 key,如 jin10 / cls */
  source: string
  /** 数据源展示名 */
  sourceName: string
  summary: string | null
  important: boolean
}

/** 新闻数据源元信息 */
export interface NewsSourceMeta {
  key: string
  name: string
  icon: string | null
}

export interface NewsResponse {
  items: NewsItem[]
  sources: NewsSourceMeta[]
  fetchedAt: number
}

/** 数据源偏好(configured=false = 从未保存过,默认全开;全项目共享一份) */
export interface NewsPreferences {
  configured: boolean
  sources: string[]
}

/** 收藏公司响应(configured=false = 从未保存过收藏;全项目共享一份) */
export interface FavoritesResponse {
  configured: boolean
  symbols: string[]
}

/** 快讯收藏响应(configured=false = 从未收藏过快讯;items 按收藏时间倒序;全项目共享一份) */
export interface NewsFavoritesResponse {
  configured: boolean
  items: NewsItem[]
}
