import { useState, type FormEvent } from 'react'
import type { InstallmentSimulationRequest } from './types'

/** 支払回数の選択肢（FR-003） */
const PAYMENT_COUNTS = [
  '3',
  '5',
  '6',
  '10',
  '12',
  '15',
  '18',
  '20',
  '24',
  '30',
  '36',
]

type Props = {
  onSubmit: (request: InstallmentSimulationRequest) => void
}

/** 入力フォーム。入力のチェックはサーバーが行うので、入力されたままの文字列を渡す（research.md R8） */
export default function InstallmentForm({ onSubmit }: Props) {
  const [amount, setAmount] = useState('')
  const [paymentCount, setPaymentCount] = useState('')
  const [annualRate, setAnnualRate] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit({ amount, paymentCount, annualRate })
  }

  return (
    <form onSubmit={handleSubmit}>
      <div>
        <label htmlFor="amount">利用金額（円）</label>
        <input
          id="amount"
          type="text"
          inputMode="numeric"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
        />
      </div>
      <div>
        <label htmlFor="paymentCount">支払回数</label>
        <select
          id="paymentCount"
          value={paymentCount}
          onChange={(event) => setPaymentCount(event.target.value)}
        >
          <option value="">選んでください</option>
          {PAYMENT_COUNTS.map((count) => (
            <option key={count} value={count}>
              {count}回
            </option>
          ))}
        </select>
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
      <button type="submit">計算する</button>
    </form>
  )
}
