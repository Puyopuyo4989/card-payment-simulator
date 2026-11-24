import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { useReturnFocus } from '../../shared/useReturnFocus'
import type { FieldError, RevolvingSimulationRequest } from './types'

type Props = {
  onSubmit: (request: RevolvingSimulationRequest) => void
  /** 実行中。古い応答で結果が上書きされないよう、応答が来るまで「計算する」を押せなくする。終わったらボタンにフォーカスを戻す */
  submitting?: boolean
  /** サーバーが返した項目ごとの入力エラー */
  errors?: FieldError[]
}

/**
 * 入力フォーム。入力のチェックはサーバーが行うので、入力されたままの文字列を渡す。
 * エラーの表示とフォーカスを戻す処理は、分割払いと共通（shared/fieldErrors・shared/useReturnFocus）
 */
export default function RevolvingForm({
  onSubmit,
  submitting = false,
  errors = [],
}: Props) {
  const [balance, setBalance] = useState('')
  const [monthlyPayment, setMonthlyPayment] = useState('')
  const [annualRate, setAnnualRate] = useState('')
  const buttonRef = useReturnFocus(submitting)
  const { attributes: errorAttributes, message: errorMessage } =
    fieldErrors(errors)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit({ balance, monthlyPayment, annualRate })
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="form-field">
        <label htmlFor="balance">利用残高（円）</label>
        <input
          id="balance"
          type="text"
          inputMode="numeric"
          value={balance}
          onChange={(event) => setBalance(event.target.value)}
          {...errorAttributes('balance')}
        />
        {errorMessage('balance')}
      </div>
      <div className="form-field">
        <label htmlFor="monthlyPayment">毎月の支払額（円）</label>
        <input
          id="monthlyPayment"
          type="text"
          inputMode="numeric"
          value={monthlyPayment}
          onChange={(event) => setMonthlyPayment(event.target.value)}
          {...errorAttributes('monthlyPayment')}
        />
        {errorMessage('monthlyPayment')}
      </div>
      <div className="form-field">
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
