import { formatYen } from '../../shared/formatYen'
import type { ScheduleRow } from './types'

type Props = {
  schedule: ScheduleRow[]
}

/** 返済表（FR-013）。1回目から最終回まで、APIが返した順に表示する */
export default function ScheduleTable({ schedule }: Props) {
  return (
    <table>
      <thead>
        <tr>
          <th scope="col">回</th>
          <th scope="col">支払額</th>
          <th scope="col">手数料</th>
          <th scope="col">元金</th>
          <th scope="col">支払後の残高</th>
        </tr>
      </thead>
      <tbody>
        {schedule.map((row) => (
          <tr key={row.number}>
            <td>{row.number}</td>
            <td>{formatYen(row.payment)}</td>
            <td>{formatYen(row.fee)}</td>
            <td>{formatYen(row.principal)}</td>
            <td>{formatYen(row.balanceAfter)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
