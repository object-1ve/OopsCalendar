import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchEarnings, fetchHealth } from '../api'
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

  const monthKey = `${year}-${String(month + 1).padStart(2, '0')}`

  const load = useCallback(
    async (y: number, m: number) => {
      const key = `${y}-${String(m + 1).padStart(2, '0')}`
      setError(null)
      if (cache.has(key)) return
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller
      setLoading(true)
      try {
        const data = await fetchEarnings(y, m, controller.signal)
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
    void load(year, month)
  }, [year, month, load])

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
    setCache(new Map())
    setError(null)
    void load(year, month)
    // 数据源可能已切换(如后端降级/恢复),刷新时同步更新徽章
    fetchHealth().then(setHealth).catch(() => setHealth(null))
  }, [year, month, load])

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
  }
}
