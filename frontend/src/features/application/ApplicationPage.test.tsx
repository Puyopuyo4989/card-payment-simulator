import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import ApplicationPage from './ApplicationPage'
import { getDetail, list, register } from './api'
import type { ApplicationDetail } from './types'

vi.mock('./api', () => ({
  register: vi.fn(),
  list: vi.fn(),
  getDetail: vi.fn(),
  changeStatus: vi.fn(),
}))

// 値はすべて架空のもの
const registered: ApplicationDetail = {
  applicationNumber: 'A00000001',
  nameKanji: 'テスト 太郎',
  nameKana: 'テスト　タロウ',
  birthDate: '1990-04-01',
  annualIncome: 450,
  employerName: 'テスト株式会社',
  phoneNumber: '0000000000',
  status: 'APPLIED',
  appliedAt: '2026-11-25T01:30:00Z',
  updatedAt: '2026-11-25T01:30:00Z',
  version: 0,
  history: [],
}

beforeEach(() => {
  vi.mocked(register).mockReset()
  vi.mocked(list).mockReset()
  vi.mocked(list).mockResolvedValue({
    kind: 'success',
    result: { items: [] },
  })
  vi.mocked(getDetail).mockReset()
  vi.mocked(getDetail).mockResolvedValue({
    kind: 'success',
    result: registered,
  })
})

test('「新規登録」を押すと登録のフォームが出る', async () => {
  const user = userEvent.setup()
  render(<ApplicationPage />)

  await user.click(await screen.findByRole('button', { name: '新規登録' }))

  expect(screen.getByLabelText('氏名（漢字）')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument()
})

test('登録が成功すると、その申込の詳細が出る', async () => {
  const user = userEvent.setup()
  vi.mocked(register).mockResolvedValue({
    kind: 'success',
    result: registered,
  })
  render(<ApplicationPage />)

  await user.click(await screen.findByRole('button', { name: '新規登録' }))
  await user.click(screen.getByRole('button', { name: '登録する' }))

  expect(
    await screen.findByRole('heading', { name: '申込番号 A00000001' }),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: '登録する' }),
  ).not.toBeInTheDocument()
})
