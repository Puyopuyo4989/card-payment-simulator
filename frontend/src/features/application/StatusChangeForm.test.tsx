import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import StatusChangeForm from './StatusChangeForm'
import { changeStatus } from './api'
import type { ApplicationDetail, ApplicationStatus } from './types'

vi.mock('./api', () => ({ changeStatus: vi.fn() }))

const changeStatusMock = vi.mocked(changeStatus)

// 値はすべて架空のもの
function detailIn(status: ApplicationStatus, version = 1): ApplicationDetail {
  return {
    applicationNumber: 'A00000001',
    nameKanji: 'テスト 太郎',
    nameKana: 'テスト　タロウ',
    birthDate: '1990-04-01',
    annualIncome: 450,
    employerName: 'テスト株式会社',
    phoneNumber: '0000000000',
    status,
    appliedAt: '2026-11-25T01:30:00Z',
    updatedAt: '2026-11-25T02:00:00Z',
    version,
    history: [],
  }
}

function renderForm(
  status: ApplicationStatus,
  {
    onChanged = vi.fn(),
    onConflict = vi.fn(),
    version = 1,
  }: { onChanged?: () => void; onConflict?: () => void; version?: number } = {},
) {
  return render(
    <StatusChangeForm
      detail={detailIn(status, version)}
      onChanged={onChanged}
      onConflict={onConflict}
    />,
  )
}

const NO_DECISION = '承認か否決を選んでください'

beforeEach(() => {
  changeStatusMock.mockReset()
})

// 申込のとき（「審査中にする」は取り消せる段階なので、1回で変える）

test('申込のときは「審査中にする」だけを出し、承認・否決の選択と否決の理由の欄は出さない', () => {
  renderForm('APPLIED')

  expect(
    screen.getAllByRole('button').map((button) => button.textContent),
  ).toEqual(['審査中にする'])
  expect(screen.getByLabelText('担当者名')).toBeInTheDocument()
  expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('否決の理由')).not.toBeInTheDocument()
})

test('「審査中にする」は否決の理由を空で送る', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockReturnValue(new Promise(() => {}))
  renderForm('APPLIED', { version: 0 })

  await user.type(screen.getByLabelText('担当者名'), 'テスト 担当A')
  await user.click(screen.getByRole('button', { name: '審査中にする' }))

  expect(changeStatusMock).toHaveBeenCalledWith('A00000001', {
    toStatus: 'UNDER_REVIEW',
    staffName: 'テスト 担当A',
    rejectionReason: '',
    version: '0',
  })
})

// 審査中のとき（T081：取り消せない承認・否決は、選んでから「決定する」の2段階）

test('審査中のときは「承認」「否決」を選ぶ欄と「決定する」だけを出し、最初はどちらも選ばない', () => {
  renderForm('UNDER_REVIEW')

  const group = screen.getByRole('group', { name: '審査の結果' })
  expect(group).toBeInTheDocument()
  expect(
    screen.getAllByRole('radio').map((radio) => radio.getAttribute('value')),
  ).toEqual(['APPROVED', 'REJECTED'])
  expect(screen.getByRole('radio', { name: '承認' })).not.toBeChecked()
  expect(screen.getByRole('radio', { name: '否決' })).not.toBeChecked()
  expect(
    screen.getAllByRole('button').map((button) => button.textContent),
  ).toEqual(['決定する'])
  expect(screen.getByLabelText('担当者名')).toBeInTheDocument()
})

test('否決の理由の欄は「否決」を選んだときだけ出し、複数行の入力欄（textarea）', async () => {
  const user = userEvent.setup()
  renderForm('UNDER_REVIEW')

  expect(screen.queryByLabelText('否決の理由')).not.toBeInTheDocument()
  await user.click(screen.getByRole('radio', { name: '承認' }))
  expect(screen.queryByLabelText('否決の理由')).not.toBeInTheDocument()

  await user.click(screen.getByRole('radio', { name: '否決' }))

  const reason = screen.getByRole('textbox', { name: '否決の理由' })
  expect(reason.tagName).toBe('TEXTAREA')
})

test('選ぶだけでは送らない', async () => {
  const user = userEvent.setup()
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('radio', { name: '否決' }))

  expect(changeStatusMock).not.toHaveBeenCalled()
})

test('何も選ばずに「決定する」を押すと、選ぶ欄の下にエラーを出し、送らない', async () => {
  const user = userEvent.setup()
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('button', { name: '決定する' }))

  const group = screen.getByRole('group', { name: '審査の結果' })
  const alert = screen.getByRole('alert')
  expect(alert).toHaveTextContent(NO_DECISION)
  expect(group).toContainElement(alert)
  expect(group).toHaveAccessibleDescription(NO_DECISION)
  expect(changeStatusMock).not.toHaveBeenCalled()
})

