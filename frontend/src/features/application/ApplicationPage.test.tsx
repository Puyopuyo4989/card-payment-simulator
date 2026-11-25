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
  // 登録が返した申込番号で、詳細を読み直す（tasks.md T050 の注記）
  expect(getDetail).toHaveBeenCalledWith('A00000001')
})

const summary = {
  applicationNumber: 'A00000001',
  nameKanji: 'テスト 太郎',
  status: 'APPLIED' as const,
  appliedAt: '2026-11-25T01:30:00Z',
}

test('最初は一覧を出す', async () => {
  vi.mocked(list).mockResolvedValue({
    kind: 'success',
    result: { items: [summary] },
  })
  render(<ApplicationPage />)

  expect(
    await screen.findByRole('button', { name: 'A00000001' }),
  ).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: '状態' })).toBeInTheDocument()
  expect(list).toHaveBeenCalledTimes(1)
})

test('一覧で選んだ申込の詳細を出し、一覧に戻ると一覧を読み直す', async () => {
  const user = userEvent.setup()
  vi.mocked(list).mockResolvedValue({
    kind: 'success',
    result: { items: [summary] },
  })
  render(<ApplicationPage />)

  await user.click(await screen.findByRole('button', { name: 'A00000001' }))

  expect(
    await screen.findByRole('heading', { name: '申込番号 A00000001' }),
  ).toBeInTheDocument()
  expect(getDetail).toHaveBeenCalledWith('A00000001')
  expect(
    screen.queryByRole('combobox', { name: '状態' }),
  ).not.toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: '一覧に戻る' }))

  expect(
    await screen.findByRole('combobox', { name: '状態' }),
  ).toBeInTheDocument()
  expect(list).toHaveBeenCalledTimes(2)
})

test('登録のフォームから、登録せずに一覧に戻れる', async () => {
  const user = userEvent.setup()
  render(<ApplicationPage />)

  await user.click(await screen.findByRole('button', { name: '新規登録' }))
  await user.click(screen.getByRole('button', { name: '一覧に戻る' }))

  expect(
    await screen.findByRole('combobox', { name: '状態' }),
  ).toBeInTheDocument()
  expect(register).not.toHaveBeenCalled()
})
