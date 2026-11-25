import { useState } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { useReturnFocus } from '../../shared/useReturnFocus'
import { changeStatus } from './api'
import { nextActions } from './statusLabels'
import type {
  ApiOutcome,
  ApplicationDetail,
  ApplicationStatus,
  StatusChangeField,
} from './types'

type Props = {
  /** 今の詳細。状態から出すボタンを決め、version をそのまま送る */
  detail: ApplicationDetail
  /** 変更できたとき。変更後の詳細を渡す */
  onChanged: (detail: ApplicationDetail) => void
  /** ほかの担当者が先に変えていたとき。詳細を読み直してもらう */
  onConflict: () => void
}

/**
 * 状態の変更のフォーム（US3、research.md R12）。今の状態から許される変更のボタンだけを出すが、
 * 遷移の正しさは API が守る（フロントでは判定しない）。入力のチェックもサーバーが行う
 */
export default function StatusChangeForm({
  detail,
  onChanged,
  onConflict,
}: Props) {
  const [staffName, setStaffName] = useState('')
  const [rejectionReason, setRejectionReason] = useState('')
  const [outcome, setOutcome] = useState<ApiOutcome<
    ApplicationDetail,
    StatusChangeField
  > | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // 終わったらフォーカスを戻すのは、押したボタン。ほかのボタンに戻すと、続けて Enter を押したときに逆の決定になる
  const [pressed, setPressed] = useState<ApplicationStatus | null>(null)
  const buttonRef = useReturnFocus(submitting)

  const errors = outcome?.kind === 'inputError' ? outcome.errors : []
  const { attributes: errorAttributes, message: errorMessage } =
    fieldErrors(errors)
  // 入力欄のない項目（変更後の状態・版）のエラーは、フォームの上に出す
  const formErrors = errors.filter(
    (error) => error.field === 'toStatus' || error.field === 'version',
  )
  const actions = nextActions(detail.status)

  async function handleClick(toStatus: ApplicationStatus) {
    setOutcome(null)
    setPressed(toStatus)
    setSubmitting(true)
    const result = await changeStatus(detail.applicationNumber, {
      toStatus,
      staffName,
      rejectionReason,
      version: String(detail.version),
    })
    setSubmitting(false)
    if (result.kind === 'success') {
      setStaffName('')
      setRejectionReason('')
      onChanged(result.result)
      return
    }
    setOutcome(result)
    if (result.kind === 'conflict') {
      // 入力していた担当者名は残し、最新の詳細を読み直してもらう（FR-019）
      onConflict()
    }
  }

  return (
    <section aria-labelledby="status-change-heading">
      <h3 id="status-change-heading">状態の変更</h3>
      {outcome?.kind === 'conflict' && (
        <p className="field-error" role="alert">
          {outcome.title}
          <br />
          {outcome.detail}
        </p>
      )}
      {outcome &&
        outcome.kind !== 'inputError' &&
        outcome.kind !== 'conflict' && (
          <p className="field-error" role="alert">
            状態を変えられませんでした。もう一度お試しください
          </p>
        )}
      {formErrors.map((error) => (
        <p key={error.field} className="field-error" role="alert">
          {error.message}
        </p>
      ))}
      {actions.length === 0 ? (
        <p>審査が終わった申込の状態は変えられません</p>
      ) : (
        <div className="form">
          <div className="form-field">
            <label htmlFor="staffName">担当者名</label>
            <input
              id="staffName"
              type="text"
              value={staffName}
              onChange={(event) => setStaffName(event.target.value)}
              {...errorAttributes('staffName')}
            />
            {errorMessage('staffName')}
          </div>
          {detail.status === 'UNDER_REVIEW' && (
            <div className="form-field">
              <label htmlFor="rejectionReason">否決の理由</label>
              <input
                id="rejectionReason"
                type="text"
                value={rejectionReason}
                onChange={(event) => setRejectionReason(event.target.value)}
                {...errorAttributes('rejectionReason')}
              />
              {errorMessage('rejectionReason')}
            </div>
          )}
          <div className="form-actions">
            {actions.map((action) => (
              <button
                key={action.toStatus}
                ref={action.toStatus === pressed ? buttonRef : undefined}
                type="button"
                className="button"
                disabled={submitting}
                onClick={() => handleClick(action.toStatus)}
              >
                {action.label}
              </button>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
