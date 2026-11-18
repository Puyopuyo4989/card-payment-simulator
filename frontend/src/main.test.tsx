import { findByRole } from '@testing-library/react'
import { expect, test } from 'vitest'

test('main.tsx を読み込むと #root の中に見出しが表示される', async () => {
  const root = document.createElement('div')
  root.id = 'root'
  document.body.appendChild(root)

  await import('./main')

  expect(await findByRole(root, 'heading', { name: '分割払いシミュレーション' })).toBeInTheDocument()
})
