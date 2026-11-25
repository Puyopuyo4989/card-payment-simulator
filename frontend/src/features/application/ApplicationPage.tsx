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

  // 部品と部品の間のすき間は .stack で1か所で決める（index.css）。中の部品には上下の余白を付けない
  return <div className="stack">{content()}</div>

  function content() {
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
        <div>
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
}
