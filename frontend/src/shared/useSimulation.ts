import { useState } from 'react'
import type { SimulationOutcome } from './types'

/** 入力 → API の呼び出し → 結果かエラー。新しい結果かエラーのどちらかだけを持つ */
export function useSimulation<Req, R, F extends string>(
  simulate: (request: Req) => Promise<SimulationOutcome<R, F>>,
) {
  const [outcome, setOutcome] = useState<SimulationOutcome<R, F> | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(request: Req) {
    // 前回の結果やエラーが新しいものと混ざって見えないよう、APIを呼ぶ前に消す（spec のエッジケース）
    setOutcome(null)
    // 応答が来るまで次の実行をさせず、古い応答で新しい結果が上書きされないようにする
    setSubmitting(true)
    try {
      setOutcome(await simulate(request))
    } catch {
      // simulate は例外を投げない作りだが、もし投げても押せないままにせず、予期しないエラーとして伝える
      setOutcome({ kind: 'unexpectedError' })
    } finally {
      setSubmitting(false)
    }
  }

  return { outcome, submitting, submit }
}
