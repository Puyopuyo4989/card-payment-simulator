// 【Should】一覧のキーワード検索とページ分け（US4、contracts 6）。値はすべて架空のもの
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import ApplicationList from './ApplicationList'
import { list } from './api'
import type { ApiOutcome, ApplicationList as ListResult } from './types'

vi.mock('./api', () => ({ list: vi.fn() }))

const listMock = vi.mocked(list)

const item: ListResult['items'][number] = {
  applicationNumber: 'A00000001',
  nameKanji: 'テスト 太郎',
  status: 'APPLIED',
  appliedAt: '2026-11-25T01:30:00Z',
}

function page(
  current: number,
  totalPages: number,
  items: ListResult['items'] = [item],
): ApiOutcome<ListResult, 'status'> {
  return {
    kind: 'success',
    result: { items, page: current, totalPages, totalItems: totalPages * 20 },
  }
}

beforeEach(() => {
  listMock.mockReset()
  listMock.mockResolvedValue(page(1, 2))
})

test('最初はキーワードなしの1ページ目を読む', async () => {
  render(<ApplicationList onSelect={vi.fn()} />)

  await screen.findByRole('table')
  expect(listMock).toHaveBeenCalledTimes(1)
  expect(listMock).toHaveBeenLastCalledWith('', { keyword: '', page: 1 })
})

test('キーワードを入れて「検索」を押すと、状態と組み合わせて1ページ目を読む', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  await user.selectOptions(
    screen.getByRole('combobox', { name: '状態' }),
    '申込',
  )
  await user.type(screen.getByRole('textbox', { name: 'キーワード' }), 'タロウ')
  // 入力しただけでは読み直さない
  expect(listMock).toHaveBeenLastCalledWith('APPLIED', {
    keyword: '',
    page: 1,
  })
  await user.click(screen.getByRole('button', { name: '検索' }))

  expect(listMock).toHaveBeenLastCalledWith('APPLIED', {
    keyword: 'タロウ',
    page: 1,
  })
})

test('キーワードの欄で Enter を押しても検索する', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  await user.type(
    screen.getByRole('textbox', { name: 'キーワード' }),
    '00002{Enter}',
  )

  expect(listMock).toHaveBeenLastCalledWith('', { keyword: '00002', page: 1 })
})

test('「n / m ページ」を出し、最初のページでは「前へ」を押せない', async () => {
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(await screen.findByText('1 / 2 ページ')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '前へ' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '次へ' })).toBeEnabled()
})

test('「次へ」で次のページを読み、最後のページでは「次へ」を押せない', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByText('1 / 2 ページ')

  listMock.mockResolvedValue(page(2, 2))
  await user.click(screen.getByRole('button', { name: '次へ' }))

  expect(listMock).toHaveBeenLastCalledWith('', { keyword: '', page: 2 })
  expect(await screen.findByText('2 / 2 ページ')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '前へ' })).toBeEnabled()

  listMock.mockResolvedValue(page(1, 2))
  await user.click(screen.getByRole('button', { name: '前へ' }))

  expect(listMock).toHaveBeenLastCalledWith('', { keyword: '', page: 1 })
  expect(await screen.findByText('1 / 2 ページ')).toBeInTheDocument()
})

test('2ページ目で検索し直すと、1ページ目に戻る', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByText('1 / 2 ページ')
  listMock.mockResolvedValue(page(2, 2))
  await user.click(screen.getByRole('button', { name: '次へ' }))
  await screen.findByText('2 / 2 ページ')

  listMock.mockResolvedValue(page(1, 1))
  await user.type(screen.getByRole('textbox', { name: 'キーワード' }), '花子')
  await user.click(screen.getByRole('button', { name: '検索' }))

  expect(listMock).toHaveBeenLastCalledWith('', { keyword: '花子', page: 1 })
})

