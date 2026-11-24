import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { useReturnFocus } from '../../shared/useReturnFocus'
import type { FieldError, InstallmentSimulationRequest } from './types'

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
  /** サーバーが返した項目ごとの入力エラー */
  errors?: FieldError[]
  /** 実行中。古い応答で結果が上書きされないよう、応答が来るまで「計算する」を押せなくする。終わったらボタンにフォーカスを戻す */
  submitting?: boolean
}

/**
 * 入力フォーム。入力のチェックはサーバーが行うので、入力されたままの文字列を渡す（research.md R8）。
 * エラーの表示とフォーカスを戻す処理は、リボ払いと共通（shared/fieldErrors・shared/useReturnFocus）
 */
export default function InstallmentForm({
  onSubmit,
  errors = [],
  submitting = false,
}: Props) {
  const [amount, setAmount] = useState('')
  const [paymentCount, setPaymentCount] = useState('')
  const [annualRate, setAnnualRate] = useState('')
  const buttonRef = useReturnFocus(submitting)
  const { attributes: errorAttributes, message: errorMessage } =
    fieldErrors(errors)

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
          {...errorAttributes('amount')}
        />
        {errorMessage('amount')}
      </div>
      <div>
        <label htmlFor="paymentCount">支払回数</label>
        <select
          id="paymentCount"
          value={paymentCount}
          onChange={(event) => setPaymentCount(event.target.value)}
          {...errorAttributes('paymentCount')}
        >
          <option value="">選んでください</option>
          {PAYMENT_COUNTS.map((count) => (
            <option key={count} value={count}>
              {count}回
            </option>
          ))}
        </select>
        {errorMessage('paymentCount')}
      </div>
      <div>
        <label htmlFor="annualRate">実質年率（%）</label>
        <input
          id="annualRate"
          type="text"
          inputMode="decimal"
          value={annualRate}
          onChange={(event) => setAnnualRate(event.target.value)}
          {...errorAttributes('annualRate')}
        />
        {errorMessage('annualRate')}
      </div>
      <button ref={buttonRef} type="submit" disabled={submitting}>
        計算する
      </button>
    </form>
  )
}
