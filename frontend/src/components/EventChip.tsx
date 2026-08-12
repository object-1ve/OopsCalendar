import type { EarningsEvent } from '../types'

interface Props {
  event: EarningsEvent
  compact?: boolean
  /** 已收藏:黄色高亮 + ★ */
  favorite?: boolean
}

/** 单个财报徽章:蓝色=盘前,紫色=盘后,灰色=盘中;实心+✓=已公布,空心=未公布。 */
export default function EventChip({ event, compact, favorite }: Props) {
  const cls = `chip ${event.session.toLowerCase()} ${event.confirmed ? 'confirmed' : 'pending'}${
    favorite ? ' favorite' : ''
  }`
  const displayName = event.nameZh ?? event.name ?? ''
  const title = `${favorite ? '★ 收藏 · ' : ''}${event.symbol} ${displayName} · ${
    event.confirmed ? '已公布' : '未公布'
  } · ${event.session === 'BMO' ? '盘前' : event.session === 'AMC' ? '盘后' : event.session === 'DNH' ? '盘中' : '待定'}`
  return (
    <span className={cls} title={title.trim()}>
      {favorite && '★ '}
      {event.confirmed ? '✓' : '·'}{' '}
      {compact ? event.symbol : displayName || event.symbol}
    </span>
  )
}
