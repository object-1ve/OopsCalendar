import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchEarnings, fetchEarningsDay, fetchHealth } from '../api'
import type { EarningsResponse, HealthResponse } from '../types'

/** 按 "YYYY-MM" 缓存月份数据。 */
export function useEarnings() {
  const [year, setYear] = useState(() => new Date().getFullYear())
  const [month, setMonth] = useState(() => new Date().getMonth())
  const [cache, setCache] = useState<Map<string, EarningsResponse>>(new Map())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const lastNavRef = useRef(0)
  const forceLoadRef = useRef(false)
  const [refreshTick, setRefreshTick] = useState(0)

  const monthKey = `${year}-${String(month + 1).padStart(2, '0')}`

  const load = useCallback(
    async (y: number, m: number, force = false) => {
      const key = `${y}-${String(m + 1).padStart(2, '0')}`
      setError(null)
      if (!force && cache.has(key)) return
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller
      setLoading(true)
      try {
        const data = force ? await fetchEarnings(y, m, controller.signal, true) : await fetchEarnings(y, m, controller.signal)
        setCache((prev) => new Map(prev).set(key, data))
      } catch (e) {
        if ((e as Error).name !== 'AbortError') {
          setError((e as Error).message || '加载失败')
        }
      } finally {
        setLoading(false)
      }
    },
    [cache],
  )

  useEffect(() => {
    // 全局刷新置位 force:由 refreshTick 触发本 effect 强制重拉当前月(refresh=true 绕过后端缓存)
    const force = forceLoadRef.current
    forceLoadRef.current = false
    void load(year, month, force)
  }, [year, month, load, refreshTick])

  useEffect(() => {
    const controller = new AbortController()
    fetchHealth(controller.signal).then(setHealth).catch(() => setHealth(null))
    return () => controller.abort()
  }, [])

  const goPrevMonth = useCallback(() => {
    const now = Date.now()
    if (now - lastNavRef.current < 300) return // 双击/连点防抖
    lastNavRef.current = now
    setMonth((m) => (m === 0 ? (setYear((y) => y - 1), 11) : m - 1))
  }, [])

  const goNextMonth = useCallback(() => {
    const now = Date.now()
    if (now - lastNavRef.current < 300) return // 双击/连点防抖
    lastNavRef.current = now
    setMonth((m) => (m === 11 ? (setYear((y) => y + 1), 0) : m + 1))
  }, [])

  const goToday = useCallback(() => {
    setYear(new Date().getFullYear())
    setMonth(new Date().getMonth())
  }, [])

  const refresh = useCallback(() => {
    // 置位 force 并触发重拉;不清空缓存,旧数据继续显示,新数据回来后再原地替换(日历不闪空白)
    forceLoadRef.current = true
    setRefreshTick((t) => t + 1)
    setError(null)
    // 数据源可能已切换(如后端降级/恢复),刷新时同步更新徽章
    fetchHealth().then(setHealth).catch(() => setHealth(null))
  }, [])

  /**
   * 单独刷新某一天(refresh=true 绕过后端缓存):拉当天最新财报,
   * 用返回结果替换当前月中该日期的全部事件(当天返回即为权威全集)。
   * 返回是否成功,供弹窗展示加载/错误状态。
   */
  const refreshDay = useCallback(
    async (date: string): Promise<boolean> => {
      try {
        const fresh = await fetchEarningsDay(date, true)
        setCache((prev) => {
          const cur = prev.get(monthKey)
          if (!cur) return prev
          const others = cur.events.filter((e) => e.date !== date)
          const all = [...others, ...fresh.events].sort(
            (a, b) => a.date.localeCompare(b.date) || a.symbol.localeCompare(b.symbol),
          )
          const next = new Map(prev)
          next.set(monthKey, { ...cur, events: all, count: all.length, source: fresh.source })
          return next
        })
        return true
      } catch (e) {
        if ((e as Error).name !== 'AbortError') {
          setError((e as Error).message || '刷新失败')
        }
        return false
      }
    },
    [monthKey],
  )

  return {
    year,
    month,
    monthKey,
    data: cache.get(monthKey) ?? null,
    loading,
    error,
    health,
    goPrevMonth,
    goNextMonth,
    goToday,
    refresh,
    refreshDay,
  }
}
