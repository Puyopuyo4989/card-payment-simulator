import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import ApplicationDetail from './ApplicationDetail'
import { changeStatus, getDetail } from './api'
import type { ApplicationDetail as Detail } from './types'

vi.mock('./api', () => ({ getDetail: vi.fn(), changeStatus: vi.fn() }))

const getDetailMock = vi.mocked(getDetail)

// 値はすべて架空のもの
const rejected: Detail = {
  applicationNumber: 'A00000001',
  nameKanji: 'テスト 太郎',
  nameKana: 'テスト　タロウ',
  birthDate: '1990-04-01',
  annualIncome: 9999,
  employerName: 'テスト株式会社',
  phoneNumber: '0000000000',
  status: 'REJECTED',
  appliedAt: '2026-11-25T01:30:00Z',
  updatedAt: '2026-11-25T03:10:00Z',
  version: 2,
  history: [
    {
      changedAt: '2026-11-25T02:00:00Z',
      fromStatus: 'APPLIED',
      toStatus: 'UNDER_REVIEW',
      staffName: 'テスト 担当A',
      rejectionReason: null,
    },
    {
      changedAt: '2026-11-25T03:10:00Z',
      fromStatus: 'UNDER_REVIEW',
      toStatus: 'REJECTED',
      staffName: 'テスト 担当B',
      rejectionReason: 'テスト用の否決理由',
    },
  ],
}

beforeEach(() => {
  getDetailMock.mockReset()
  getDetailMock.mockResolvedValue({ kind: 'success', result: rejected })
  vi.mocked(changeStatus).mockReset()
})

/** 要約（dl）の「項目名 → 値」 */
function summaryOf(container: HTMLElement) {
  const list = container.querySelector('dl.summary') as HTMLElement
  return Object.fromEntries(
    within(list)
      .getAllByRole('term')
      .map((term) => [term.textContent, term.nextElementSibling?.textContent]),
  )
}

test('申込番号の詳細を読み、全項目・状態・日時（日本時間）を出す', async () => {
  const { container } = render(
    <ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />,
  )

  expect(
    await screen.findByRole('heading', { name: '申込番号 A00000001' }),
  ).toBeInTheDocument()
  expect(getDetailMock).toHaveBeenCalledWith('A00000001')
  expect(summaryOf(container)).toEqual({
    '氏名（漢字）': 'テスト 太郎',
    '氏名（カナ）': 'テスト　タロウ',
    生年月日: '1990-04-01',
    年収: '9,999万円',
    勤務先名: 'テスト株式会社',
    電話番号: '0000000000',
    状態: '否決',
    申込日時: '2026/11/25 10:30',
    更新日時: '2026/11/25 12:10',
  })
  expect(
    screen.getByText('否決', { selector: '.status-badge' }),
  ).toBeInTheDocument()
})

test('変更履歴を API の順（古い順）で表に出す', async () => {
  render(<ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />)

  const table = await screen.findByRole('table', { name: '変更履歴' })
  expect(
    within(table)
      .getAllByRole('columnheader')
      .map((header) => header.textContent),
  ).toEqual(['変更日時', '変更前 → 変更後', '担当者名', '否決の理由'])
  expect(
    within(table)
      .getAllByRole('row')
      .slice(1)
      .map((row) =>
        within(row)
          .getAllByRole('cell')
          .map((cell) => cell.textContent),
      ),
  ).toEqual([
    ['2026/11/25 11:00', '申込 → 審査中', 'テスト 担当A', ''],
    ['2026/11/25 12:10', '審査中 → 否決', 'テスト 担当B', 'テスト用の否決理由'],
  ])
})

test('別の申込に切り替えたあと、前の申込の古い応答で上書きしない', async () => {
  let resolveFirst: (
    value: Awaited<ReturnType<typeof getDetail>>,
  ) => void = () => {}
  getDetailMock.mockReturnValueOnce(
    new Promise((resolve) => {
      resolveFirst = resolve
    }),
  )
  getDetailMock.mockResolvedValueOnce({
    kind: 'success',
    result: { ...rejected, applicationNumber: 'A00000002' },
  })
  const { rerender } = render(
    <ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />,
  )
  rerender(<ApplicationDetail applicationNumber="A00000002" onBack={vi.fn()} />)
  await screen.findByRole('heading', { name: '申込番号 A00000002' })

  resolveFirst({ kind: 'success', result: rejected })

  await screen.findByRole('heading', { name: '申込番号 A00000002' })
  expect(
    screen.queryByRole('heading', { name: '申込番号 A00000001' }),
  ).not.toBeInTheDocument()
})

