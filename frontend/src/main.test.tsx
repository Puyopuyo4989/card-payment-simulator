import { findByRole } from '@testing-library/react'
import { expect, test } from 'vitest'
import indexHtml from '../index.html?raw'

test('index.html のタイトルは、見出しと同じ「カード支払シミュレーション」', () => {
  const page = new DOMParser().parseFromString(indexHtml, 'text/html')

  expect(page.title).toBe('カード支払シミュレーション')
})

test('main.tsx を読み込むと #root の中に見出しが表示される', async () => {
  const root = document.createElement('div')
  root.id = 'root'
  document.body.appendChild(root)

  await import('./main')

  expect(
    await findByRole(root, 'heading', { name: 'カード支払シミュレーション' }),
  ).toBeInTheDocument()
})
