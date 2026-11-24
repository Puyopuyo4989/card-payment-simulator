import { useState } from 'react'
import InstallmentPage from './features/installment/InstallmentPage'

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
      <h1>カード支払シミュレーション</h1>
      <div role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`${tab.id}-tab`}
            aria-selected={tab.id === selected}
            aria-controls={`${tab.id}-panel`}
            onClick={() => setSelected(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div
        role="tabpanel"
        id={`${selected}-panel`}
        aria-labelledby={`${selected}-tab`}
      >
        {selected === 'installment' && <InstallmentPage />}
      </div>
    </>
  )
}
