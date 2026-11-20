import { useState } from 'react'
import { simulate } from './api'
import InstallmentForm from './InstallmentForm'
import InstallmentSummary from './InstallmentSummary'
import ScheduleTable from './ScheduleTable'
import type { InstallmentSimulationRequest, SimulationOutcome } from './types'

/** 入力 → 結果かエラーの表示。新しい結果かエラーのどちらかだけを表示する */
export default function InstallmentPage() {
  const [outcome, setOutcome] = useState<SimulationOutcome | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(request: InstallmentSimulationRequest) {
    // 前回の結果やエラーが新しいものと混ざって見えないよう、APIを呼ぶ前に消す（spec のエッジケース）
    setOutcome(null)
    // 応答が来るまで次の実行をさせず、古い応答で新しい結果が上書きされないようにする
    setSubmitting(true)
    setOutcome(await simulate(request))
    setSubmitting(false)
  }

  return (
    <>
      <InstallmentForm
        onSubmit={handleSubmit}
        submitting={submitting}
        errors={outcome?.kind === 'inputError' ? outcome.errors : []}
      />
      {outcome?.kind === 'unexpectedError' && (
        <p className="field-error">
          計算できませんでした。もう一度お試しください
        </p>
      )}
      {outcome?.kind === 'success' && (
        <>
          <InstallmentSummary result={outcome.result} />
          <ScheduleTable schedule={outcome.result.schedule} />
        </>
      )}
    </>
  )
}
