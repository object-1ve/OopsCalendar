import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { NewsFavoritesExport, NewsItem, NewsSourceMeta } from '../types'
import {
  exportNews,
  exportNewsFavorites,
  fetchNews,
  fetchNewsCount,
  fetchNewsHistory,
  fetchNewsPreferences,
  fetchNewsSources,
  importNews,
  importNewsFavorites,
  saveNewsPreferences,
} from '../api'
import { useNewsFavorites } from '../hooks/useNewsFavorites'
import { formatNewsTime } from '../utils'

/** 快讯视图:SSE 实时推送 + 数据源配置 + 客户端即时筛选。 */
const STORAGE_KEY = 'opsCalendar.newsSources'
const MAX_ITEMS = 500
const PAGE_SIZE = 40

/** 记录用户已经见过哪些数据源(用于识别“真正新增”的源,避免把用户关闭过的源重新开启)。 */
const KNOWN_SOURCES_KEY = 'opsCalendar.knownSources'

/** 弱缓存读取:数据源配置以服务端数据库为权威,本地仅在后端不可达时作为回退缓存。 */
function readCachedSources(): string[] | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return null
    return parsed.filter((x): x is string => typeof x === 'string')
  } catch {
    return null
  }
}

/** 写本地弱缓存(不参与权威判定,仅离线兜底)。 */
function cacheSources(list: string[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // 存储不可用时静默忽略
  }
}

/** 读取已知数据源快照:首次(无快照)视为全部已知,避免把已关闭的源当作新源重新开启。 */
function readKnownSources(all: NewsSourceMeta[]): Set<string> {
  try {
    const raw = localStorage.getItem(KNOWN_SOURCES_KEY)
    if (raw) {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr)) return new Set(arr.filter((x): x is string => typeof x === 'string'))
    }
  } catch {
    // 忽略
  }
  return new Set(all.map((s) => s.key))
}

/** 记录当前全部数据源为已知(本次之后不再视为新源)。 */
function writeKnownSources(all: NewsSourceMeta[]) {
  try {
    localStorage.setItem(KNOWN_SOURCES_KEY, JSON.stringify(all.map((s) => s.key)))
  } catch {
    // 存储不可用时静默忽略
  }
}

/** 已保存列表基础上,只自动开启真正新出现的数据源(不在 known 快照中);用户关闭过的源不会复活。 */
function withNewSources(enabled: string[], all: NewsSourceMeta[], known: Set<string>): string[] {
  if (enabled.length === 0) return [] // 全部禁用语义保持不动
  const set = new Set(enabled)
  for (const s of all) {
    if (!known.has(s.key)) set.add(s.key)
  }
  return [...set]
}

