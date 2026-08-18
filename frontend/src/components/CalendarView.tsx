import { useMemo } from 'react'
import type { EarningsEvent } from '../types'
import { sortByRevenue } from '../utils'
import EventChip from './EventChip'

interface Props {
  year: number
  month: number // 0-based
  events: EarningsEvent[]
  loading: boolean
  favorites: Set<string>
  onSelectDay: (date: string) => void
}

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const MAX_CHIPS = 3

function fmt(y: number, m: number, d: number): string {
  return `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

export default function CalendarView({ year, month, events, loading, favorites, onSelectDay }: Props) {
  // dateStr -> events(营收降序,收藏的置顶,格子显示靠前的几家)
  const byDate = useMemo(() => {
    const map = new Map<string, EarningsEvent[]>()
    for (const e of events) {
      const list = map.get(e.date)
      if (list) list.push(e)
      else map.set(e.date, [e])
    }
    for (const list of map.values()) {
      sortByRevenue(list)
      // 收藏的公司排前面(同组内保持营收顺序,Array.sort 稳定)
      list.sort((a, b) => Number(favorites.has(b.symbol)) - Number(favorites.has(a.symbol)))
    }
    return map
  }, [events, favorites])

  // 生成 6x7 网格
  const cells = useMemo(() => {
    const first = new Date(year, month, 1)
    const startOffset = first.getDay() // 周日开头
    const gridStart = new Date(year, month, 1 - startOffset)
    const today = new Date()
    const todayStr = fmt(today.getFullYear(), today.getMonth(), today.getDate())
    const cells: { date: string; day: number; inMonth: boolean; isToday: boolean }[] = []
    for (let i = 0; i < 42; i++) {
      const d = new Date(gridStart)
      d.setDate(gridStart.getDate() + i)
      const dateStr = fmt(d.getFullYear(), d.getMonth(), d.getDate())
      cells.push({
        date: dateStr,
        day: d.getDate(),
        inMonth: d.getMonth() === month,
        isToday: dateStr === todayStr,
      })
    }
    return cells
  }, [year, month])

  return (
    <div className="calendar">
      <div className="calendar-weekdays">
        {WEEKDAYS.map((w) => (
          <div key={w} className="weekday">
            周{w}
          </div>
        ))}
      </div>
      <div className="calendar-grid">
        {cells.map((cell) => {
          const dayEvents = byDate.get(cell.date) ?? []
          const visible = dayEvents.slice(0, MAX_CHIPS)
          const rest = dayEvents.length - visible.length
          return (
            <div
              key={cell.date}
              className={`day-cell ${cell.inMonth ? '' : 'out-month'} ${cell.isToday ? 'today' : ''} ${
                dayEvents.length > 0 ? 'has-events' : ''
              }`}
              onClick={() => onSelectDay(cell.date)}
              title={dayEvents.length > 0 ? `点击查看 ${dayEvents.length} 条财报` : undefined}
            >
              <div className="day-number">{cell.day}</div>
              <div className="day-chips">
                {visible.map((e) => (
                  <EventChip key={`${cell.date}-${e.symbol}`} event={e} compact favorite={favorites.has(e.symbol)} />
                ))}
                {rest > 0 && <span className="chip more">+{rest}</span>}
              </div>
              {cell.isToday && <div className="today-dot" />}
            </div>
          )
        })}
      </div>
      {loading && <div className="calendar-loading">加载中…</div>}
    </div>
  )
}
