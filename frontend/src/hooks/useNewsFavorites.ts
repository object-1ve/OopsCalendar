import { useCallback, useEffect, useRef, useState } from 'react'
import {
  deleteNewsFavoriteGroup,
  fetchNewsFavoriteGroups,
  fetchNewsFavorites,
  renameNewsFavoriteGroup,
  saveNewsFavoriteGroups,
  saveNewsFavorites,
} from '../api'
import type { NewsItem } from '../types'

const STORAGE_KEY = 'opsCalendar.newsFavorites'
const GROUPS_KEY = 'opsCalendar.newsFavoriteGroups'

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

/** 本地缓存:组别名称列表(服务端不可用时可离线展示)。 */
function loadGroupsLocal(): string[] {
  try {
    const raw = localStorage.getItem(GROUPS_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw) as string[]
    return Array.isArray(arr) ? arr.filter((x): x is string => typeof x === 'string' && x.trim() !== '') : []
  } catch {
    return []
  }
}

function saveGroupsLocal(groups: string[]) {
  try {
    localStorage.setItem(GROUPS_KEY, JSON.stringify(groups))
  } catch {
    // 存储不可用时静默忽略
  }
}

/** 去重并过滤空白后的组别列表。 */
function normalizeGroups(groups: string[]): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const g of groups) {
    const name = (g ?? '').trim()
    if (!name || seen.has(name)) continue
    seen.add(name)
    out.push(name)
  }
  return out
}

/**
 * 收藏快讯:localStorage 即时生效 + 服务端持久化(全项目共享,SQLite 数据库)。
 * 服务端有存档时以服务端为准(取消收藏也能跨会话同步);
 * 服务端无存档但本地有,首次打开自动推送落库。
 * items 为完整快讯快照(最近收藏的在前),即使快讯已从实时流淘汰仍可展示。
 * 支持二级分类:用户自建组别,收藏项可分配/移出组别。
 */
export function useNewsFavorites() {
  const [favorites, setFavorites] = useState<NewsItem[]>(loadLocal)
  const [groups, setGroups] = useState<string[]>(loadGroupsLocal)
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
    const localGroups = loadGroupsLocal()
    fetchNewsFavoriteGroups()
      .then((resp) => {
        if (cancelled) return
        if (resp.configured) {
          const list = normalizeGroups(resp.groups)
          setGroups(list)
          saveGroupsLocal(list)
        } else if (localGroups.length > 0) {
          // 服务端还没有存档 → 首次持久化本地组别
          const list = normalizeGroups(localGroups)
          setGroups(list)
          saveGroupsLocal(list)
          saveNewsFavoriteGroups(list).catch(() => {})
        }
      })
      .catch(() => {
        // 服务端不可用:保留本地缓存
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

  /** 把收藏项移入/移出组别(groupName 为空 = 未分组);本地即时生效 + 服务端整表替换。 */
  const assignGroup = useCallback(
    (itemId: string, groupName: string) => {
      const name = (groupName ?? '').trim()
      const next = favorites.map((f) =>
        f.id === itemId ? { ...f, groupName: name || undefined } : f,
      )
      setFavorites(next)
      saveLocal(next)
      saveNewsFavorites(next).catch(() => {})
    },
    [favorites],
  )

  /** 新建组别(已存在则忽略)。 */
  const addGroup = useCallback(
    (name: string) => {
      const clean = (name ?? '').trim()
      if (!clean) return false
      if (groups.includes(clean)) return false
      const next = [...groups, clean]
      setGroups(next)
      saveGroupsLocal(next)
      saveNewsFavoriteGroups(next).catch(() => {})
      return true
    },
    [groups],
  )

  /** 重命名组别:成功后同步本地组别与收藏归属;失败(后端拒绝)时抛出异常。 */
  const renameGroup = useCallback(
    async (oldName: string, newName: string) => {
      const cleanNew = (newName ?? '').trim()
      if (!cleanNew) throw new Error('组别名称不能为空')
      if (oldName === cleanNew) return
      await renameNewsFavoriteGroup(oldName, cleanNew)
      setGroups((prev) => prev.map((g) => (g === oldName ? cleanNew : g)))
      saveGroupsLocal(groups.map((g) => (g === oldName ? cleanNew : g)))
      setFavorites((prev) =>
        prev.map((f) => (f.groupName === oldName ? { ...f, groupName: cleanNew } : f)),
      )
    },
    [groups],
  )

  /** 删除组别:该组下的收藏移回未分组,收藏本身保留。 */
  const deleteGroup = useCallback(
    async (name: string) => {
      await deleteNewsFavoriteGroup(name)
      setGroups((prev) => prev.filter((g) => g !== name))
      saveGroupsLocal(groups.filter((g) => g !== name))
      setFavorites((prev) =>
        prev.map((f) => (f.groupName === name ? { ...f, groupName: undefined } : f)),
      )
      saveLocal(favorites.map((f) => (f.groupName === name ? { ...f, groupName: undefined } : f)))
    },
    [groups, favorites],
  )

  return { favorites, groups, toggleFavorite, isFavorite, applyImport, assignGroup, addGroup, renameGroup, deleteGroup }
}
