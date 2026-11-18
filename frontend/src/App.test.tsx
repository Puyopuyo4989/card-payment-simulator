import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App'

test('見出し「分割払いシミュレーション」が表示される', () => {
  render(<App />)

  expect(screen.getByRole('heading', { name: '分割払いシミューション' })).toBeInTheDocument()
})
