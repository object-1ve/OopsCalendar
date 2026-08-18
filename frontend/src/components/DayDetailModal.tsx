import { useEffect, useMemo, useState } from 'react'
import type { EarningsEvent, Session } from '../types'
import { fetchValuation } from '../api'
import { sortByRevenue, isRevenueEstimateSuspicious } from '../utils'
import EventChip from './EventChip'

interface Props {
  date: string // YYYY-MM-DD
  events: EarningsEvent[]
  favorites: Set<string>
  onToggleFavorite: (symbol: string) => void
  /** 单独刷新该日(refresh=true 绕过缓存),返回是否成功。 */
  onRefreshDay: (date: string) => Promise<boolean>
  onClose: () => void
}

/** 弹窗内单日财报过多时,默认只渲染前 N 条,提示用筛选。 */
const MAX_RENDER = 150

const SESSION_OPTIONS: { value: Session; label: string }[] = [
  { value: 'BMO', label: '盘前' },
  { value: 'AMC', label: '盘后' },
  { value: 'DNH', label: '盘中' },
  { value: 'UNKNOWN', label: '待定' },
]

const STATUS_OPTIONS: { value: 'all' | 'confirmed' | 'pending'; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'confirmed', label: '已公布' },
  { value: 'pending', label: '未公布' },
]

