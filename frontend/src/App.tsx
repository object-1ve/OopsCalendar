import { useMemo, useState } from 'react'
import CalendarView from './components/CalendarView'
import DayDetailModal from './components/DayDetailModal'
import Legend from './components/Legend'
import { useEarnings } from './hooks/useEarnings'

const MONTH_NAMES = ['1 月', '2 月', '3 月', '4 月', '5 月', '6 月', '7 月', '8 月', '9 月', '10 月', '11 月', '12 月']

export default function App() {
  const { year, month, data, loading, error, health, goPrevMonth, goNextMonth, goToday, refresh } = useEarnings()
  const [selectedDate, setSelectedDate] = useState<string | null>(null)

  const selectedEvents = useMemo(() => {
    if (!selectedDate || !data) return []
    return data.events.filter((e) => e.date === selectedDate)
  }, [selectedDate, data])

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-title">
          <h1>📅 美股财报日历</h1>
          <p className="subtitle">美国上市公司财报发布日期 · 区分盘前 / 盘后 · 标注已公布 / 未公布</p>
        </div>
        <div className="header-right">
          <span
            className={`source-badge ${health?.provider === 'fmp' || health?.provider === 'finnhub' ? 'real' : 'mock'}`}
            title={health?.message ?? '数据源未知'}
          >
            {health
              ? health.provider === 'fmp'
                ? '● FMP 真实数据'
                : health.provider === 'finnhub'
                  ? '● Finnhub 真实数据'
                  : '● 演示数据'
              : '…'}
          </span>
          <button className="btn" onClick={refresh} disabled={loading} title="重新加载当前月">
            ↻ 刷新
          </button>
        </div>
      </header>

      <Legend />

      <div className="toolbar">
        <button className="btn" onClick={goPrevMonth}>
          ‹ 上月
        </button>
        <button className="btn" onClick={goToday}>
          今天
        </button>
        <button className="btn" onClick={goNextMonth}>
          下月 ›
        </button>
        <span className="toolbar-month">
          {year} 年 {MONTH_NAMES[month]}
          {data && <span className="toolbar-count"> · {data.count} 条财报</span>}
        </span>
        {loading && <span className="toolbar-loading">加载中…</span>}
      </div>

      {error && (
        <div className="error-banner">
          ⚠ {error}
          <button className="btn small" onClick={refresh}>
            重试
          </button>
        </div>
      )}

      <CalendarView
        year={year}
        month={month}
        events={data?.events ?? []}
        loading={loading}
        onSelectDay={setSelectedDate}
      />

      {selectedDate && (
        <DayDetailModal date={selectedDate} events={selectedEvents} onClose={() => setSelectedDate(null)} />
      )}
    </div>
  )
}
