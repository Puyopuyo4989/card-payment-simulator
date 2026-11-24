import ScheduleTable from '../../shared/ScheduleTable'
import { useSimulation } from '../../shared/useSimulation'
import { simulate } from './api'
import InstallmentForm from './InstallmentForm'
import InstallmentSummary from './InstallmentSummary'

/** 入力 → 結果かエラーの表示。新しい結果かエラーのどちらかだけを表示する（実行中の扱いはリボ払いと共通の useSimulation） */
export default function InstallmentPage() {
  const { outcome, submitting, submit } = useSimulation(simulate)

  return (
    <>
      <InstallmentForm
        onSubmit={submit}
        submitting={submitting}
        errors={outcome?.kind === 'inputError' ? outcome.errors : []}
      />
      {outcome?.kind === 'unexpectedError' && (
        <p className="field-error" role="alert">
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
