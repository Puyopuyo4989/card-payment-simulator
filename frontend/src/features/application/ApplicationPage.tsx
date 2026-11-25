import { useState } from 'react'
import ApplicationDetail from './ApplicationDetail'
import ApplicationForm from './ApplicationForm'
import ApplicationList from './ApplicationList'

type View =
  | { name: 'list' }
  | { name: 'register' }
  | { name: 'detail'; applicationNumber: string }

/**
 * 申込管理のタブの中身（research.md R12）。一覧 → 詳細、一覧 → 新規登録 → 詳細 を useState で切り替える（ルーターは使わない）。
 * 一覧に戻るたびに一覧を作り直し、API から最新の状態を読む
 */
export default function ApplicationPage() {
  const [view, setView] = useState<View>({ name: 'list' })
  const showList = () => setView({ name: 'list' })
  const showDetail = (applicationNumber: string) =>
    setView({ name: 'detail', applicationNumber })

  if (view.name === 'register') {
    return (
      <>
        <button type="button" className="button-secondary" onClick={showList}>
          一覧に戻る
        </button>
        <ApplicationForm onRegistered={showDetail} />
      </>
    )
  }
  if (view.name === 'detail') {
    return (
      <ApplicationDetail
        applicationNumber={view.applicationNumber}
        onBack={showList}
      />
    )
  }
  return (
    <>
      <div className="page-actions">
        <button
          type="button"
          className="button-secondary"
          onClick={() => setView({ name: 'register' })}
        >
          新規登録
        </button>
      </div>
      <ApplicationList onSelect={showDetail} />
    </>
  )
}