test('2ページ目で状態を変えると、検索したキーワードのまま1ページ目に戻る', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByText('1 / 2 ページ')
  await user.type(
    screen.getByRole('textbox', { name: 'キーワード' }),
    'タロウ{Enter}',
  )
  await user.click(await screen.findByRole('button', { name: '次へ' }))
  expect(listMock).toHaveBeenLastCalledWith('', { keyword: 'タロウ', page: 2 })

  await user.selectOptions(
    screen.getByRole('combobox', { name: '状態' }),
    '審査中',
  )

  expect(listMock).toHaveBeenLastCalledWith('UNDER_REVIEW', {
    keyword: 'タロウ',
    page: 1,
  })
})

test('該当なしのときは「該当する申込はありません」を出し、ページの切り替えは出さない', async () => {
  listMock.mockResolvedValue(page(1, 0, []))
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(
    await screen.findByText('該当する申込はありません'),
  ).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '次へ' })).not.toBeInTheDocument()
  expect(screen.queryByText(/ページ$/)).not.toBeInTheDocument()
})

const KEYWORD_ERROR = 'キーワードは50文字以内で入力してください'
const PAGE_ERROR = 'ページは1以上の半角数字で指定してください'

function inputError(
  ...fields: ('keyword' | 'page')[]
): ApiOutcome<ListResult, 'status' | 'keyword' | 'page'> {
  return {
    kind: 'inputError',
    errors: fields.map((field) => ({
      field,
      message: field === 'keyword' ? KEYWORD_ERROR : PAGE_ERROR,
    })),
  }
}

test('キーワードの誤りは、キーワードの欄の下に赤字で出し、前の表は消さない', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  listMock.mockResolvedValue(inputError('keyword'))
  const input = screen.getByRole('textbox', { name: 'キーワード' })
  await user.type(input, `${'ア'.repeat(51)}{Enter}`)

  expect(await screen.findByRole('alert')).toHaveTextContent(KEYWORD_ERROR)
  expect(input).toHaveAttribute('aria-invalid', 'true')
  expect(input).toHaveAccessibleDescription(KEYWORD_ERROR)
  // 予期しないエラーにはしない。前の表とページの切り替えは残す
  expect(
    screen.queryByText('一覧を読み込めませんでした。もう一度お試しください'),
  ).not.toBeInTheDocument()
  expect(screen.getByRole('table')).toBeInTheDocument()
  expect(screen.getByText('1 / 2 ページ')).toBeInTheDocument()
})

test('ページの誤りは、ページの切り替えの下に赤字で出し、前の表は消さない', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  listMock.mockResolvedValue(inputError('page'))
  await user.click(screen.getByRole('button', { name: '次へ' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(PAGE_ERROR)
  expect(
    screen.getByRole('navigation', { name: 'ページ' }),
  ).toHaveAccessibleDescription(PAGE_ERROR)
  expect(screen.getByRole('table')).toBeInTheDocument()
})

test('キーワードとページの誤りが同時に来たら、両方を画面の順に出す', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  listMock.mockResolvedValue(inputError('page', 'keyword'))
  await user.click(screen.getByRole('button', { name: '次へ' }))

  const alerts = await screen.findAllByRole('alert')
  expect(alerts.map((alert) => alert.textContent)).toEqual([
    KEYWORD_ERROR,
    PAGE_ERROR,
  ])
})

test('誤りのあとに検索し直して読めたら、誤りを消す', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')
  listMock.mockResolvedValue(inputError('keyword'))
  const input = screen.getByRole('textbox', { name: 'キーワード' })
  await user.type(input, `${'ア'.repeat(51)}{Enter}`)
  await screen.findByRole('alert')

  listMock.mockResolvedValue(page(1, 1))
  await user.clear(input)
  await user.type(input, 'タロウ{Enter}')

  expect(await screen.findByText('1 / 1 ページ')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(input).not.toHaveAttribute('aria-invalid')
})

test('最初の読み込みで入力の誤りが来たときは、誤りだけを出す（表はない）', async () => {
  listMock.mockResolvedValue(inputError('keyword'))
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(await screen.findByRole('alert')).toHaveTextContent(KEYWORD_ERROR)
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(screen.queryByText('読み込み中です')).not.toBeInTheDocument()
})
