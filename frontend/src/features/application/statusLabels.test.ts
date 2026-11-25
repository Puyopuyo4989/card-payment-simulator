import { expect, test } from 'vitest'
import { STATUS_LABELS, STATUSES, nextActions } from './statusLabels'

test('状態の表示名', () => {
  expect(STATUS_LABELS).toEqual({
    APPLIED: '申込',
    UNDER_REVIEW: '審査中',
    APPROVED: '承認',
    REJECTED: '否決',
  })
})

test('状態は4つで、この順に並べる', () => {
  expect(STATUSES).toEqual(['APPLIED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'])
})

test('申込のときは「審査中にする」だけを出す', () => {
  expect(nextActions('APPLIED')).toEqual([
    { toStatus: 'UNDER_REVIEW', label: '審査中にする' },
  ])
})

test('審査中のときは「承認する」と「否決する」を出す', () => {
  expect(nextActions('UNDER_REVIEW')).toEqual([
    { toStatus: 'APPROVED', label: '承認する' },
    { toStatus: 'REJECTED', label: '否決する' },
  ])
})

test.each(['APPROVED', 'REJECTED'] as const)(
  '%s のときはボタンを出さない',
  (status) => {
    expect(nextActions(status)).toEqual([])
  },
)
