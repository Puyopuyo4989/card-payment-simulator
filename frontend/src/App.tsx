import { useState } from 'react'
import InstallmentPage from './features/installment/InstallmentPage'
import RevolvingPage from './features/revolving/RevolvingPage'

const TABS = [
  { id: 'installment', label: '分割払い' },
  { id: 'revolving', label: 'リボ払い' },
] as const

type TabId = (typeof TABS)[number]['id']

/**
 * 見出しと、分割払い・リボ払いを切り替えるタブ（002 の research.md R7）。ルーターは使わない。
 * 選んでいないほうは描画しないので、切り替えると入力と結果は消える
 */
export default function App() {
  const [selected, setSelected] = useState<TabId>('installment')

  return (
    <>
      <header className="app-header">
        <div className="app-header-inner">
          <Logo />
          <h1 className="app-title">カード支払シミュレーション</h1>
        </div>
      </header>
      <main className="app-main">
        <div role="tablist" className="tabs">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`${tab.id}-tab`}
              aria-selected={tab.id === selected}
              aria-controls={`${tab.id}-panel`}
              className={tab.id === selected ? 'tab-selected' : undefined}
              onClick={() => setSelected(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div
          role="tabpanel"
          className="panel"
          id={`${selected}-panel`}
          aria-labelledby={`${selected}-tab`}
        >
          {selected === 'installment' && <InstallmentPage />}
          {selected === 'revolving' && <RevolvingPage />}
        </div>
      </main>
    </>
  )
}

/** ロゴ（このアプリのために作った図。カードと、上に伸びる若葉）。飾りなので、スクリーンリーダーには読ませない */
function Logo() {
  return (
    <svg className="app-logo" viewBox="0 0 40 40" aria-hidden="true">
      <rect width="40" height="40" rx="12" fill="var(--color-brand)" />
      <rect
        x="8"
        y="15"
        width="24"
        height="16"
        rx="3"
        fill="var(--color-on-primary)"
      />
      <rect
        x="8"
        y="19"
        width="24"
        height="3"
        fill="var(--color-primary-soft)"
      />
      <path
        d="M20 15 C20 11 22 8 27 7 C27 11 24 14 20 15 Z"
        fill="var(--color-on-primary)"
      />
    </svg>
  )
}
