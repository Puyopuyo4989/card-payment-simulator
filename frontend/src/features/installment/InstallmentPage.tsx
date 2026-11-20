import { useState } from 'react'
import { simulate } from './api'
import InstallmentForm from './InstallmentForm'
import InstallmentSummary from './InstallmentSummary'
import ScheduleTable from './ScheduleTable'
import type {
  InstallmentSimulationRequest,
  InstallmentSimulationResult,
} from './types'

/** 入力 → 結果の表示。エラーの表示は US3 で足す */
export default function InstallmentPage() {
  const [result, setResult] = useState<InstallmentSimulationResult | null>(null)

  async function handleSubmit(request: InstallmentSimulationRequest) {
    // 前回の結果が新しい結果と混ざって見えないよう、APIを呼ぶ前に消す（spec のエッジケース）
    setResult(null)
    setResult(await simulate(request))
  }

  return (
    <>
      <InstallmentForm onSubmit={handleSubmit} />
      {result && (
        <>
          <InstallmentSummary result={result} />
          <ScheduleTable schedule={result.schedule} />
        </>
      )}
    </>
  )
}
