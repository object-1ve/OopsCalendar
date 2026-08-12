/**
 * 与后端 /api 契约对齐的类型定义。
 */

/** 财报发布时段:盘前 / 盘后 / 盘中 / 待定 */
export type Session = 'BMO' | 'AMC' | 'DNH' | 'UNKNOWN'

export interface EarningsEvent {
  date: string // YYYY-MM-DD
  symbol: string
  name: string | null
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