test('選んだらエラーを消す', async () => {
  const user = userEvent.setup()
  renderForm('UNDER_REVIEW')
  await user.click(screen.getByRole('button', { name: '決定する' }))

  await user.click(screen.getByRole('radio', { name: '承認' }))

  expect(screen.queryByText(NO_DECISION)).not.toBeInTheDocument()
})

test('「否決」を選んで「決定する」を押すと、入力のままの値と、詳細の版を文字列にして送る', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockReturnValue(new Promise(() => {}))
  renderForm('UNDER_REVIEW', { version: 3 })

  await user.type(screen.getByLabelText('担当者名'), ' テスト 担当B ')
  await user.click(screen.getByRole('radio', { name: '否決' }))
  await user.type(screen.getByLabelText('否決の理由'), 'テスト用の否決理由')
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(changeStatusMock).toHaveBeenCalledWith('A00000001', {
    toStatus: 'REJECTED',
    staffName: ' テスト 担当B ',
    rejectionReason: 'テスト用の否決理由',
    version: '3',
  })
})

test('理由を入れてから「承認」に選び直したときは、見えていない理由を送らない', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockReturnValue(new Promise(() => {}))
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('radio', { name: '否決' }))
  await user.type(screen.getByLabelText('否決の理由'), 'テスト用の否決理由')
  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(changeStatusMock).toHaveBeenCalledWith(
    'A00000001',
    expect.objectContaining({ toStatus: 'APPROVED', rejectionReason: '' }),
  )
})

// 審査が終わったとき

