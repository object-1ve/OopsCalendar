import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchNewsFavorites, saveNewsFavorites } from '../api'
import type { NewsItem } from '../types'

const STORAGE_KEY = 'opsCalendar.newsFavorites'

/** 本地缓存:完整快讯快照数组(最近收藏的在前),服务端不可用时可离线展示。 */
function loadLocal(): NewsItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw) as NewsItem[]
    return Array.isArray(arr) ? arr.filter((x) => x && typeof x.id === 'string') : []
  } catch {
    return []
  }
}

function saveLocal(items: NewsItem[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  } catch {
    // 存储不可用时静默忽略
  }
}

/**
 * 收藏快讯:localStorage 即时生效 + 服务端持久化(全项目共享,SQLite 数据库)。
 * 服务端有存档时以服务端为准(取消收藏也能跨会话同步);
 * 服务端无存档但本地有,首次打开自动推送落库。
 * items 为完整快讯快照(最近收藏的在前),即使快讯已从实时流淘汰仍可展示。
 */
export function useNewsFavorites() {
  const [favorites, setFavorites] = useState<NewsItem[]>(loadLocal)
  const lastToggleRef = useRef(0)

  useEffect(() => {
    let cancelled = false
    const local = loadLocal()
    fetchNewsFavorites()
      .then((resp) => {
        if (cancelled) return
        if (resp.configured) {
          setFavorites(resp.items)
          saveLocal(resp.items)
        } else if (local.length > 0) {
          // 服务端还没有存档 → 首次持久化本地收藏
          saveNewsFavorites(local).catch(() => {})
        }
      })
      .catch(() => {
        // 服务端不可用:保留本地缓存,切换收藏时仍会尝试推送
      })
    return () => {
      cancelled = true
    }
  }, [])

  const toggleFavorite = useCallback(
    (item: NewsItem) => {
      // 双击/连点防抖:400ms 内的重复切换忽略
      const now = Date.now()
      if (now - lastToggleRef.current < 400) return
      lastToggleRef.current = now
      const id = item?.id
      if (!id) return
      const exists = favorites.some((f) => f.id === id)
      const next = exists ? favorites.filter((f) => f.id !== id) : [item, ...favorites]
      setFavorites(next)
      saveLocal(next)
      saveNewsFavorites(next).catch(() => {})
    },
    [favorites],
  )

  const isFavorite = useCallback((id: string) => favorites.some((f) => f.id === id), [favorites])

  /** 去重导入后应用服务端合并结果(服务端为权威,本地缓存同步,按 id 去重)。 */
  const applyImport = useCallback((items: NewsItem[]) => {
    const seen = new Set<string>()
    const deduped = items.filter((it) => {
      const id = it?.id
      if (!id || seen.has(id)) return false
      seen.add(id)
      return true
    })
    setFavorites(deduped)
    saveLocal(deduped)
  }, [])

  return { favorites, toggleFavorite, isFavorite, applyImport }
}
