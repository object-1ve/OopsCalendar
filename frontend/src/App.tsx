import { useCallback, useEffect, useMemo, useState } from 'react'
import { Moon, Sun } from 'lucide-react'
import CalendarView from './components/CalendarView'
import DayDetailModal from './components/DayDetailModal'
import Legend from './components/Legend'
import NewsView from './components/NewsView'
import { Toaster } from './components/ui/sonner'
import { useEarnings } from './hooks/useEarnings'
import { useFavorites } from './hooks/useFavorites'

const MONTH_NAMES = ['1 月', '2 月', '3 月', '4 月', '5 月', '6 月', '7 月', '8 月', '9 月', '10 月', '11 月', '12 月']

type View = 'calendar' | 'news'

/** 解析当前 pathname 对应的视图:根路径 → 日历,其余非 /news 也归日历(容错)。 */
function pathToView(pathname: string): View {
  if (pathname === '/news') return 'news'
  return 'calendar'
}

/**
 * 轻量 history 路由(无第三方依赖):URL 与视图一一对应。
 * - 根路径 / → 日历; /news → 快讯
 * - 导航用 history.pushState(不刷新页面),用 popstate 同步浏览器前进/后退
 */
function useRoute() {
  const [pathname, setPathname] = useState(() => window.location.pathname)
  useEffect(() => {
    const onPop = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])
  const view: View = pathToView(pathname)
  const navigate = useCallback((next: View) => {
    const path = next === 'news' ? '/news' : '/'
    if (window.location.pathname === path) return
    window.history.pushState(null, '', path)
    setPathname(path)
  }, [])
  return { view, navigate }
}

export default function App() {
  const { year, month, data, loading, error, health, goPrevMonth, goNextMonth, goToday, refresh, refreshDay } =
    useEarnings()
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const { view, navigate } = useRoute()
  const { favorites, toggleFavorite } = useFavorites()
  // 明暗主题:localStorage 持久化,未设置时跟随系统偏好
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    try {
      const saved = localStorage.getItem('opsCalendar.theme')
      if (saved === 'light' || saved === 'dark') return saved
    } catch {
      // 存储不可用时走系统偏好
    }
    return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem('opsCalendar.theme', theme)
    } catch {
      // 存储不可用时静默忽略
    }
  }, [theme])

  const selectedEvents = useMemo(() => {
    if (!selectedDate || !data) return []
    return data.events.filter((e) => e.date === selectedDate)
  }, [selectedDate, data])

  return (
    <div className="app">
      <Toaster theme={theme} />
      <header className="app-header">
        <div className="app-title">
          <h1>📅 美股财报日历</h1>
          <p className="subtitle">美国上市公司财报发布日期 · 区分盘前 / 盘后 · 标注已公布 / 未公布</p>
        </div>
        <div className="header-right">
          <button
            className="btn theme-toggle"
            onClick={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
            title={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
            aria-label="切换明暗主题"
          >
            {theme === 'dark' ? <Sun size={14} /> : <Moon size={14} />}
          </button>
          <div className="view-tabs">
            <button
              className={`view-tab ${view === 'calendar' ? 'active' : ''}`}
              onClick={() => navigate('calendar')}
              title="美股财报日历"
              aria-current={view === 'calendar' ? 'page' : undefined}
            >
              📅 日历
            </button>
            <button
              className={`view-tab ${view === 'news' ? 'active' : ''}`}
              onClick={() => navigate('news')}
              title="金十 / 财联社 / 华尔街见闻 / 东方财富 / 雪球 / 格隆汇 财经快讯"
              aria-current={view === 'news' ? 'page' : undefined}
            >
              ⚡ 快讯
            </button>
          </div>
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
          {view === 'calendar' && (
            <button className="btn" onClick={refresh} disabled={loading} title="重新加载当前月">
              ↻ 刷新
            </button>
          )}
        </div>
      </header>

      {view === 'news' ? (
        <NewsView />
      ) : (
        <>
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
            favorites={favorites}
            onSelectDay={setSelectedDate}
          />

          {selectedDate && (
            <DayDetailModal
              date={selectedDate}
              events={selectedEvents}
              favorites={favorites}
              onToggleFavorite={toggleFavorite}
              onRefreshDay={refreshDay}
              onClose={() => setSelectedDate(null)}
            />
          )}
        </>
      )}
    </div>
  )
}
