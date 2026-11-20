import { useEffect, useRef, useState, type FormEvent } from 'react'
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
 * エラーは入力欄のすぐ下に赤字で出し、aria-describedby で入力欄と結び付ける（ポップアップは使わない）。
 * role="alert" で、出たことをスクリーンリーダーに伝える
 */
export default function InstallmentForm({
  onSubmit,
  errors = [],
  submitting = false,
}: Props) {
  const [amount, setAmount] = useState('')
  const [paymentCount, setPaymentCount] = useState('')
  const [annualRate, setAnnualRate] = useState('')
  const buttonRef = useRef<HTMLButtonElement>(null)
  const wasSubmitting = useRef(false)

  // 押せなくなったボタンからはフォーカスが外れるので、実行が終わったらボタンに戻す（キーボードの利用者のため）。
  // 入力欄で Enter を押したときなど、フォーカスがどこかにあるときは動かさない
  useEffect(() => {
    if (
      wasSubmitting.current &&
      !submitting &&
      document.activeElement === document.body
    ) {
      buttonRef.current?.focus()
    }
    wasSubmitting.current = submitting
  }, [submitting])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit({ amount, paymentCount, annualRate })
  }

  function messageOf(field: FieldError['field']) {
    return errors.find((error) => error.field === field)?.message
  }

  /** エラーがある項目の入力欄に付ける属性 */
  function errorAttributes(field: FieldError['field']) {
    return messageOf(field)
      ? { 'aria-invalid': true, 'aria-describedby': `${field}-error` }
      : {}
  }

  function errorMessage(field: FieldError['field']) {
    const message = messageOf(field)
    return (
      message && (
        <p id={`${field}-error`} className="field-error" role="alert">
          {message}
        </p>
      )
    )
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
