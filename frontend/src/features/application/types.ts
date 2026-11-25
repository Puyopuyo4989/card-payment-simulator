// 申込管理の API の型。項目名は contracts/card-application-api.md と同じにする
import type { FieldError } from '../../shared/types'

export type { FieldError }

/** 申込の状態（API の値） */
export type ApplicationStatus =
  'APPLIED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED'

/** 登録のリクエスト（contracts 1）。入力されたままの文字列を送る */
export type CardApplicationRequest = {
  nameKanji: string
  nameKana: string
  birthDate: string
  annualIncome: string
  employerName: string
  phoneNumber: string
}

export type RegistrationField = keyof CardApplicationRequest

/** 一覧の1行（contracts 2） */
export type ApplicationSummary = {
  applicationNumber: string
  nameKanji: string
  status: ApplicationStatus
  appliedAt: string
}

export type ApplicationList = {
  items: ApplicationSummary[]
}

/** 変更履歴の1行（contracts 3）。否決でない行の rejectionReason は null */
export type StatusHistory = {
  changedAt: string
  fromStatus: ApplicationStatus
  toStatus: ApplicationStatus
  staffName: string
  rejectionReason: string | null
}

/** 申込の詳細（contracts 3）。年収は万円の整数 */
export type ApplicationDetail = {
  applicationNumber: string
  nameKanji: string
  nameKana: string
  birthDate: string
  annualIncome: number
  employerName: string
  phoneNumber: string
  status: ApplicationStatus
  appliedAt: string
  updatedAt: string
  version: number
  history: StatusHistory[]
}

/** 状態の変更のリクエスト（contracts 4）。version も文字列で送る */
export type StatusChangeRequest = {
  toStatus: string
  staffName: string
  rejectionReason: string
  version: string
}

export type StatusChangeField = keyof StatusChangeRequest

/** API を呼んだ結果 */
export type ApiOutcome<R, F extends string> =
  | { kind: 'success'; result: R }
  | { kind: 'inputError'; errors: FieldError<F>[] }
  | { kind: 'notFound' }
  | { kind: 'conflict'; detail: string }
  | { kind: 'unexpectedError' }
