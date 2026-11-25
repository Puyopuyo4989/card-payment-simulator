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

beforeEach(() => {
  changeStatusMock.mockReset()
})

test('申込のときは「審査中にする」だけを出し、否決の理由の欄は出さない', () => {
  renderForm('APPLIED')

  expect(
    screen.getAllByRole('button').map((button) => button.textContent),
  ).toEqual(['審査中にする'])
  expect(screen.getByLabelText('担当者名')).toBeInTheDocument()
  expect(screen.queryByLabelText('否決の理由')).not.toBeInTheDocument()
})

test('審査中のときは「承認する」「否決する」だけを出し、否決の理由の欄も出す', () => {
  renderForm('UNDER_REVIEW')

  expect(
    screen.getAllByRole('button').map((button) => button.textContent),
  ).toEqual(['承認する', '否決する'])
  expect(screen.getByLabelText('担当者名')).toBeInTheDocument()
  expect(screen.getByLabelText('否決の理由')).toBeInTheDocument()
})

test.each(['APPROVED', 'REJECTED'] as const)(
  '%s のときはフォームの代わりにメッセージを出す',
  (status) => {
    renderForm(status)

    expect(
      screen.getByText('審査が終わった申込の状態は変えられません'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('担当者名')).not.toBeInTheDocument()
  },
)

test('ボタンを押すと、入力のままの値と、詳細の版を文字列にして送る', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockReturnValue(new Promise(() => {}))
  renderForm('UNDER_REVIEW', { version: 3 })

  await user.type(screen.getByLabelText('担当者名'), ' テスト 担当B ')
  await user.type(screen.getByLabelText('否決の理由'), 'テスト用の否決理由')
  await user.click(screen.getByRole('button', { name: '否決する' }))

  expect(changeStatusMock).toHaveBeenCalledWith('A00000001', {
    toStatus: 'REJECTED',
    staffName: ' テスト 担当B ',
    rejectionReason: 'テスト用の否決理由',
    version: '3',
  })
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

  await user.click(screen.getByRole('button', { name: '否決する' }))

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

test('ほかの担当者とぶつかったとき、title と detail を出して知らせ、担当者名は残す', async () => {
  const user = userEvent.setup()
  const onConflict = vi.fn()
  changeStatusMock.mockResolvedValue({
    kind: 'conflict',
    title: 'ほかの担当者が先に状態を変えました',
    detail: '最新の状態を確かめてから、もう一度操作してください',
  })
  renderForm('UNDER_REVIEW', { onConflict })

  await user.type(screen.getByLabelText('担当者名'), 'テスト 担当B')
  await user.click(screen.getByRole('button', { name: '承認する' }))

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('ほかの担当者が先に状態を変えました')
  expect(alert).toHaveTextContent(
    '最新の状態を確かめてから、もう一度操作してください',
  )
  expect(onConflict).toHaveBeenCalledTimes(1)
  expect(screen.getByLabelText('担当者名')).toHaveValue('テスト 担当B')
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

test('成功したら変更後の詳細を渡し、担当者名と否決の理由の欄を空にする', async () => {
  const user = userEvent.setup()
  const onChanged = vi.fn()
  const changed = detailIn('UNDER_REVIEW', 1)
  changeStatusMock.mockResolvedValue({ kind: 'success', result: changed })
  const { rerender } = renderForm('UNDER_REVIEW', { onChanged })

  await user.type(screen.getByLabelText('担当者名'), 'テスト 担当A')
  await user.type(screen.getByLabelText('否決の理由'), 'テスト用の否決理由')
  await user.click(screen.getByRole('button', { name: '承認する' }))

  expect(onChanged).toHaveBeenCalledWith(changed)
  expect(screen.getByLabelText('担当者名')).toHaveValue('')
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

test('実行中はボタンを押せない', async () => {
  const user = userEvent.setup()
  changeStatusMock.mockReturnValue(new Promise(() => {}))
  renderForm('UNDER_REVIEW')

  await user.click(screen.getByRole('button', { name: '承認する' }))

  expect(screen.getByRole('button', { name: '承認する' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '否決する' })).toBeDisabled()
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

  await user.click(screen.getByRole('button', { name: '承認する' }))
  await screen.findByRole('alert')
  await user.click(screen.getByRole('button', { name: '承認する' }))

  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

// 押したのと違うボタンにフォーカスが移ると、続けて Enter を押したときに逆の決定になる（取り消せない）
test.each(['承認する', '否決する'])(
  '「%s」のあと入力エラーが返ったら、フォーカスを押したボタンに戻す',
  async (label) => {
    const user = userEvent.setup()
    let respond!: (outcome: Awaited<ReturnType<typeof changeStatus>>) => void
    changeStatusMock.mockReturnValueOnce(
      new Promise((resolve) => {
        respond = resolve
      }),
    )
    renderForm('UNDER_REVIEW')

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
