import { formatYen } from './formatYen'
import type { ScheduleRow } from './types'

type Props = {
  schedule: ScheduleRow[]
}

/** 返済表（分割払いの FR-013、リボ払いの FR-014）。1回目から最終回まで、APIが返した順に表示する */
export default function ScheduleTable({ schedule }: Props) {
  return (
    // 数字の列はすべて右寄せにして、桁をそろえる（分割払いの FR-020）。狭い画面では表だけを横に動かせる
    <div className="table-wrap">
      <table className="schedule-table">
        <thead>
          <tr>
            <th scope="col" className="num">
              回
            </th>
            <th scope="col" className="num">
              支払額
            </th>
            <th scope="col" className="num">
              手数料
            </th>
            <th scope="col" className="num">
              元金
            </th>
            <th scope="col" className="num">
              支払後の残高
            </th>
          </tr>
        </thead>
        <tbody>
          {schedule.map((row) => (
            <tr key={row.number}>
              <td className="num">{row.number}</td>
              <td className="num">{formatYen(row.payment)}</td>
              <td className="num">{formatYen(row.fee)}</td>
              <td className="num">{formatYen(row.principal)}</td>
              <td className="num">{formatYen(row.balanceAfter)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
