import type { Session } from '../types'

const ITEMS: { session: Session; confirmed: boolean; label: string }[] = [
  { session: 'BMO', confirmed: false, label: '盘前 · 未公布' },
  { session: 'BMO', confirmed: true, label: '盘前 · 已公布' },
  { session: 'AMC', confirmed: false, label: '盘后 · 未公布' },
  { session: 'AMC', confirmed: true, label: '盘后 · 已公布' },
]

/** 图例:说明盘前/盘后与已公布/未公布的配色。 */
export default function Legend() {
  return (
    <div className="legend">
      {ITEMS.map((item) => (
        <span key={`${item.session}-${item.confirmed}`} className="legend-item">
          <span className={`chip demo ${item.session.toLowerCase()} ${item.confirmed ? 'confirmed' : 'pending'}`}>
            {item.confirmed ? '✓' : '·'}
          </span>
          <span className="legend-label">{item.label}</span>
        </span>
      ))}
      <span className="legend-item">
        <span className="chip demo dnh pending">盘中</span>
        <span className="legend-label">盘中 / 待定</span>
      </span>
      <span className="legend-note">日期格:顶部蓝条=当日有盘前,底部紫条=当日有盘后,底色同色渐变</span>
    </div>
  )
}
