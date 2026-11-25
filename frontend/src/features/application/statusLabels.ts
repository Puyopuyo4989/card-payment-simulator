import type { ApplicationStatus } from './types'

/** 状態の表示名 */
export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  APPLIED: '申込',
  UNDER_REVIEW: '審査中',
  APPROVED: '承認',
  REJECTED: '否決',
}

/** 状態の並び（絞り込みの選択肢の順） */
export const STATUSES: ApplicationStatus[] = [
  'APPLIED',
  'UNDER_REVIEW',
  'APPROVED',
  'REJECTED',
]

export type StatusAction = { toStatus: ApplicationStatus; label: string }

/**
 * 今の状態から出すボタン（research.md R12）。ボタンを出すかどうかを決めるだけで、遷移の正しさは API が守る
 */
const NEXT_ACTIONS: Record<ApplicationStatus, StatusAction[]> = {
  APPLIED: [{ toStatus: 'UNDER_REVIEW', label: '審査中にする' }],
  UNDER_REVIEW: [
    { toStatus: 'APPROVED', label: '承認する' },
    { toStatus: 'REJECTED', label: '否決する' },
  ],
  APPROVED: [],
  REJECTED: [],
}

export function nextActions(status: ApplicationStatus): StatusAction[] {
  return NEXT_ACTIONS[status]
}
