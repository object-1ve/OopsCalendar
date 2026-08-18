import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchFavorites, saveFavorites } from '../api'

const STORAGE_KEY = 'earnings-calendar:favorites'
/** 本地是否已初始化(用户在本浏览器操作过收藏)。localStorage 被清空时该标记消失。 */
const INIT_KEY = 'earnings-calendar:favorites-initialized'
const DEBOUNCE_MS = 400
const SAVE_RETRIES = 3
const RETRY_DELAY_MS = 600

function loadLocal(): Set<string> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return new Set()
    const arr = JSON.parse(raw) as string[]
    return new Set(Array.isArray(arr) ? arr : [])
  } catch {
    return new Set()
  }
}

function saveLocal(set: Set<string>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...set]))
  } catch {
    // 存储不可用时静默忽略
  }
}

function isInitialized(): boolean {
  try {
    return localStorage.getItem(INIT_KEY) === '1'
  } catch {
    return false
  }
}

function markInitialized() {
  try {
    localStorage.setItem(INIT_KEY, '1')
  } catch {
    // 忽略
  }
}

/**
 * 收藏公司(股票代码):localStorage 即时生效 + 服务端持久化(全项目共享,data/favorites.json)。
 *
 * 同步策略(防止收藏"悄悄丢失"):
 * - 本地一旦初始化即为权威:哪怕本地为空(用户删光了收藏)也不会被服务端旧值覆盖;
 *   加载时会用本地集合覆盖推送服务端,自动补齐此前静默失败的保存(如后端短暂不可用)。
 * - 本地未初始化(例如刚清过站点数据)且服务端有存档时,用服务端存档恢复。
 * - 服务端不可用时保留本地缓存,切换收藏时仍会重试推送。
 */
export function useFavorites() {
  const [favorites, setFavorites] = useState<Set<string>>(loadLocal)
  const lastToggleRef = useRef<Record<string, number>>({})
  const pushSeqRef = useRef(0)

  /** 带重试与乱序保护的收藏推送:短时抖动自动重试;期间出现更新的推送则放弃本次旧快照。 */
  const pushFavorites = useCallback((symbols: string[]) => {
    const seq = ++pushSeqRef.current
    const attempt = async (remaining: number) => {
      if (seq !== pushSeqRef.current) return // 已有更新的推送,本次快照作废
      try {
        await saveFavorites(symbols)
      } catch {
        if (remaining > 0) {
          await new Promise((r) => setTimeout(r, RETRY_DELAY_MS))
          return attempt(remaining - 1)
        }
        // 最后一次仍失败:保留本地,下次加载会再次尝试补齐
      }
    }
    void attempt(SAVE_RETRIES - 1)
  }, [])

  useEffect(() => {
    let cancelled = false
    fetchFavorites()
      .then((resp) => {
        if (cancelled) return
        if (resp.configured) {
          if (isInitialized()) {
            // 本地已初始化:以本地为准(含删除操作),并向服务端补齐,避免旧值覆盖/丢失
            const local = loadLocal()
            setFavorites(local)
            saveLocal(local)
            pushFavorites([...local])
          } else {
            // 本地刚被清空过(未初始化)→ 用服务端存档恢复
            const serverSet = new Set(resp.symbols)
            setFavorites(serverSet)
            saveLocal(serverSet)
            markInitialized()
          }
        } else {
          const local = loadLocal()
          if (local.size > 0) {
            // 服务端还没有存档 → 首次持久化本地收藏
            markInitialized()
            pushFavorites([...local])
          }
        }
      })
      .catch(() => {
        // 服务端不可用:保留本地缓存,切换收藏时仍会尝试推送
      })
    return () => {
      cancelled = true
    }
  }, [pushFavorites])

  const toggleFavorite = useCallback(
    (symbol: string) => {
      // 按代码防抖:同一代码 400ms 内的重复切换忽略,不同代码互不影响
      const now = Date.now()
      if (now - (lastToggleRef.current[symbol] ?? 0) < DEBOUNCE_MS) return
      lastToggleRef.current = { ...lastToggleRef.current, [symbol]: now }
      const next = new Set(favorites)
      if (next.has(symbol)) next.delete(symbol)
      else next.add(symbol)
      setFavorites(next)
      saveLocal(next)
      markInitialized()
      pushFavorites([...next])
    },
    [favorites, pushFavorites],
  )

  const isFavorite = useCallback((symbol: string) => favorites.has(symbol), [favorites])

  return { favorites, toggleFavorite, isFavorite }
}
