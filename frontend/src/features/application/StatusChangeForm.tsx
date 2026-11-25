import { useEffect, useRef, useState } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { useReturnFocus } from '../../shared/useReturnFocus'
import { changeStatus } from './api'
import { nextActions, STATUS_LABELS } from './statusLabels'
import type {
  ApiOutcome,
  ApplicationDetail,
  ApplicationStatus,
  StatusChangeField,
} from './types'

type Props = {
  /** 今の詳細。状態から出す操作を決め、version をそのまま送る */
  detail: ApplicationDetail
  /** 変更できたとき。変更後の詳細を渡す */
  onChanged: (detail: ApplicationDetail) => void
  /** ほかの担当者が先に変えていたとき。詳細を読み直してもらう */
  onConflict: () => void
}

const NO_DECISION = '承認か否決を選んでください'

/**
 * 状態の変更のフォーム（US3、research.md R12）。今の状態から許される変更だけを出すが、
 * 遷移の正しさは API が守る（フロントでは判定しない）。入力のチェックもサーバーが行う。
 * 許される変更が1つ（申込 → 審査中）ならそのボタンで変える。2つ以上（審査中 → 承認・否決）は取り消せないので、
 * 選んでから「決定する」で変える2段階にし、否決の理由の欄は「否決」を選んだときだけ出す（T081）。
 * 成功したら「○○に変更しました」を出し、そこにフォーカスを移す（T085）
 */
export default function StatusChangeForm({
  detail,
  onChanged,
  onConflict,
}: Props) {
  const [staffName, setStaffName] = useState('')
  const [rejectionReason, setRejectionReason] = useState('')
  const [decision, setDecision] = useState<ApplicationStatus | null>(null)
  const [decisionMissing, setDecisionMissing] = useState(false)
  const [changedTo, setChangedTo] = useState<ApplicationStatus | null>(null)
  const [outcome, setOutcome] = useState<ApiOutcome<
    ApplicationDetail,
    StatusChangeField
  > | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // 成功の知らせにフォーカスを移す。押したボタンは消えるか、取り消せない決定のボタンに変わるので、そこには戻さない。
  // useReturnFocus より先に動かす（こちらがフォーカスを移すと、useReturnFocus は動かさない）
  const statusRef = useRef<HTMLParagraphElement>(null)
  useEffect(() => {
    if (changedTo) {
      statusRef.current?.focus()
    }
  }, [changedTo])
  // 入力エラーなどで終わったら、押したボタン（ボタンは1つだけ）にフォーカスを戻す
  const buttonRef = useReturnFocus(submitting)

  const errors = outcome?.kind === 'inputError' ? outcome.errors : []
  const { attributes: errorAttributes, message: errorMessage } =
    fieldErrors(errors)
  // 入力欄のない項目（変更後の状態・版）のエラーは、フォームの上に出す
  const formErrors = errors.filter(
    (error) => error.field === 'toStatus' || error.field === 'version',
  )
  const actions = nextActions(detail.status)
  const twoStep = actions.length > 1

  async function submit(toStatus: ApplicationStatus) {
    setOutcome(null)
    setChangedTo(null)
    setSubmitting(true)
    const result = await changeStatus(detail.applicationNumber, {
      toStatus,
      staffName,
      // 理由の欄は否決のときだけ見えているので、ほかのときは送らない
      rejectionReason: toStatus === 'REJECTED' ? rejectionReason : '',
      version: String(detail.version),
    })
    setSubmitting(false)
    if (result.kind === 'success') {
      setStaffName('')
      setRejectionReason('')
      setDecision(null)
      setChangedTo(result.result.status)
      onChanged(result.result)
      return
    }
    setOutcome(result)
    if (result.kind === 'conflict') {
      // 入力していた担当者名と選んだ結果は残し、最新の詳細を読み直してもらう（FR-019）
      onConflict()
    }
  }

  function handleDecide() {
    if (decision === null) {
      setDecisionMissing(true)
      return
    }
    submit(decision)
  }

  return (
    <section aria-labelledby="status-change-heading">
      <h3 id="status-change-heading">状態の変更</h3>
      {changedTo && (
        <p ref={statusRef} role="status" tabIndex={-1} className="notice">
          {STATUS_LABELS[changedTo]}に変更しました
        </p>
      )}
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
          {twoStep && (
            <fieldset
              className="radio-field"
              aria-describedby={decisionMissing ? 'decision-error' : undefined}
            >
              <legend>審査の結果</legend>
              <div className="radio-options">
                {actions.map((action) => (
                  <label key={action.toStatus}>
                    <input
                      type="radio"
                      name="decision"
                      value={action.toStatus}
                      checked={decision === action.toStatus}
                      onChange={() => {
                        setDecision(action.toStatus)
                        setDecisionMissing(false)
                      }}
                    />
                    {action.label}
                  </label>
                ))}
              </div>
              {decisionMissing && (
                <p id="decision-error" className="field-error" role="alert">
                  {NO_DECISION}
                </p>
              )}
            </fieldset>
          )}
          {decision === 'REJECTED' && (
            <div className="form-field">
              <label htmlFor="rejectionReason">否決の理由</label>
              <textarea
                id="rejectionReason"
                rows={3}
                value={rejectionReason}
                onChange={(event) => setRejectionReason(event.target.value)}
                {...errorAttributes('rejectionReason')}
              />
              {errorMessage('rejectionReason')}
            </div>
          )}
          <div className="form-actions">
            {twoStep ? (
              <button
                ref={buttonRef}
                type="button"
                className="button"
                disabled={submitting}
                onClick={handleDecide}
              >
                決定する
              </button>
            ) : (
              <button
                ref={buttonRef}
                type="button"
                className="button"
                disabled={submitting}
                onClick={() => submit(actions[0].toStatus)}
              >
                {actions[0].label}
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
