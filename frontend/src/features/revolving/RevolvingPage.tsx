import ScheduleTable from '../../shared/ScheduleTable'
import { useSimulation } from '../../shared/useSimulation'
import { simulate } from './api'
import RevolvingForm from './RevolvingForm'
import RevolvingSummary from './RevolvingSummary'

/** 入力 → 結果の表示。新しい結果だけを表示する（実行中の扱いは分割払いと共通の useSimulation） */
export default function RevolvingPage() {
  const { outcome, submitting, submit } = useSimulation(simulate)

  return (
    <>
      <RevolvingForm onSubmit={submit} submitting={submitting} />
      {outcome?.kind === 'success' && (
        <>
          <RevolvingSummary result={outcome.result} />
          <ScheduleTable schedule={outcome.result.schedule} />
        </>
      )}
    </>
  )
}
