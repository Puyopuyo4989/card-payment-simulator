// POST /api/revolving-simulations の型。項目名は contracts/revolving-simulation-api.md と同じにする
import type {
  FieldError as SharedFieldError,
  ScheduleRow,
  SimulationErrorResponse,
  SimulationOutcome as SharedSimulationOutcome,
} from '../../shared/types'

export type { ScheduleRow }

/** リクエスト。画面に入力されたままの文字列を、変換しないで送る */
export type RevolvingSimulationRequest = {
  balance: string
  monthlyPayment: string
  annualRate: string
}

/** 成功（200）のレスポンス。金額は円単位の整数 */
export type RevolvingSimulationResult = {
  paymentCount: number
  totalFee: number
  totalPayment: number
  schedule: ScheduleRow[]
}

/** エラーを返す項目 */
export type RevolvingField = 'balance' | 'monthlyPayment' | 'annualRate'

/** 1つの項目の入力エラー。message は画面にそのまま表示する */
export type FieldError = SharedFieldError<RevolvingField>

/** 入力エラー（400）のレスポンス */
export type RevolvingSimulationErrorResponse =
  SimulationErrorResponse<RevolvingField>

/** API を呼んだ結果。成功・入力エラー・予期しないエラー（errors のない 400、500、通信の失敗）のどれか */
export type SimulationOutcome = SharedSimulationOutcome<
  RevolvingSimulationResult,
  RevolvingField
>
