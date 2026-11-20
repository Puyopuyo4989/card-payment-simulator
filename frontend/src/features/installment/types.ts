// POST /api/installment-simulations の型。項目名は contracts/installment-simulation-api.md と同じにする

/** リクエスト。画面に入力されたままの文字列を、変換しないで送る */
export type InstallmentSimulationRequest = {
  amount: string
  paymentCount: string
  annualRate: string
}

/** 返済表の1行。金額は円単位の整数 */
export type ScheduleRow = {
  number: number
  payment: number
  principal: number
  fee: number
  balanceAfter: number
}

/** 成功（200）のレスポンス。金額は円単位の整数 */
export type InstallmentSimulationResult = {
  totalFee: number
  totalPayment: number
  firstPayment: number
  subsequentPayment: number
  schedule: ScheduleRow[]
}

/** 1つの項目の入力エラー。message は画面にそのまま表示する */
export type FieldError = {
  field: 'amount' | 'paymentCount' | 'annualRate'
  message: string
}

/** 入力エラー（400）のレスポンス */
export type InstallmentSimulationErrorResponse = {
  errors: FieldError[]
}

/** API を呼んだ結果。成功・入力エラー・予期しないエラー（errors のない 400、500、通信の失敗）のどれか */
export type SimulationOutcome =
  | { kind: 'success'; result: InstallmentSimulationResult }
  | { kind: 'inputError'; errors: FieldError[] }
  | { kind: 'unexpectedError' }
