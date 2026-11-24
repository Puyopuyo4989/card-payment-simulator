import { formatYen } from '../../shared/formatYen'
import type { InstallmentSimulationResult } from './types'

type Props = {
  result: InstallmentSimulationResult
}

/** 毎月の支払額・手数料の合計・支払総額（FR-012）。初回が2回目以降と異なるときだけ分けて表示する */
export default function InstallmentSummary({ result }: Props) {
  const payments =
    result.firstPayment === result.subsequentPayment
      ? [['毎月の支払額', result.subsequentPayment] as const]
      : [
          ['初回の支払額', result.firstPayment] as const,
          ['2回目以降の支払額', result.subsequentPayment] as const,
        ]
  const items = [
    ['手数料の合計', result.totalFee] as const,
    ['支払総額', result.totalPayment] as const,
    ...payments,
  ]

  return (
    <dl className="summary">
      {items.map(([label, amount]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd className="num">{formatYen(amount)}</dd>
        </div>
      ))}
    </dl>
  )
}