test('変更履歴がないときはメッセージを出す', async () => {
  getDetailMock.mockResolvedValue({
    kind: 'success',
    result: { ...rejected, status: 'APPLIED', history: [] },
  })
  render(<ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />)

  expect(await screen.findByText('変更履歴はありません')).toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('ない申込のときは「申込が見つかりません」と一覧に戻るボタンを出す', async () => {
  const user = userEvent.setup()
  const onBack = vi.fn()
  getDetailMock.mockResolvedValue({ kind: 'notFound' })
  render(<ApplicationDetail applicationNumber="A99999999" onBack={onBack} />)

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '申込が見つかりません',
  )
  await user.click(screen.getByRole('button', { name: '一覧に戻る' }))
  expect(onBack).toHaveBeenCalledTimes(1)
})

test('詳細を読み込めなかったときはメッセージを出す', async () => {
  getDetailMock.mockResolvedValue({ kind: 'unexpectedError' })
  render(<ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />)

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '詳細を読み込めませんでした。もう一度お試しください',
  )
  expect(screen.getByRole('button', { name: '一覧に戻る' })).toBeInTheDocument()
})

// 状態の変更とのつながり（US3。tasks.md T058）

const applied: Detail = {
  ...rejected,
  status: 'APPLIED',
  updatedAt: '2026-11-25T01:30:00Z',
  version: 0,
  history: [],
}

test('状態の変更が成功すると、返った詳細で状態・更新日時・履歴を置き換える', async () => {
  const user = userEvent.setup()
  getDetailMock.mockResolvedValue({ kind: 'success', result: applied })
  vi.mocked(changeStatus).mockResolvedValue({
    kind: 'success',
    result: {
      ...applied,
      status: 'UNDER_REVIEW',
      updatedAt: '2026-11-25T02:00:00Z',
      version: 1,
      history: [rejected.history[0]],
    },
  })
  const { container } = render(
    <ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />,
  )

  await user.type(await screen.findByLabelText('担当者名'), 'テスト 担当A')
  await user.click(screen.getByRole('button', { name: '審査中にする' }))

  expect(
    await screen.findByRole('button', { name: '決定する' }),
  ).toBeInTheDocument()
  expect(summaryOf(container)['状態']).toBe('審査中')
  expect(summaryOf(container)['更新日時']).toBe('2026/11/25 11:00')
  expect(
    within(screen.getByRole('table', { name: '変更履歴' }))
      .getAllByRole('row')
      .slice(1),
  ).toHaveLength(1)
  // 詳細を読み直さず、返った詳細を使う
  expect(getDetailMock).toHaveBeenCalledTimes(1)
})

test('ほかの担当者とぶつかったら、詳細を読み直して最新の状態と履歴を出し、メッセージは残す', async () => {
  const user = userEvent.setup()
  const underReview: Detail = {
    ...applied,
    status: 'UNDER_REVIEW',
    version: 1,
    history: [rejected.history[0]],
  }
  const approvedByOther: Detail = {
    ...underReview,
    status: 'APPROVED',
    updatedAt: '2026-11-25T03:00:00Z',
    version: 2,
    history: [
      rejected.history[0],
      {
        changedAt: '2026-11-25T03:00:00Z',
        fromStatus: 'UNDER_REVIEW',
        toStatus: 'APPROVED',
        staffName: 'テスト 担当C',
        rejectionReason: null,
      },
    ],
  }
  getDetailMock
    .mockResolvedValueOnce({ kind: 'success', result: underReview })
    .mockResolvedValueOnce({ kind: 'success', result: approvedByOther })
  vi.mocked(changeStatus).mockResolvedValue({
    kind: 'conflict',
    title: 'ほかの担当者が先に状態を変えました',
    detail: '最新の状態を確かめてから、もう一度操作してください',
  })
  const { container } = render(
    <ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />,
  )

  await user.type(await screen.findByLabelText('担当者名'), 'テスト 担当B')
  await user.click(screen.getByRole('radio', { name: '否決' }))
  await user.type(screen.getByLabelText('否決の理由'), 'テスト用の否決理由')
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(
    await screen.findByText('審査が終わった申込の状態は変えられません'),
  ).toBeInTheDocument()
  expect(getDetailMock).toHaveBeenCalledTimes(2)
  expect(summaryOf(container)['状態']).toBe('承認')
  expect(screen.getByText('テスト 担当C')).toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent(
    '最新の状態を確かめてから、もう一度操作してください',
  )
})

