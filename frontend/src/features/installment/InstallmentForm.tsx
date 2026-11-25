import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { useReturnFocus } from '../../shared/useReturnFocus'
import { amountForApi, formatAmountInput, formatRateInput } from './inputFormat'
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
 * 【Should】ただし利用金額のカンマは取り除いて渡し、フォーカスが外れたときに表示を整える（FR-015〜FR-018、inputFormat.ts）。
 * 整えられない値は、入力されたまま渡す（チェックはサーバー）。
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
    // 【Should】利用金額は、表示のカンマを取り除いて送る（FR-015・FR-017）。ほかは入力されたまま
    onSubmit({ amount: amountForApi(amount), paymentCount, annualRate })
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="form-field">
        <label htmlFor="amount">利用金額（円）</label>
        <input
          id="amount"
          type="text"
          inputMode="numeric"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          // 【Should】入力中はカンマを差し込まず、フォーカスが外れたときに整える（FR-015・FR-018）
          onBlur={() => setAmount(formatAmountInput)}
          {...errorAttributes('amount')}
        />
        {errorMessage('amount')}
      </div>
      <div className="form-field">
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
      <div className="form-field">
        <label htmlFor="annualRate">実質年率（%）</label>
        <input
          id="annualRate"
          type="text"
          inputMode="decimal"
          value={annualRate}
          onChange={(event) => setAnnualRate(event.target.value)}
          onBlur={() => setAnnualRate(formatRateInput)}
          {...errorAttributes('annualRate')}
        />
        {errorMessage('annualRate')}
      </div>
      <div className="form-actions">
        <button
          ref={buttonRef}
          type="submit"
          className="button"
          disabled={submitting}
        >
          計算する
        </button>
      </div>
    </form>
  )
}