test.each(['APPROVED', 'REJECTED'] as const)(
  '%s のときはフォームの代わりにメッセージを出す',
  (status) => {
    renderForm(status)

    expect(
      screen.getByText('審査が終わった申込の状態は変えられません'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('担当者名')).not.toBeInTheDocument()
  },
)

// API の結果

test('入力エラーのとき、担当者名と否決の理由の欄の下にメッセージを出す', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockResolvedValue({
    kind: 'inputError',
    errors: [
      { field: 'staffName', message: '担当者名を入力してください' },
      { field: 'rejectionReason', message: '否決の理由を入力してください' },
    ],
  })
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('radio', { name: '否決' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(
    (await screen.findAllByRole('alert')).map((alert) => alert.textContent),
  ).toEqual(['担当者名を入力してください', '否決の理由を入力してください'])
  expect(screen.getByLabelText('担当者名')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
  expect(screen.getByLabelText('担当者名')).toHaveAccessibleDescription(
    '担当者名を入力してください',
  )
  expect(screen.getByLabelText('否決の理由')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
})

test('変更後の状態や版のエラーは、フォームの上に出す', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockResolvedValue({
    kind: 'inputError',
    errors: [
      {
        field: 'toStatus',
        message: '承認・否決の前に、先に「審査中」にしてください',
      },
      {
        field: 'version',
        message: '画面を開き直してから、もう一度操作してください',
      },
    ],
  })
  renderForm('APPLIED')

  await user.click(screen.getByRole('button', { name: '審査中にする' }))

  expect(
    (await screen.findAllByRole('alert')).map((alert) => alert.textContent),
  ).toEqual([
    '承認・否決の前に、先に「審査中」にしてください',
    '画面を開き直してから、もう一度操作してください',
  ])
})

test('ほかの担当者とぶつかったとき、title と detail を出して知らせ、担当者名と選んだ結果は残す', async () => {
  const user = userEvent.setup()
  const onConflict = vi.fn()
  changeStatusMock.mockResolvedValue({
    kind: 'conflict',
    title: 'ほかの担当者が先に状態を変えました',
    detail: '最新の状態を確かめてから、もう一度操作してください',
  })
  renderForm('UNDER_REVIEW', { onConflict })

  await user.type(screen.getByLabelText('担当者名'), 'テスト 担当B')
  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('ほかの担当者が先に状態を変えました')
  expect(alert).toHaveTextContent(
    '最新の状態を確かめてから、もう一度操作してください',
  )
  expect(onConflict).toHaveBeenCalledTimes(1)
  expect(screen.getByLabelText('担当者名')).toHaveValue('テスト 担当B')
  expect(screen.getByRole('radio', { name: '承認' })).toBeChecked()
})

test('予期しないエラーのとき、メッセージを出す', async () => {
  const user = userEvent.setup()
  const onChanged = vi.fn()
  changeStatusMock.mockResolvedValue({ kind: 'unexpectedError' })
  renderForm('APPLIED', { onChanged })

  await user.click(screen.getByRole('button', { name: '審査中にする' }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    '状態を変えられませんでした。もう一度お試しください',
  )
  expect(onChanged).not.toHaveBeenCalled()
})

test('成功したら変更後の詳細を渡し、担当者名・選んだ結果・否決の理由を空にする', async () => {
  const user = userEvent.setup()
  const onChanged = vi.fn()
  const changed = detailIn('REJECTED', 2)
  changeStatusMock.mockResolvedValue({ kind: 'success', result: changed })
  const { rerender } = renderForm('UNDER_REVIEW', { onChanged })

  await user.type(screen.getByLabelText('担当者名'), 'テスト 担当B')
  await user.click(screen.getByRole('radio', { name: '否決' }))
  await user.type(screen.getByLabelText('否決の理由'), 'テスト用の否決理由')
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(onChanged).toHaveBeenCalledWith(changed)
  expect(screen.getByLabelText('担当者名')).toHaveValue('')
  expect(screen.getByRole('radio', { name: '否決' })).not.toBeChecked()
  // 選び直したときも、前の理由は残っていない
  await user.click(screen.getByRole('radio', { name: '否決' }))
  expect(screen.getByLabelText('否決の理由')).toHaveValue('')
  rerender(
    <StatusChangeForm
      detail={changed}
      onChanged={onChanged}
      onConflict={vi.fn()}
    />,
  )
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

// T085：成功したら「○○に変更しました」を出し、そこにフォーカスを移す
test.each([
  ['APPLIED', '審査中にする', null, 'UNDER_REVIEW', '審査中に変更しました'],
  ['UNDER_REVIEW', '決定する', '承認', 'APPROVED', '承認に変更しました'],
  ['UNDER_REVIEW', '決定する', '否決', 'REJECTED', '否決に変更しました'],
] as const)(
  '%s から「%s」（%s）が成功したら、role="status" で知らせ、そこにフォーカスを移す',
  async (from, button, decision, to, message) => {
    const user = userEvent.setup()
    const changed = detailIn(to, 2)
    changeStatusMock.mockResolvedValue({ kind: 'success', result: changed })
    const { rerender } = renderForm(from)

    if (decision) {
      await user.click(screen.getByRole('radio', { name: decision }))
    }
    await user.click(screen.getByRole('button', { name: button }))
    // 親（ApplicationDetail）が変更後の詳細を渡し直したあと
    rerender(
      <StatusChangeForm
        detail={changed}
        onChanged={vi.fn()}
        onConflict={vi.fn()}
      />,
    )

    const status = await screen.findByRole('status')
    expect(status).toHaveTextContent(message)
    expect(status).toHaveFocus()
  },
)

test('もう一度押すとき、前の成功のメッセージを消す', async () => {
  const user = userEvent.setup()
  const changed = detailIn('UNDER_REVIEW', 1)
  changeStatusMock.mockResolvedValueOnce({ kind: 'success', result: changed })
  changeStatusMock.mockReturnValueOnce(new Promise(() => {}))
  const { rerender } = renderForm('APPLIED')
  await user.click(screen.getByRole('button', { name: '審査中にする' }))
  rerender(
    <StatusChangeForm
      detail={changed}
      onChanged={vi.fn()}
      onConflict={vi.fn()}
    />,
  )
  await screen.findByRole('status')

  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

test('実行中は「決定する」を押せない', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockReturnValue(new Promise(() => {}))
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(screen.getByRole('button', { name: '決定する' })).toBeDisabled()
})

test('もう一度押すとき、前のメッセージを消す', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockResolvedValueOnce({
    kind: 'conflict',
    title: 'ほかの担当者が先に状態を変えました',
    detail: '最新の状態を確かめてから、もう一度操作してください',
  })
  changeStatusMock.mockReturnValueOnce(new Promise(() => {}))
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('radio', { name: '承認' }))
  await user.click(screen.getByRole('button', { name: '決定する' }))
  await screen.findByRole('alert')
  await user.click(screen.getByRole('button', { name: '決定する' }))

  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test.each([
  ['APPLIED', '審査中にする', null],
  ['UNDER_REVIEW', '決定する', '否決'],
] as const)(
  '%s で「%s」のあと入力エラーが返ったら、フォーカスを押したボタンに戻す',
  async (status, label, decision) => {
    const user = userEvent.setup()
    let respond!: (outcome: Awaited<ReturnType<typeof changeStatus>>) => void
    changeStatusMock.mockReturnValueOnce(
      new Promise((resolve) => {
        respond = resolve
      }),
    )
    renderForm(status)

    if (decision) {
      await user.click(screen.getByRole('radio', { name: decision }))
    }
    await user.click(screen.getByRole('button', { name: label }))
    const button = screen.getByRole('button', { name: label })
    // ブラウザーは押せなくなったボタンからフォーカスを外すが、jsdom は外さないので、ここで外す
    button.blur()
    expect(button).not.toHaveFocus()

    respond({
      kind: 'inputError',
      errors: [{ field: 'staffName', message: '担当者名を入力してください' }],
    })

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(button).toHaveFocus()
  },
)
