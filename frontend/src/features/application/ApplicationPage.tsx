import { useState } from 'react'
import ApplicationForm from './ApplicationForm'

type View =
  | { name: 'start' }
  | { name: 'register' }
  | { name: 'detail'; applicationNumber: string }

/**
 * 申込管理のタブの中身（research.md R12）。表示を useState で切り替える（ルーターは使わない）。
 * 一覧と詳細の中身は、このあとの Phase で足す
 */
export default function ApplicationPage() {
  const [view, setView] = useState<View>({ name: 'start' })

  if (view.name === 'register') {
    return (
      <ApplicationForm
        onRegistered={(applicationNumber) =>
          setView({ name: 'detail', applicationNumber })
        }
      />
    )
  }
  if (view.name === 'detail') {
    return <h2>申込番号 {view.applicationNumber}</h2>
  }
  return (
    <button
      type="button"
      className="button-secondary"
      onClick={() => setView({ name: 'register' })}
    >
      新規登録
    </button>
  )
}
