import { useEffect, useState } from 'react'
import { formatDateTime } from '../../shared/formatDateTime'
import { list } from './api'
import { STATUS_LABELS, STATUSES } from './statusLabels'
import type {
  ApiOutcome,
  ApplicationList as ListResult,
  ApplicationStatus,
} from './types'

type Props = {
  /** 申込番号のボタンを押したとき */
  onSelect: (applicationNumber: string) => void
}

/**
 * 申込の一覧（US2）。開くたび・絞り込みを変えるたびに API を呼び、最新の状態を出す。
 * 並びは API が返した順（申込日時の新しい順）のままにし、画面では並べ替えない
 */
export default function ApplicationList({ onSelect }: Props) {
  const [status, setStatus] = useState<ApplicationStatus | ''>('')
  const [outcome, setOutcome] = useState<ApiOutcome<
    ListResult,
    'status'
  > | null>(null)

  useEffect(() => {
    // 絞り込みを続けて変えたとき、古い応答で新しい一覧を上書きしない
    let current = true
    list(status).then((result) => {
      if (current) {
        setOutcome(result)
      }
    })
    return () => {
      current = false
    }
  }, [status])

  return (
    <>
      <div className="form-field">
        <label htmlFor="statusFilter">状態</label>
        <select
          id="statusFilter"
          value={status}
          onChange={(event) =>
            setStatus(event.target.value as ApplicationStatus | '')
          }
        >
          <option value="">すべて</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {STATUS_LABELS[value]}
            </option>
          ))}
        </select>
      </div>
      {outcome && <ListBody outcome={outcome} onSelect={onSelect} />}
    </>
  )
}

function ListBody({
  outcome,
  onSelect,
}: {
  outcome: ApiOutcome<ListResult, 'status'>
  onSelect: (applicationNumber: string) => void
}) {
  if (outcome.kind !== 'success') {
    // 画面からは誤った状態を送らないので、入力エラーも予期しないエラーとして扱う
    return (
      <p className="field-error" role="alert">
        一覧を読み込めませんでした。もう一度お試しください
      </p>
    )
  }
  if (outcome.result.items.length === 0) {
    return <p>該当する申込はありません</p>
  }
  return (
    <div className="table-wrap">
      <table className="schedule-table">
        <thead>
          <tr>
            <th scope="col">申込番号</th>
            <th scope="col">氏名（漢字）</th>
            <th scope="col">状態</th>
            <th scope="col">申込日時</th>
          </tr>
        </thead>
        <tbody>
          {outcome.result.items.map((item) => (
            <tr key={item.applicationNumber}>
              <td>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => onSelect(item.applicationNumber)}
                >
                  {item.applicationNumber}
                </button>
              </td>
              <td>{item.nameKanji}</td>
              <td>
                <span className="status-badge">
                  {STATUS_LABELS[item.status]}
                </span>
              </td>
              <td>{formatDateTime(item.appliedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
