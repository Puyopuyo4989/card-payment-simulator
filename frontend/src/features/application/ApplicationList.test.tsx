import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import ApplicationList from './ApplicationList'
import { list } from './api'
import type { ApiOutcome, ApplicationList as ListResult } from './types'

vi.mock('./api', () => ({ list: vi.fn() }))

const listMock = vi.mocked(list)

// 値はすべて架空のもの。API が返す順（新しい順）のまま
const items: ListResult['items'] = [
  {
    applicationNumber: 'A00000003',
    nameKanji: '見本 花子',
    status: 'APPLIED',
    appliedAt: '2026-11-25T02:00:00Z',
  },
  {
    applicationNumber: 'A00000002',
    nameKanji: 'テスト 次郎',
    status: 'UNDER_REVIEW',
    appliedAt: '2026-11-24T15:00:00Z',
  },
]

function success(
  result: ListResult['items'],
): ApiOutcome<ListResult, 'status'> {
  return { kind: 'success', result: { items: result } }
}

beforeEach(() => {
  listMock.mockReset()
  listMock.mockResolvedValue(success(items))
})

test('表の列は申込番号・氏名（漢字）・状態・申込日時で、API の順のまま並べる', async () => {
  render(<ApplicationList onSelect={vi.fn()} />)

  const table = await screen.findByRole('table')
  expect(
    within(table)
      .getAllByRole('columnheader')
      .map((header) => header.textContent),
  ).toEqual(['申込番号', '氏名（漢字）', '状態', '申込日時'])
  const rows = within(table).getAllByRole('row').slice(1)
  expect(
    rows.map((row) =>
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    ),
  ).toEqual([
    ['A00000003', '見本 花子', '申込', '2026/11/25 11:00'],
    ['A00000002', 'テスト 次郎', '審査中', '2026/11/25 00:00'],
  ])
  expect(within(rows[0]).getByText('申込')).toHaveClass('status-badge')
})

test('状態の選択肢は「すべて」と4つの状態で、最初は「すべて」', async () => {
  render(<ApplicationList onSelect={vi.fn()} />)

  const select = screen.getByRole('combobox', { name: '状態' })
  expect(
    within(select)
      .getAllByRole('option')
      .map((option) => option.textContent),
  ).toEqual(['すべて', '申込', '審査中', '承認', '否決'])
  expect(select).toHaveValue('')
  await screen.findByRole('table')
  expect(listMock).toHaveBeenCalledTimes(1)
  // 状態（1つ目の引数）だけを確かめる。2つ目は Should のキーワードとページ（ApplicationListSearch.test.tsx）
  expect(listMock.mock.lastCall?.[0]).toBe('')
})

test('状態を選ぶと絞り込んで呼び直し、「すべて」に戻すと状態なしで呼ぶ', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  listMock.mockResolvedValue(success([items[1]]))
  await user.selectOptions(
    screen.getByRole('combobox', { name: '状態' }),
    '審査中',
  )

  expect(listMock.mock.lastCall?.[0]).toBe('UNDER_REVIEW')
  expect(await screen.findAllByRole('row')).toHaveLength(2)
  expect(screen.queryByText('A00000003')).not.toBeInTheDocument()

  listMock.mockResolvedValue(success(items))
  await user.selectOptions(
    screen.getByRole('combobox', { name: '状態' }),
    'すべて',
  )

  expect(listMock.mock.lastCall?.[0]).toBe('')
  expect(await screen.findByText('A00000003')).toBeInTheDocument()
})

test('絞り込みを続けて変えたとき、古い応答で表を上書きしない', async () => {
  const user = userEvent.setup()
  render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')

  let resolveSlow: (value: ApiOutcome<ListResult, 'status'>) => void = () => {}
  listMock.mockReturnValueOnce(
    new Promise((resolve) => {
      resolveSlow = resolve
    }),
  )
  listMock.mockResolvedValueOnce(success([items[1]]))
  const select = screen.getByRole('combobox', { name: '状態' })
  await user.selectOptions(select, '申込')
  await user.selectOptions(select, '審査中')
  expect(await screen.findByText('A00000002')).toBeInTheDocument()

  resolveSlow(success([items[0]]))

  await screen.findByText('A00000002')
  expect(screen.queryByText('A00000003')).not.toBeInTheDocument()
})

test('該当する申込がないときはメッセージを出し、表は出さない', async () => {
  listMock.mockResolvedValue(success([]))
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(
    await screen.findByText('該当する申込はありません'),
  ).toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('一覧を読み込めなかったときはメッセージを出す', async () => {
  listMock.mockResolvedValue({ kind: 'unexpectedError' })
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '一覧を読み込めませんでした。もう一度お試しください',
  )
})

test('申込番号のボタンを押すと、その申込を選ぶ（キーボードでも）', async () => {
  const user = userEvent.setup()
  const onSelect = vi.fn()
  render(<ApplicationList onSelect={onSelect} />)

  await user.click(await screen.findByRole('button', { name: 'A00000003' }))
  expect(onSelect).toHaveBeenLastCalledWith('A00000003')

  screen.getByRole('button', { name: 'A00000002' }).focus()
  await user.keyboard('{Enter}')
  expect(onSelect).toHaveBeenLastCalledWith('A00000002')
})

test('一覧を開くたびに API を呼び、最新の状態を出す', async () => {
  const { unmount } = render(<ApplicationList onSelect={vi.fn()} />)
  await screen.findByRole('table')
  unmount()

  listMock.mockResolvedValue(success([{ ...items[1], status: 'APPROVED' }]))
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(await screen.findByText('承認')).toBeInTheDocument()
  expect(listMock).toHaveBeenCalledTimes(2)
})

test('読み込みが終わるまでは「読み込み中です」を出し、終わったら消す', async () => {
  let resolve: (value: ApiOutcome<ListResult, 'status'>) => void = () => {}
  listMock.mockReturnValue(
    new Promise((r) => {
      resolve = r
    }),
  )
  render(<ApplicationList onSelect={vi.fn()} />)

  expect(screen.getByRole('status')).toHaveTextContent('読み込み中です')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()

  resolve(success(items))

  expect(await screen.findByRole('table')).toBeInTheDocument()
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})
