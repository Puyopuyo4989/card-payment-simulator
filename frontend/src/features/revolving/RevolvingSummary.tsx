import { formatYen } from '../../shared/formatYen'
import type { RevolvingSimulationResult } from './types'

type Props = {
  result: RevolvingSimulationResult
}

/** 完済までの支払回数・手数料の合計・支払総額（FR-013）。金額の計算はしない */
export default function RevolvingSummary({ result }: Props) {
  const items = [
    ['完済までの支払回数', `${result.paymentCount}回`],
    ['手数料の合計', formatYen(result.totalFee)],
    ['支払総額', formatYen(result.totalPayment)],
  ] as const

  return (
    <dl>
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
