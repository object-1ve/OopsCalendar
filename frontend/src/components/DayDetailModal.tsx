import type { EarningsEvent } from '../types'
import EventChip from './EventChip'

interface Props {
  date: string // YYYY-MM-DD
  events: EarningsEvent[]
  onClose: () => void
}

/** 点击日期后弹出的当日财报详情。 */
export default function DayDetailModal({ date, events, onClose }: Props) {
  const [, m, d] = date.split('-')
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>
            {Number(m)} 月 {Number(d)} 日财报 <span className="modal-count">({events.length} 家)</span>
          </h2>
          <button className="modal-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </div>
        <div className="modal-body">
          {events.length === 0 && <p className="empty">当日无财报。</p>}
          {events.map((e) => (
            <div key={`${e.date}-${e.symbol}`} className="event-row">
              <div className="event-main">
                <span className="event-symbol">{e.symbol}</span>
                <EventChip event={e} />
                <span className={`status-badge ${e.confirmed ? 'done' : 'todo'}`}>
                  {e.confirmed ? '✓ 已公布' : '未公布'}
                </span>
              </div>
              <div className="event-detail">
                <span>{e.name ?? ''}</span>
                <span>
                  EPS 实际 <b>{e.eps ?? '—'}</b> / 预估 <b>{e.epsEstimated ?? '—'}</b>
                </span>
                <span>
                  营收(百万$)实际 <b>{e.revenue?.toLocaleString() ?? '—'}</b> / 预估{' '}
                  <b>{e.revenueEstimated?.toLocaleString() ?? '—'}</b>
                </span>
                <span className="source-tag">数据源:{e.source === 'fmp' ? 'FMP 真实数据' : '演示数据'}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