/** 点击日期后弹出的当日财报详情,支持搜索/时段/公布状态筛选。 */
export default function DayDetailModal({ date, events, favorites, onToggleFavorite, onRefreshDay, onClose }: Props) {
  const [, m, d] = date.split('-')
  const [query, setQuery] = useState('')
  const [sessions, setSessions] = useState<Set<Session>>(new Set())
  const [status, setStatus] = useState<'all' | 'confirmed' | 'pending'>('all')
  const [peMap, setPeMap] = useState<Record<string, number>>({})
  const [refreshing, setRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState<string | null>(null)

  // 打开弹窗时加载当日知名公司的市盈率
  useEffect(() => {
    const controller = new AbortController()
    setPeMap({})
    fetchValuation(date, controller.signal)
      .then((resp) => setPeMap(resp.values))
      .catch(() => setPeMap({}))
    return () => controller.abort()
  }, [date])

  const handleRefreshDay = async () => {
    setRefreshing(true)
    setRefreshError(null)
    const ok = await onRefreshDay(date)
    setRefreshing(false)
    if (!ok) setRefreshError('刷新失败,请稍后重试')
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    const list = sortByRevenue(
      events.filter((e) => {
        if (q) {
          const symbol = e.symbol.toLowerCase()
          const name = (e.name ?? '').toLowerCase()
          if (!symbol.includes(q) && !name.includes(q)) return false
        }
        if (sessions.size > 0 && !sessions.has(e.session)) return false
        if (status === 'confirmed' && !e.confirmed) return false
        if (status === 'pending' && e.confirmed) return false
        return true
      }),
    )
    // 收藏的公司置顶(同组内保持营收顺序,Array.sort 稳定)
    list.sort((a, b) => Number(favorites.has(b.symbol)) - Number(favorites.has(a.symbol)))
    return list
  }, [events, query, sessions, status, favorites])

  const toggleSession = (s: Session) => {
    setSessions((prev) => {
      const next = new Set(prev)
      if (next.has(s)) next.delete(s)
      else next.add(s)
      return next
    })
  }

  const hasFilter = query.trim() !== '' || sessions.size > 0 || status !== 'all'
  const hidden = filtered.length > MAX_RENDER

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>
            {Number(m)} 月 {Number(d)} 日财报 <span className="modal-count">(共 {events.length} 家)</span>
          </h2>
          <div className="modal-actions">
            {refreshError && <span className="refresh-error">{refreshError}</span>}
            <button
              className="btn small"
              onClick={handleRefreshDay}
              disabled={refreshing}
              title="重新拉取该日财报(绕过后端缓存)"
            >
              {refreshing ? '刷新中…' : '↻ 刷新该日'}
            </button>
            <button className="modal-close" onClick={onClose} aria-label="关闭">
              ×
            </button>
          </div>
        </div>

        <div className="modal-filters">
          <input
            className="filter-search"
            type="text"
            placeholder="搜索代码或公司名,如 NBIS / Cerebras"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="filter-row">
            <span className="filter-label">时段</span>
            {SESSION_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                className={`filter-btn session-${opt.value.toLowerCase()} ${sessions.has(opt.value) ? 'active' : ''}`}
                onClick={() => toggleSession(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>
          <div className="filter-row">
            <span className="filter-label">状态</span>
            {STATUS_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                className={`filter-btn ${status === opt.value ? 'active' : ''}`}
                onClick={() => setStatus(opt.value)}
              >
                {opt.label}
              </button>
            ))}
            {hasFilter && (
              <button className="filter-btn clear" onClick={() => { setQuery(''); setSessions(new Set()); setStatus('all') }}>
                ✕ 清空筛选
              </button>
            )}
          </div>
          <div className="filter-count">
            {hasFilter ? `筛选后 ${filtered.length} 家` : `共 ${events.length} 家`}
            {hidden && <span className="filter-hint"> · 列表较长,仅显示前 {MAX_RENDER} 条,请用筛选缩小范围</span>}
          </div>
        </div>

        <div className="modal-body">
          {filtered.length === 0 && <p className="empty">无匹配的财报。</p>}
          {filtered.slice(0, hidden ? MAX_RENDER : filtered.length).map((e) => {
            const fav = favorites.has(e.symbol)
            return (
              <div
                key={`${e.date}-${e.symbol}`}
                className={`event-row session-${e.session.toLowerCase()}${fav ? ' favorite' : ''}`}
              >
                <div className="event-main">
                  <button
                    className={`fav-btn ${fav ? 'active' : ''}`}
                    onClick={() => onToggleFavorite(e.symbol)}
                    title={fav ? '取消收藏' : '收藏该公司,财报用黄色标识'}
                  >
                    {fav ? '★' : '☆'}
                  </button>
                  <div className="event-company">
                    <span className="event-name">{e.nameZh ?? e.name ?? e.symbol}</span>
                    {e.industry && <span className="industry-tag">{e.industry}</span>}
                  </div>
                  <span className="event-symbol">{e.symbol}</span>
                  <EventChip event={e} favorite={fav} />
                  {peMap[e.symbol] != null && (
                    <span className="pe-tag" title="市盈率 PE(TTM)">
                      PE {peMap[e.symbol].toFixed(1)}
                    </span>
                  )}
                  <span className={`status-badge ${e.confirmed ? 'done' : 'todo'}`}>
                    {e.confirmed ? '✓ 已公布' : '未公布'}
                  </span>
                </div>
                <div className="event-detail">
                  <span>
                    EPS 实际 <b>{e.eps ?? '—'}</b> / 预估 <b>{e.epsEstimated ?? '—'}</b>
                  </span>
                  <span>
                    营收(百万$)实际 <b>{e.revenue?.toLocaleString() ?? '—'}</b> / 预估{' '}
                    {e.revenueEstimated != null && !isRevenueEstimateSuspicious(e) ? (
                      <b>{e.revenueEstimated.toLocaleString()}</b>
                    ) : (
                      '—'
                    )}
                    {isRevenueEstimateSuspicious(e) && (
                      <span
                        className="warn-tag"
                        title={`实际与预估差距超 5 倍(实际 ${e.revenue?.toLocaleString() ?? '—'}M / 预估 ${e.revenueEstimated?.toLocaleString() ?? '—'}M),两者大概率不是同一营收口径。如 BN 这类控股集团,分析师营收预期只覆盖资管费+净投资收益,不含并表运营业务,故预估不作对比展示。`}
                      >
                        口径存疑
                      </span>
                    )}
                  </span>
                  <span className="source-tag">
                    数据源:
                    {e.source === 'fmp'
                      ? 'FMP 真实数据'
                      : e.source === 'finnhub'
                        ? 'Finnhub 真实数据'
                        : '演示数据'}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
