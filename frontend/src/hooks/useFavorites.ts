import { useCallback, useRef, useState } from 'react'

const STORAGE_KEY = 'earnings-calendar:favorites'

function load(): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return new Set()
    const arr = JSON.parse(raw) as string[]
    return new Set(Array.isArray(arr) ? arr : [])
  } catch {
    return new Set()
  }
}

function save(set: Set<string>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...set]))
  } catch {
    // 存储不可用时静默忽略
  }
}

/** 收藏公司(股票代码),localStorage 持久化。 */
export function useFavorites() {
  const [favorites, setFavorites] = useState<Set<string>>(load)
  const lastToggleRef = useRef(0)

  const toggleFavorite = useCallback((symbol: string) => {
    // 双击/连点防抖:400ms 内的重复切换忽略
    const now = Date.now()
    if (now - lastToggleRef.current < 400) return
    lastToggleRef.current = now
    setFavorites((prev) => {
      const next = new Set(prev)
      if (next.has(symbol)) next.delete(symbol)
      else next.add(symbol)
      save(next)
      return next
    })
  }, [])

  const isFavorite = useCallback((symbol: string) => favorites.has(symbol), [favorites])

  return { favorites, toggleFavorite, isFavorite }
}
