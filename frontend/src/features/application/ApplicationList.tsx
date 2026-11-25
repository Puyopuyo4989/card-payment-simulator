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
 * 並びは API が返した順（申込日時の新しい順）のままにし、画面では並べ替えない。
 * 【Should】キーワードは「検索」を押したときに使い、状態やキーワードを変えたら1ページ目に戻る（US4）
 */
export default function ApplicationList({ onSelect }: Props) {
  const [status, setStatus] = useState<ApplicationStatus | ''>('')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [outcome, setOutcome] = useState<ApiOutcome<
    ListResult,
    'status'
  > | null>(null)

  useEffect(() => {
    // 絞り込みを続けて変えたとき、古い応答で新しい一覧を上書きしない
    let current = true
    list(status, { keyword, page }).then((result) => {
      if (current) {
        setOutcome(result)
      }
    })
    return () => {
      current = false
    }
  }, [status, keyword, page])

  return (
    <>
      <div className="form">
        <form
          className="form-field search-field"
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            setKeyword(keywordInput)
            setPage(1)
          }}
        >
          <label htmlFor="keywordFilter">キーワード</label>
          <input
            id="keywordFilter"
            type="text"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
          />
          <button type="submit" className="button-secondary">
            検索
          </button>
        </form>
        <div className="form-field">
          <label htmlFor="statusFilter">状態</label>
          <select
            id="statusFilter"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ApplicationStatus | '')
              setPage(1)
            }}
          >
            <option value="">すべて</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
          </select>
        </div>
      </div>
      {outcome && (
        <ListBody outcome={outcome} onSelect={onSelect} onPage={setPage} />
      )}
    </>
  )
}

function ListBody({
  outcome,
  onSelect,
  onPage,
}: {
  outcome: ApiOutcome<ListResult, 'status'>
  onSelect: (applicationNumber: string) => void
  onPage: (page: number) => void
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
  const { page, totalPages } = outcome.result
  return (
    <>
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
      {page !== undefined && totalPages !== undefined && (
        <nav className="pager" aria-label="ページ">
          <button
            type="button"
            className="button-secondary"
            disabled={page <= 1}
            onClick={() => onPage(page - 1)}
          >
            前へ
          </button>
          <span>
            {page} / {totalPages} ページ
          </span>
          <button
            type="button"
            className="button-secondary"
            disabled={page >= totalPages}
            onClick={() => onPage(page + 1)}
          >
            次へ
          </button>
        </nav>
      )}
    </>
  )
}
