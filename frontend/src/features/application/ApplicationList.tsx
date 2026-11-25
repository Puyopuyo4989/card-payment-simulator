import { useEffect, useState } from 'react'
import { fieldErrors } from '../../shared/fieldErrors'
import { formatDateTime } from '../../shared/formatDateTime'
import { list } from './api'
import { STATUS_LABELS, STATUSES } from './statusLabels'
import type {
  ApplicationList as ListResult,
  ApplicationStatus,
  FieldError,
  ListField,
} from './types'

type Props = {
  /** 申込番号のボタンを押したとき */
  onSelect: (applicationNumber: string) => void
}

/**
 * 画面に出しているもの。result は最後に読めた一覧（入力の誤りが来ても消さない）、errors はその欄の下に出す誤り、
 * failed は予期しないエラー（一覧も消す）
 */
type Shown = {
  result: ListResult | null
  errors: FieldError<ListField>[]
  failed: boolean
}

/**
 * 申込の一覧（US2）。開くたび・絞り込みを変えるたびに API を呼び、最新の状態を出す。
 * 並びは API が返した順（申込日時の新しい順）のままにし、画面では並べ替えない。
 * 【Should】キーワードは「検索」を押したときに使い、状態やキーワードを変えたら1ページ目に戻る（US4）。
 * キーワード・ページの誤り（51文字のキーワードなど）は、予期しないエラーにせず、その欄の下に出す
 */
export default function ApplicationList({ onSelect }: Props) {
  const [status, setStatus] = useState<ApplicationStatus | ''>('')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [shown, setShown] = useState<Shown | null>(null)

  useEffect(() => {
    // 絞り込みを続けて変えたとき、古い応答で新しい一覧を上書きしない
    let current = true
    list(status, { keyword, page }).then((outcome) => {
      if (!current) {
        return
      }
      if (outcome.kind === 'success') {
        setShown({ result: outcome.result, errors: [], failed: false })
      } else if (outcome.kind === 'inputError') {
        // 前の一覧は残し、誤りだけを出す
        setShown((previous) => ({
          result: previous?.result ?? null,
          errors: outcome.errors,
          failed: false,
        }))
      } else {
        setShown({ result: null, errors: [], failed: true })
      }
    })
    return () => {
      current = false
    }
  }, [status, keyword, page])

  const { attributes: errorAttributes, message: errorMessage } = fieldErrors(
    shown?.errors ?? [],
  )

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
            {...errorAttributes('keyword')}
          />
          <button type="submit" className="button-secondary">
            検索
          </button>
          {errorMessage('keyword')}
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
            {...errorAttributes('status')}
          >
            <option value="">すべて</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
          </select>
          {errorMessage('status')}
        </div>
      </div>
      {shown === null && <p role="status">読み込み中です</p>}
      {shown?.failed && (
        <p className="field-error" role="alert">
          一覧を読み込めませんでした。もう一度お試しください
        </p>
      )}
      {shown?.result && (
        <ListBody
          result={shown.result}
          pagerAttributes={errorAttributes('page')}
          onSelect={onSelect}
          onPage={setPage}
        />
      )}
      {errorMessage('page')}
    </>
  )
}

function ListBody({
  result,
  pagerAttributes,
  onSelect,
  onPage,
}: {
  result: ListResult
  /** ページの誤りがあるとき、ページの切り替えと誤りを結び付ける属性 */
  pagerAttributes: { 'aria-describedby'?: string }
  onSelect: (applicationNumber: string) => void
  onPage: (page: number) => void
}) {
  if (result.items.length === 0) {
    return <p>該当する申込はありません</p>
  }
  const { page, totalPages } = result
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
            {result.items.map((item) => (
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
        <nav
          className="pager"
          aria-label="ページ"
          aria-describedby={pagerAttributes['aria-describedby']}
        >
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
