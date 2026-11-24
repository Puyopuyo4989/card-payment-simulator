import { useState, type FormEvent } from 'react'
import { useReturnFocus } from '../../shared/useReturnFocus'
import type { RevolvingSimulationRequest } from './types'

type Props = {
  onSubmit: (request: RevolvingSimulationRequest) => void
  /** 実行中。古い応答で結果が上書きされないよう、応答が来るまで「計算する」を押せなくする。終わったらボタンにフォーカスを戻す */
  submitting?: boolean
}

/** 入力フォーム。入力のチェックはサーバーが行うので、入力されたままの文字列を渡す */
export default function RevolvingForm({ onSubmit, submitting = false }: Props) {
  const [balance, setBalance] = useState('')
  const [monthlyPayment, setMonthlyPayment] = useState('')
  const [annualRate, setAnnualRate] = useState('')
  const buttonRef = useReturnFocus(submitting)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit({ balance, monthlyPayment, annualRate })
  }

  return (
    <form onSubmit={handleSubmit}>
      <div>
        <label htmlFor="balance">利用残高（円）</label>
        <input
          id="balance"
          type="text"
          inputMode="numeric"
          value={balance}
          onChange={(event) => setBalance(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor="monthlyPayment">毎月の支払額（円）</label>
        <input
          id="monthlyPayment"
          type="text"
          inputMode="numeric"
          value={monthlyPayment}
          onChange={(event) => setMonthlyPayment(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor="annualRate">実質年率（%）</label>
        <input
          id="annualRate"
          type="text"
          inputMode="decimal"
          value={annualRate}
          onChange={(event) => setAnnualRate(event.target.value)}
        />
      </div>
      <button ref={buttonRef} type="submit" disabled={submitting}>
        計算する
      </button>
    </form>
  )
}