test('一覧に戻るボタンで一覧に戻る', async () => {
  const user = userEvent.setup()
  const onBack = vi.fn()
  render(<ApplicationDetail applicationNumber="A00000001" onBack={onBack} />)

  await screen.findByRole('heading', { name: '申込番号 A00000001' })
  await user.click(screen.getByRole('button', { name: '一覧に戻る' }))

  expect(onBack).toHaveBeenCalledTimes(1)
  expect(screen.getByRole('button', { name: '一覧に戻る' })).toHaveClass(
    'button-secondary',
  )
})

// 取り消せない決定のボタンにフォーカスがあると、続けて Enter を押しただけで承認・否決になる（T086）。
// 成功したら「○○に変更しました」にフォーカスを移す（T085）
test('「審査中にする」が成功したら「審査中に変更しました」にフォーカスを移し、承認・否決の操作にはフォーカスがない', async () => {
  const user = userEvent.setup()
  getDetailMock.mockResolvedValue({ kind: 'success', result: applied })
  vi.mocked(changeStatus).mockResolvedValue({
    kind: 'success',
    result: {
      ...applied,
      status: 'UNDER_REVIEW',
      version: 1,
      history: [rejected.history[0]],
    },
  })
  render(<ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />)

  await user.type(await screen.findByLabelText('担当者名'), 'テスト 担当A')
  await user.click(screen.getByRole('button', { name: '審査中にする' }))

  const decide = await screen.findByRole('button', { name: '決定する' })
  const status = screen.getByRole('status')
  expect(status).toHaveTextContent('審査中に変更しました')
  expect(status).toHaveFocus()
  expect(decide).not.toHaveFocus()
  expect(screen.getByRole('radio', { name: '承認' })).not.toHaveFocus()
  expect(screen.getByRole('radio', { name: '否決' })).not.toHaveFocus()
})

test('読み込みが終わるまでは「読み込み中です」を出し、終わったら消す', async () => {
  let resolve: (value: Awaited<ReturnType<typeof getDetail>>) => void = () => {}
  getDetailMock.mockReturnValue(
    new Promise((r) => {
      resolve = r
    }),
  )
  render(<ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />)

  expect(screen.getByRole('status')).toHaveTextContent('読み込み中です')
  expect(screen.getByRole('button', { name: '一覧に戻る' })).toBeInTheDocument()

  resolve({ kind: 'success', result: rejected })

  expect(
    await screen.findByRole('heading', { name: '申込番号 A00000001' }),
  ).toBeInTheDocument()
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

test('ぶつかったあとの読み直しに失敗しても、今の詳細と 409 のメッセージを残し、読み直せなかったことを知らせる', async () => {
  const user = userEvent.setup()
  const underReview: Detail = {
    ...applied,
    status: 'UNDER_REVIEW',
    version: 1,
    history: [rejected.history[0]],
  }
  getDetailMock
    .mockResolvedValueOnce({ kind: 'success', result: underReview })
    .mockResolvedValueOnce({ kind: 'unexpectedError' })
  vi.mocked(changeStatus).mockResolvedValue({
    kind: 'conflict',
    title: 'ほかの担当者が先に状態を変えました',
    detail: '最新の状態を確かめてから、もう一度操作してください',
  })
  const { container } = render(
    <ApplicationDetail applicationNumber="A00000001" onBack={vi.fn()} />,
  )

  await user.type(await screen.findByLabelText('担当者名'), 'テスト 担当B')
  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(
    await screen.findByText(
      '最新の状態を読み込めませんでした。一覧に戻って、開き直してください',
    ),
  ).toBeInTheDocument()
  expect(getDetailMock).toHaveBeenCalledTimes(2)
  expect(
    screen.queryByText('詳細を読み込めませんでした。もう一度お試しください'),
  ).not.toBeInTheDocument()
  expect(
    screen.getByRole('heading', { name: '申込番号 A00000001' }),
  ).toBeInTheDocument()
  expect(summaryOf(container)['状態']).toBe('審査中')
  expect(
    screen.getByText('最新の状態を確かめてから、もう一度操作してください', {
      exact: false,
    }),
  ).toBeInTheDocument()
  expect(screen.getByLabelText('担当者名')).toHaveValue('テスト 担当B')

  // そのあと変更が成功したら、読み直せなかったことの知らせは消す
  vi.mocked(changeStatus).mockResolvedValue({
    kind: 'success',
    result: { ...underReview, status: 'APPROVED', version: 2 },
  })
  // 選んだ「承認」は残っているので、そのまま決定する
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(
    await screen.findByText('審査が終わった申込の状態は変えられません'),
  ).toBeInTheDocument()
  expect(
    screen.queryByText(
      '最新の状態を読み込めませんでした。一覧に戻って、開き直してください',
    ),
  ).not.toBeInTheDocument()
})