export default function NewsView() {
  const [sources, setSources] = useState<NewsSourceMeta[]>([])
  const [sourcesReady, setSourcesReady] = useState(false)
  const [master, setMaster] = useState<NewsItem[]>([])
  const [active, setActive] = useState('all')
  const [enabled, setEnabled] = useState<Set<string> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fetchedAt, setFetchedAt] = useState<number | null>(null)
  const [showSettings, setShowSettings] = useState(false)
  const [tick, setTick] = useState(0)
  const [query, setQuery] = useState('')
  const [visible, setVisible] = useState(PAGE_SIZE)
  const [moreOffset, setMoreOffset] = useState<number | null>(null)
  const [moreDone, setMoreDone] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [dbCount, setDbCount] = useState<number | null>(null)
  const loadingMoreRef = useRef(false)
  const listRef = useRef<HTMLDivElement | null>(null)
  const pendingRef = useRef<number | null>(null) // 防抖定时器
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const { favorites, toggleFavorite, isFavorite, applyImport } = useNewsFavorites()
  const [importing, setImporting] = useState(false)
  const [favNote, setFavNote] = useState<{ text: string; error: boolean } | null>(null)

  const mergeItems = (incoming: NewsItem[]) => {
    if (!incoming.length) return
    setMaster((prev) => {
      const map = new Map(prev.map((it) => [it.id, it]))
      for (const it of incoming) map.set(it.id, it)
      const arr = [...map.values()].sort((a, b) => (b.pubDate ?? 0) - (a.pubDate ?? 0))
      return arr.slice(0, MAX_ITEMS)
    })
  }

  // 1) 数据源列表(供标签与配置面板)
  useEffect(() => {
    let cancelled = false
    fetchNewsSources()
      .then((list) => {
        if (cancelled) return
        setSources(list)
        setSourcesReady(true)
      })
      .catch(() => {
        if (!cancelled) setSourcesReady(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 2) 初始化启用配置:以后端数据库为准(权威);localStorage 仅作后端不可达时的回退缓存
  useEffect(() => {
    if (!sourcesReady || enabled !== null) return
    let cancelled = false
    const cached = readCachedSources()
    fetchNewsPreferences()
      .then((pref) => {
        if (cancelled) return
        const known = readKnownSources(sources)
        if (pref.configured) {
          const list = withNewSources(pref.sources, sources, known)
          setEnabled(new Set(list))
          cacheSources(list)
          writeKnownSources(sources)
          // 有真正新增的数据源被默认开启时写回服务端,真正落库(否则只是内存合并)
          if (list.length !== pref.sources.length) {
            saveNewsPreferences(list).catch(() => {})
          }
        } else if (cached && cached.length > 0) {
          // 数据库还没有该配置,但本地有历史缓存:首次推送落库(一次性迁移,之后以数据库为准)
          const list = withNewSources(cached, sources, known)
          setEnabled(new Set(list))
          cacheSources(list)
          writeKnownSources(sources)
          saveNewsPreferences(list).catch(() => {})
        } else {
          setEnabled(new Set(sources.map((s) => s.key)))
          writeKnownSources(sources)
        }
      })
      .catch(() => {
        if (cancelled) return
        // 后端不可达:先用本地缓存,没有则默认全开
        if (cached) {
          const known = readKnownSources(sources)
          setEnabled(new Set(withNewSources(cached, sources, known)))
        } else {
          setEnabled(new Set(sources.map((s) => s.key)))
        }
      })
    return () => {
      cancelled = true
    }
  }, [sourcesReady, enabled, sources])

  const enabledKeys = useMemo(
    () => sources.filter((s) => enabled?.has(s.key)).map((s) => s.key),
    [sources, enabled],
  )
  const visibleTabs = useMemo(() => sources.filter((s) => enabledKeys.includes(s.key)), [sources, enabledKeys])
  const noneEnabled = sourcesReady && sources.length > 0 && enabledKeys.length === 0

  // 3) 初始/手动刷新:按启用的源拉全量(替换 master);之后新条目靠 SSE 增量
  useEffect(() => {
    if (enabled === null) return
    if (noneEnabled) {
      setMaster([])
      setLoading(false)
      return
    }
    let cancelled = false
    const load = async () => {
      setLoading(true)
      setError(null)
      try {
        const param =
          enabledKeys.length === sources.length || enabledKeys.length === 0 ? undefined : enabledKeys.join(',')
        const resp = await fetchNews(param)
        if (cancelled) return
        setMaster(resp.items)
        setFetchedAt(resp.fetchedAt)
      } catch {
        if (!cancelled) setError('快讯加载失败,请稍后重试')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [enabled, enabledKeys, sources.length, noneEnabled, tick])

  // 4) SSE 实时订阅:后端每 15s 增量轮询推送新增;每轮结束都发心跳,据此刷新"更新于"
  useEffect(() => {
    const es = new EventSource('/api/news/stream')
    // 命名 news 帧:增量合并新快讯并刷新"更新于"
    const onNews = (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data) as NewsItem[]
        if (Array.isArray(data)) {
          mergeItems(data)
          setFetchedAt(Date.now())
        }
      } catch {
        // 忽略异常帧
      }
    }
    es.onmessage = onNews // 兜底:无 event: 字段的帧
    es.addEventListener('news', onNews)
    // 心跳:每轮轮询推送服务端时间(即便无新增),让"更新于"按 15s 周期刷新
    es.addEventListener('heartbeat', (ev) => {
      const t = Number(ev.data)
      if (Number.isFinite(t) && t > 0) setFetchedAt(t)
    })
    return () => es.close()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 数据库已入库快讯总条数(全部数据源,不受筛选影响)
  useEffect(() => {
    let cancelled = false
    const loadCount = () =>
      fetchNewsCount()
        .then((resp) => {
          if (!cancelled) setDbCount(resp.count)
        })
        .catch(() => {
          // 后端不可达时静默,不打断快讯主流程
        })
    loadCount()
    // 每次手动刷新后也同步一次
    const iv = window.setInterval(loadCount, 60000)
    return () => {
      cancelled = true
      window.clearInterval(iv)
    }
  }, [tick])

  // 渲染前按当前筛选(全部 = 启用的源;单独源 = 只看该源)
  const displayed = useMemo(() => {
    return master.filter((it) => {
      if (active !== 'all' && it.source !== active) return false
      if (enabled && !enabled.has(it.source)) return false
      return true
    })
  }, [master, active, enabled])

  const matchesQuery = (it: NewsItem) => {
    const q = query.trim().toLowerCase()
    if (!q) return true
    return (
      (it.title ?? '').toLowerCase().includes(q) ||
      (it.summary ?? '').toLowerCase().includes(q) ||
      (it.sourceName ?? '').toLowerCase().includes(q)
    )
  }

  /** 当前页签 + 搜索词过滤后的完整列表(供无限滚动分页)。 */
  const filtered = useMemo(() => {
    const base = active === 'fav' ? favorites : displayed
    return base.filter(matchesQuery)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, favorites, displayed, query])

  const shown = useMemo(() => filtered.slice(0, visible), [filtered, visible])

  /** 无限滚动到底后,向后端分页拉取已入库历史快讯(按当前页签的数据源范围)。 */
  const loadMoreHistory = useCallback(async () => {
    if (loadingMoreRef.current || moreDone) return
    const scopeSources =
      active === 'fav'
        ? undefined
        : active === 'all'
          ? enabledKeys.length > 0
            ? enabledKeys.join(',')
            : undefined
          : active
    if (!scopeSources) return
    loadingMoreRef.current = true
    setLoadingMore(true)
    try {
      const offset = moreOffset ?? 0
      const q = query.trim()
      const resp = await fetchNewsHistory(offset, PAGE_SIZE, scopeSources, q || undefined)
      if (resp.items.length > 0) mergeItems(resp.items)
      if (resp.items.length === 0 || resp.items.length < PAGE_SIZE || offset + resp.items.length >= resp.total) {
        setMoreDone(true)
      } else {
        setMoreOffset(offset + resp.items.length)
      }
    } catch {
      // 历史加载失败:保留现场,下次滚动会重试
    } finally {
      loadingMoreRef.current = false
      setLoadingMore(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, enabledKeys, moreDone, moreOffset, query])

  // 切换页签 / 数据源 / 搜索时重置分页与历史游标(回到第一屏)
  useEffect(() => {
    setVisible(PAGE_SIZE)
    setMoreOffset(null)
    setMoreDone(false)
    if (query) listRef.current?.scrollIntoView({ block: 'start' })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, enabledKeys, query])

  // 无限滚动:监听列表滚动,接近底部时先翻本地分页,翻完再向后端拉历史。
  // 用 scroll 事件而非 IntersectionObserver,避免哨兵 enter/leave 时序导致翻页断链。
  const maybeLoadMore = useCallback(() => {
    const el = listRef.current
    if (!el) return
    const rect = el.getBoundingClientRect()
    // 距视口底部不足 400px 即视为“接近底部”
    if (rect.bottom - window.innerHeight < 400) {
      if (shown.length < filtered.length) {
        setVisible((v) => v + PAGE_SIZE)
      } else {
        void loadMoreHistory()
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shown.length, filtered.length, loadMoreHistory])

  useEffect(() => {
    const onScroll = () => {
      if (pendingRef.current) window.clearTimeout(pendingRef.current)
      pendingRef.current = window.setTimeout(maybeLoadMore, 120)
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })
    maybeLoadMore() // 首次挂载或列表变化时立即检测一次
    return () => {
      if (pendingRef.current) window.clearTimeout(pendingRef.current)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [maybeLoadMore])

  const toggleSource = (key: string) => {
    const base = enabled ?? new Set(sources.map((s) => s.key))
    const next = new Set(base)
    if (next.has(key)) next.delete(key)
    else next.add(key)
    const list = [...next]
    cacheSources(list)
    // 每次勾选都写入服务端数据库(权威存储,全项目共享一份),换浏览器 / 清缓存 / 重启均可恢复
    saveNewsPreferences(list).catch(() => {})
    setEnabled(next)
    if (active === key && !next.has(key)) setActive('all')
  }

  /** 当前页签的导出范围:收藏 = 收藏快照;其余 = 已入库快讯(按当前数据源与搜索词)。 */
  const exportScope = (): { kind: 'fav' } | { kind: 'news'; sources?: string; search?: string } => {
    if (active === 'fav') return { kind: 'fav' }
    if (active === 'all') {
      return {
        kind: 'news',
        sources: enabledKeys.length > 0 ? enabledKeys.join(',') : undefined,
        search: query.trim() || undefined,
      }
    }
    return { kind: 'news', sources: active, search: query.trim() || undefined }
  }

  /** 当前页签的展示名(用于操作提示)。 */
  const activeTabName = () => {
    if (active === 'fav') return '收藏'
    if (active === 'all') return '全部'
    return sources.find((s) => s.key === active)?.name ?? active
  }

  /** 导出当前页签内容为 JSON 文件(服务端生成)。 */
  const handleExport = async () => {
    setFavNote(null)
    try {
      const scope = exportScope()
      const count =
        scope.kind === 'fav'
          ? await exportNewsFavorites()
          : await exportNews(scope.sources, scope.search)
      const file = scope.kind === 'fav' ? 'news-favorites.json' : 'news.json'
      setFavNote({ text: `已导出 ${count} 条${activeTabName()}快讯到 ${file}`, error: false })
    } catch (e) {
      setFavNote({ text: `导出失败:${e instanceof Error ? e.message : '请稍后重试'}`, error: true })
    }
  }

  /** 读取选择的 JSON 文件并按当前页签去重导入(兼容导出文件封装与裸快讯数组)。 */
  const handleImportFile = async (file: File) => {
    try {
      setImporting(true)
      setFavNote(null)
      const text = await file.text()
      const data = JSON.parse(text) as unknown
      const list = Array.isArray(data) ? data : (data as NewsFavoritesExport | null)?.items
      if (!Array.isArray(list)) {
        setFavNote({ text: '导入失败:文件格式不正确(应为快讯数组或导出的 JSON 文件)', error: true })
        return
      }
      const isFav = active === 'fav'
      const resp = isFav ? await importNewsFavorites(list) : await importNews(list)
      if (isFav) {
        applyImport(resp.items)
      } else {
        // 导入进快讯库:合并进当前列表(按来源/搜索过滤后自然呈现)并刷新库总数
        mergeItems(resp.items)
        setDbCount(resp.total)
      }
      const parts = [`导入完成:新增 ${resp.imported} 条`]
      if (resp.skipped > 0) parts.push(`跳过 ${resp.skipped} 条(重复或格式不正确)`)
      parts.push(isFav ? `当前共 ${resp.total} 条收藏` : `当前库中共 ${resp.total} 条`)
      setFavNote({ text: parts.join(';'), error: false })
    } catch (e) {
      setFavNote({ text: `导入失败:${e instanceof Error ? e.message : '文件解析失败'}`, error: true })
    } finally {
      setImporting(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const iconOf = (key: string) => sources.find((s) => s.key === key)?.icon ?? null

  /** 渲染一条快讯 + 右侧收藏按钮;收藏 tab 与实时列表共用,保证样式一致。 */
  const renderItem = (it: NewsItem, fav: boolean) => (
    <article className={`news-item${fav ? ' news-item-favorite' : ''}`} key={it.id}>
      <a className="news-link" href={it.url} target="_blank" rel="noreferrer">
        <span className={`news-icon news-icon-${it.source}`}>
          {iconOf(it.source) ? (
            <img className="news-icon-img" src={`/icons/${iconOf(it.source)}`} alt="" />
          ) : (
            <span className="news-icon-dot" />
          )}
        </span>
        <div className="news-body">
          <div className="news-title">
            {it.important && <span className="news-important">★ </span>}
            {it.title}
          </div>
          {it.summary && <div className="news-summary">{it.summary}</div>}
          <div className="news-meta">
            <span className={`news-source news-source-${it.source}`}>{it.sourceName}</span>
            {it.pubDate != null && <span> · {formatNewsTime(it.pubDate)}</span>}
          </div>
        </div>
      </a>
      <button
        className={`news-fav-btn${fav ? ' active' : ''}`}
        onClick={() => toggleFavorite(it)}
        title={fav ? '取消收藏' : '收藏这条快讯'}
        aria-label={fav ? '取消收藏' : '收藏'}
      >
        {fav ? '★' : '☆'}
      </button>
    </article>
  )

  return (
    <section className="news-view">
      <div className="news-toolbar">
        <div className="news-tabs">
          <button className={`news-tab ${active === 'all' ? 'active' : ''}`} onClick={() => setActive('all')}>
            全部{enabledKeys.length < sources.length ? ` (${enabledKeys.length})` : ''}
          </button>
          <button
            className={`news-tab ${active === 'fav' ? 'active' : ''}`}
            onClick={() => setActive('fav')}
            title="查看收藏的快讯(点击快讯右侧的 ☆ 收藏)"
          >
            ★ 收藏{favorites.length > 0 ? ` (${favorites.length})` : ''}
          </button>
          {visibleTabs.map((s) => (
            <button
              key={s.key}
              className={`news-tab ${active === s.key ? 'active' : ''}`}
              onClick={() => setActive(s.key)}
            >
              {s.icon && <img className="news-tab-icon" src={`/icons/${s.icon}`} alt="" />}
              {s.name}
            </button>
          ))}
        </div>
        <div className="news-toolbar-right">
          <input
            className="news-search"
            type="search"
            placeholder="搜索标题/摘要/来源…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="搜索快讯"
          />
          {query.trim() && <span className="news-result-count">匹配 {filtered.length} 条</span>}
          {dbCount != null && (
            <span className="news-db-count" title="数据库已入库快讯总条数(不受筛选影响)">
              库中 {dbCount} 条
            </span>
          )}
          <span className="news-live" title="已连接实时推送,新快讯约 15 秒内到达">
            <span className="news-live-dot" /> 实时
          </span>
          {fetchedAt != null && <span className="news-updated">更新于 {formatNewsTime(fetchedAt)}</span>}
          {loading && <span className="toolbar-loading">加载中…</span>}
          <button
            className="btn small"
            onClick={handleExport}
            disabled={importing}
            title={`导出当前${active === 'fav' ? '收藏' : '页签'}为 JSON 文件(收藏页签导出收藏,其余导出已入库快讯)`}
          >
            ⇩ 导出
          </button>
          <button
            className="btn small"
            onClick={() => fileInputRef.current?.click()}
            disabled={importing}
            title={`从 JSON 文件导入(自动按 id 去重,不删除现有;收藏页签导入收藏,其余导入已入库快讯)`}
          >
            {importing ? '导入中…' : '⇧ 导入'}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json,.json"
            style={{ display: 'none' }}
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) void handleImportFile(f)
            }}
            aria-label="选择要导入的 JSON 文件"
          />
          <button className="btn small" onClick={() => setShowSettings((v) => !v)} title="选择「全部」中显示的数据源">
            ⚙ 数据源
          </button>
          <button className="btn small" onClick={() => setTick((t) => t + 1)} disabled={loading} title="刷新快讯">
            ↻ 刷新
          </button>
        </div>
      </div>

      {favNote && (
        <div className={`news-note${favNote.error ? ' news-note-error' : ''}`}>
          <span>{favNote.text}</span>
          <button className="btn small" onClick={() => setFavNote(null)} aria-label="关闭提示">
            ✕
          </button>
        </div>
      )}

      {showSettings && (
        <div className="news-settings">
          <div className="news-settings-title">「全部」显示的数据源</div>
          {sources.map((s) => (
            <label className="news-settings-item" key={s.key}>
              <input
                type="checkbox"
                checked={enabled?.has(s.key) ?? true}
                onChange={() => toggleSource(s.key)}
              />
              {s.icon && <img src={`/icons/${s.icon}`} alt="" />}
              {s.name}
            </label>
          ))}
          <div className="news-settings-hint">
            取消勾选后,该源不会出现在「全部」与顶部标签中;配置保存在后端数据库,换浏览器 / 清缓存 / 重启都能恢复。
          </div>
        </div>
      )}

      {noneEnabled && active !== 'fav' && (
        <div className="error-banner">
          ⚠ 未启用任何数据源,请点击「⚙ 数据源」勾选要显示的平台。
          <button className="btn small" onClick={() => setShowSettings(true)}>
            去设置
          </button>
        </div>
      )}

      {error && (
        <div className="error-banner">
          ⚠ {error}
          <button className="btn small" onClick={() => setTick((t) => t + 1)}>
            重试
          </button>
        </div>
      )}

      <div className="news-list" ref={listRef}>
        {active === 'fav' ? (
          favorites.length === 0 ? (
            <p className="empty">还没有收藏的快讯,点击快讯右侧的 ☆ 收藏。</p>
          ) : shown.length === 0 ? (
            <p className="empty">没有匹配「{query.trim()}」的收藏快讯。</p>
          ) : (
            shown.map((it) => renderItem(it, true))
          )
        ) : !loading && !noneEnabled && shown.length === 0 && displayed.length === 0 ? (
          <p className="empty">暂无快讯。</p>
        ) : !loading && shown.length === 0 && displayed.length > 0 ? (
          <p className="empty">没有匹配「{query.trim()}」的快讯。</p>
        ) : (
          <>
            {shown.map((it) => renderItem(it, isFavorite(it.id)))}
            {loadingMore && <div className="news-load-more">加载中…</div>}
            {!loadingMore && shown.length < filtered.length && <div className="news-load-more">加载更多…</div>}
            {!loadingMore && shown.length >= filtered.length && !moreDone && (
              <div className="news-load-more">加载更早的快讯…</div>
            )}
          </>
        )}
      </div>
    </section>
  )
}
