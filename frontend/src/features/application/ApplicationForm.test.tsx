import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import ApplicationForm from './ApplicationForm'
import { register } from './api'
import type { ApplicationDetail } from './types'

vi.mock('./api', () => ({ register: vi.fn() }))

const registerMock = vi.mocked(register)

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
  registerMock.mockReset()
})

const LABELS = [
  '氏名（漢字）',
  '氏名（カナ）',
  '生年月日',
  '年収（万円）',
  '勤務先名',
  '電話番号',
] as const

test('6項目の入力欄がラベルで探せる', () => {
  render(<ApplicationForm onRegistered={vi.fn()} />)

  for (const label of LABELS) {
    expect(screen.getByLabelText(label)).toBeInTheDocument()
  }
  expect(screen.getByLabelText('生年月日')).toHaveAttribute('type', 'date')
  expect(screen.getByLabelText('年収（万円）')).toHaveAttribute(
    'inputmode',
    'numeric',
  )
  expect(screen.getByLabelText('電話番号')).toHaveAttribute(
    'inputmode',
    'numeric',
  )
})

test('ダミーの情報だけを入力するよう注意書きがある', () => {
  render(<ApplicationForm onRegistered={vi.fn()} />)

  expect(
    screen.getByText(
      'ダミーの情報だけを入力してください。本物の氏名・電話番号などは入力しないでください',
    ),
  ).toHaveClass('notice')
})

test('カード番号の入力欄はない', () => {
  render(<ApplicationForm onRegistered={vi.fn()} />)

  expect(screen.queryByLabelText(/カード/)).not.toBeInTheDocument()
  // 入力欄は6つだけ
  expect(document.querySelectorAll('input, select, textarea')).toHaveLength(6)
})

test('入力の例には実在しない値だけを使う', () => {
  render(<ApplicationForm onRegistered={vi.fn()} />)

  expect(screen.getByLabelText('氏名（漢字）')).toHaveAttribute(
    'placeholder',
    '例：見本 花子',
  )
  expect(screen.getByLabelText('氏名（カナ）')).toHaveAttribute(
    'placeholder',
    '例：ミホン　ハナコ',
  )
  expect(screen.getByLabelText('電話番号')).toHaveAttribute(
    'placeholder',
    '例：0000000000',
  )
})

async function fillAll(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('氏名（漢字）'), 'テスト 太郎')
  await user.type(screen.getByLabelText('氏名（カナ）'), 'テスト　タロウ')
  await user.type(screen.getByLabelText('生年月日'), '1990-04-01')
  await user.type(screen.getByLabelText('年収（万円）'), '450')
  await user.type(screen.getByLabelText('勤務先名'), 'テスト株式会社')
  await user.type(screen.getByLabelText('電話番号'), '0000000000')
}

test('入力したままの文字列で登録し、成功したら申込番号を伝える', async () => {
  const user = userEvent.setup()
  const onRegistered = vi.fn()
  registerMock.mockResolvedValue({ kind: 'success', result: registered })
  render(<ApplicationForm onRegistered={onRegistered} />)

  await fillAll(user)
  await user.click(screen.getByRole('button', { name: '登録する' }))

  expect(registerMock).toHaveBeenCalledWith({
    nameKanji: 'テスト 太郎',
    nameKana: 'テスト　タロウ',
    birthDate: '1990-04-01',
    annualIncome: '450',
    employerName: 'テスト株式会社',
    phoneNumber: '0000000000',
  })
  expect(onRegistered).toHaveBeenCalledWith('A00000001')
})

test('入力エラーのとき、各項目の入力欄のすぐ下にメッセージを出す', async () => {
  const user = userEvent.setup()
  const onRegistered = vi.fn()
  registerMock.mockResolvedValue({
    kind: 'inputError',
    errors: [
      { field: 'nameKanji', message: '氏名（漢字）を入力してください' },
      {
        field: 'annualIncome',
        message: '年収は0万円以上9,999万円以下で入力してください',
      },
    ],
  })
  render(<ApplicationForm onRegistered={onRegistered} />)

  await user.click(screen.getByRole('button', { name: '登録する' }))

  const alerts = await screen.findAllByRole('alert')
  expect(alerts.map((alert) => alert.textContent)).toEqual([
    '氏名（漢字）を入力してください',
    '年収は0万円以上9,999万円以下で入力してください',
  ])
  expect(screen.getByLabelText('氏名（漢字）')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
  expect(screen.getByLabelText('氏名（漢字）')).toHaveAccessibleDescription(
    '氏名（漢字）を入力してください',
  )
  expect(screen.getByLabelText('年収（万円）')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
  expect(screen.getByLabelText('氏名（カナ）')).not.toHaveAttribute(
    'aria-invalid',
  )
  expect(onRegistered).not.toHaveBeenCalled()
})

test('登録の実行中はボタンを押せない', async () => {
  const user = userEvent.setup()
  registerMock.mockReturnValue(new Promise(() => {}))
  render(<ApplicationForm onRegistered={vi.fn()} />)

  await user.click(screen.getByRole('button', { name: '登録する' }))

  expect(screen.getByRole('button', { name: '登録する' })).toBeDisabled()
})

test('もう一度登録するとき、前のエラーを消す', async () => {
  const user = userEvent.setup()
  registerMock.mockResolvedValueOnce({
    kind: 'inputError',
    errors: [{ field: 'nameKanji', message: '氏名（漢字）を入力してください' }],
  })
  registerMock.mockReturnValueOnce(new Promise(() => {}))
  render(<ApplicationForm onRegistered={vi.fn()} />)

  await user.click(screen.getByRole('button', { name: '登録する' }))
  await screen.findByRole('alert')
  await user.click(screen.getByRole('button', { name: '登録する' }))

  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('予期しないエラーのとき、メッセージを出す', async () => {
  const user = userEvent.setup()
  const onRegistered = vi.fn()
  registerMock.mockResolvedValue({ kind: 'unexpectedError' })
  render(<ApplicationForm onRegistered={onRegistered} />)

  await user.click(screen.getByRole('button', { name: '登録する' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '登録できませんでした。もう一度お試しください',
  )
  expect(onRegistered).not.toHaveBeenCalled()
})
