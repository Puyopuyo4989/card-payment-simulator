// POST /api/installment-simulations の型。項目名は contracts/installment-simulation-api.md と同じにする
import type {
  FieldError as SharedFieldError,
  ScheduleRow,
  SimulationErrorResponse,
  SimulationOutcome as SharedSimulationOutcome,
} from '../../shared/types'

export type { ScheduleRow }

/** リクエスト。画面に入力されたままの文字列を、変換しないで送る */
export type InstallmentSimulationRequest = {
  amount: string
  paymentCount: string
  annualRate: string
}

/** 成功（200）のレスポンス。金額は円単位の整数 */
export type InstallmentSimulationResult = {
  totalFee: number
  totalPayment: number
  firstPayment: number
  subsequentPayment: number
  schedule: ScheduleRow[]
}

/** エラーを返す項目 */
export type InstallmentField = 'amount' | 'paymentCount' | 'annualRate'

/** 1つの項目の入力エラー。message は画面にそのまま表示する */
export type FieldError = SharedFieldError<InstallmentField>

/** 入力エラー（400）のレスポンス */
export type InstallmentSimulationErrorResponse =
  SimulationErrorResponse<InstallmentField>

/** API を呼んだ結果。成功・入力エラー・予期しないエラー（errors のない 400、500、通信の失敗）のどれか */
export type SimulationOutcome = SharedSimulationOutcome<
  InstallmentSimulationResult,
  InstallmentField
>
