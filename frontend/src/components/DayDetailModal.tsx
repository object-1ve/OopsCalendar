import { useMemo, useState } from 'react'
import type { EarningsEvent, Session } from '../types'
import EventChip from './EventChip'

interface Props {
  date: string // YYYY-MM-DD
  events: EarningsEvent[]
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
export default function DayDetailModal({ date, events, onClose }: Props) {
  const [, m, d] = date.split('-')
  const [query, setQuery] = useState('')
  const [sessions, setSessions] = useState<Set<Session>>(new Set())
  const [status, setStatus] = useState<'all' | 'confirmed' | 'pending'>('all')

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return events
      .filter((e) => {
        if (q) {
          const symbol = e.symbol.toLowerCase()
          const name = (e.name ?? '').toLowerCase()
          if (!symbol.includes(q) && !name.includes(q)) return false
        }
        if (sessions.size > 0 && !sessions.has(e.session)) return false
        if (status === 'confirmed' && !e.confirmed) return false
        if (status === 'pending' && e.confirmed) return false
        return true
      })
      .sort((a, b) => {
        // 按营收降序:已公布用实际营收,未公布用预估;无数据排最后
        const ra = a.revenue ?? a.revenueEstimated
        const rb = b.revenue ?? b.revenueEstimated
        if (ra == null && rb == null) return a.symbol.localeCompare(b.symbol)
        if (ra == null) return 1
        if (rb == null) return -1
        return rb - ra
      })
  }, [events, query, sessions, status])

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
          <button className="modal-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
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
                className={`filter-btn ${sessions.has(opt.value) ? 'active' : ''}`}
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
          {filtered.slice(0, hidden ? MAX_RENDER : filtered.length).map((e) => (
            <div key={`${e.date}-${e.symbol}`} className="event-row">
              <div className="event-main">
                <div className="event-company">
                  <span className="event-name">{e.name ?? e.symbol}</span>
                  {e.industry && <span className="industry-tag">{e.industry}</span>}
                </div>
                <span className="event-symbol">{e.symbol}</span>
                <EventChip event={e} />
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
                  <b>{e.revenueEstimated?.toLocaleString() ?? '—'}</b>
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
          ))}
        </div>
      </div>
    </div>
  )
}
