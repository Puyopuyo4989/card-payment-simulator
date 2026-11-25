import { useEffect, useState } from 'react'
import { formatDateTime } from '../../shared/formatDateTime'
import { getDetail } from './api'
import { formatManYen } from './formatManYen'
import StatusChangeForm from './StatusChangeForm'
import { STATUS_LABELS } from './statusLabels'
import type { ApiOutcome, ApplicationDetail as Detail } from './types'

type Props = {
  applicationNumber: string
  /** 一覧に戻るボタンを押したとき */
  onBack: () => void
}

/**
 * 申込の詳細（US2）。全項目と、変更履歴（API の順＝古い順）と、状態の変更のフォーム（US3）を出す。
 * 変更できたら返った詳細で置き換え、ほかの担当者とぶつかったら読み直す
 */
export default function ApplicationDetail({
  applicationNumber,
  onBack,
}: Props) {
  const [outcome, setOutcome] = useState<ApiOutcome<Detail, never> | null>(null)

  useEffect(() => {
    let current = true
    getDetail(applicationNumber).then((result) => {
      if (current) {
        setOutcome(result)
      }
    })
    return () => {
      current = false
    }
  }, [applicationNumber])

  const backButton = (
    <button type="button" className="button-secondary" onClick={onBack}>
      一覧に戻る
    </button>
  )

  if (outcome === null) {
    return backButton
  }
  if (outcome.kind !== 'success') {
    return (
      <>
        <p className="field-error" role="alert">
          {outcome.kind === 'notFound'
            ? '申込が見つかりません'
            : '詳細を読み込めませんでした。もう一度お試しください'}
        </p>
        {backButton}
      </>
    )
  }

  const detail = outcome.result
  return (
    <>
      {backButton}
      <h2>申込番号 {detail.applicationNumber}</h2>
      <dl className="summary">
        <Item term="氏名（漢字）">{detail.nameKanji}</Item>
        <Item term="氏名（カナ）">{detail.nameKana}</Item>
        <Item term="生年月日">{detail.birthDate}</Item>
        <Item term="年収">{formatManYen(detail.annualIncome)}</Item>
        <Item term="勤務先名">{detail.employerName}</Item>
        <Item term="電話番号">{detail.phoneNumber}</Item>
        <Item term="状態">
          <span className="status-badge">{STATUS_LABELS[detail.status]}</span>
        </Item>
        <Item term="申込日時">{formatDateTime(detail.appliedAt)}</Item>
        <Item term="更新日時">{formatDateTime(detail.updatedAt)}</Item>
      </dl>
      <h3 id="history-heading">変更履歴</h3>
      {detail.history.length === 0 ? (
        <p>変更履歴はありません</p>
      ) : (
        <div className="table-wrap">
          <table className="schedule-table" aria-labelledby="history-heading">
            <thead>
              <tr>
                <th scope="col">変更日時</th>
                <th scope="col">変更前 → 変更後</th>
                <th scope="col">担当者名</th>
                <th scope="col">否決の理由</th>
              </tr>
            </thead>
            <tbody>
              {detail.history.map((entry, index) => (
                <tr key={index}>
                  <td>{formatDateTime(entry.changedAt)}</td>
                  <td>
                    {STATUS_LABELS[entry.fromStatus]} →{' '}
                    {STATUS_LABELS[entry.toStatus]}
                  </td>
                  <td>{entry.staffName}</td>
                  <td>{entry.rejectionReason ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <StatusChangeForm
        detail={detail}
        onChanged={(changed) =>
          setOutcome({ kind: 'success', result: changed })
        }
        onConflict={() => {
          // ほかの担当者が先に変えたので、最新の状態と履歴を読み直す（FR-019、research.md R12）
          getDetail(applicationNumber).then(setOutcome)
        }}
      />
    </>
  )
}

function Item({ term, children }: { term: string; children: React.ReactNode }) {
  return (
    <div>
      <dt>{term}</dt>
      <dd>{children}</dd>
    </div>
  )
}
