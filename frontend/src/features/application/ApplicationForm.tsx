import { useState, type FormEvent } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { useReturnFocus } from '../../shared/useReturnFocus'
import { register } from './api'
import type {
  ApiOutcome,
  ApplicationDetail,
  CardApplicationRequest,
  RegistrationField,
} from './types'

type Props = {
  /** 登録できたとき。登録した申込の申込番号を渡す */
  onRegistered: (applicationNumber: string) => void
}

type Field = {
  name: RegistrationField
  label: string
  type?: 'text' | 'date'
  inputMode?: 'numeric'
  placeholder?: string
}

/** 6項目（contracts 1）。例はすべて実在しない値にする。カード番号の欄は作らない（FR-021） */
const FIELDS: Field[] = [
  { name: 'nameKanji', label: '氏名（漢字）', placeholder: '例：見本 花子' },
  {
    name: 'nameKana',
    label: '氏名（カナ）',
    placeholder: '例：ミホン　ハナコ',
  },
  { name: 'birthDate', label: '生年月日', type: 'date' },
  { name: 'annualIncome', label: '年収（万円）', inputMode: 'numeric' },
  { name: 'employerName', label: '勤務先名' },
  {
    name: 'phoneNumber',
    label: '電話番号',
    inputMode: 'numeric',
    placeholder: '例：0000000000',
  },
]

const EMPTY: CardApplicationRequest = {
  nameKanji: '',
  nameKana: '',
  birthDate: '',
  annualIncome: '',
  employerName: '',
  phoneNumber: '',
}

/**
 * 申込の登録のフォーム（US1）。入力のチェックはサーバーが行うので、入力されたままの文字列を送る。
 * エラーの表示とフォーカスを戻す処理は、分割払い・リボ払いと共通（shared/fieldErrors・shared/useReturnFocus）
 */
export default function ApplicationForm({ onRegistered }: Props) {
  const [values, setValues] = useState<CardApplicationRequest>(EMPTY)
  const [outcome, setOutcome] = useState<ApiOutcome<
    ApplicationDetail,
    RegistrationField
  > | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const buttonRef = useReturnFocus(submitting)
  const { attributes: errorAttributes, message: errorMessage } = fieldErrors(
    outcome?.kind === 'inputError' ? outcome.errors : [],
  )

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // 前のエラーが新しい結果と混ざって見えないよう、呼ぶ前に消す
    setOutcome(null)
    setSubmitting(true)
    const result = await register(values)
    setSubmitting(false)
    if (result.kind === 'success') {
      onRegistered(result.result.applicationNumber)
    } else {
      setOutcome(result)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <p className="notice">
        ダミーの情報だけを入力してください。本物の氏名・電話番号などは入力しないでください
      </p>
      {FIELDS.map((field) => (
        <div className="form-field" key={field.name}>
          <label htmlFor={field.name}>{field.label}</label>
          <input
            id={field.name}
            type={field.type ?? 'text'}
            inputMode={field.inputMode}
            placeholder={field.placeholder}
            value={values[field.name]}
            onChange={(event) =>
              setValues({ ...values, [field.name]: event.target.value })
            }
            {...errorAttributes(field.name)}
          />
          {errorMessage(field.name)}
        </div>
      ))}
      {outcome?.kind !== undefined && outcome.kind !== 'inputError' && (
        <p className="field-error" role="alert">
          登録できませんでした。もう一度お試しください
        </p>
      )}
      <div className="form-actions">
        <button
          ref={buttonRef}
          type="submit"
          className="button"
          disabled={submitting}
        >
          登録する
        </button>
      </div>
    </form>
  )
}
