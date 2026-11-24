// 分割払いとリボ払いの API に共通の型。項目名は各機能の contracts と同じにする

/** 返済表の1行。金額は円単位の整数 */
export type ScheduleRow = {
  number: number
  payment: number
  principal: number
  fee: number
  balanceAfter: number
}

/** 1つの項目の入力エラー。message は画面にそのまま表示する */
export type FieldError<F extends string> = {
  field: F
  message: string
}

/** 入力エラー（400）のレスポンス */
export type SimulationErrorResponse<F extends string> = {
  errors: FieldError<F>[]
}

/** API を呼んだ結果。成功・入力エラー・予期しないエラー（errors のない 400、500、通信の失敗）のどれか */
export type SimulationOutcome<R, F extends string> =
  | { kind: 'success'; result: R }
  | { kind: 'inputError'; errors: FieldError<F>[] }
  | { kind: 'unexpectedError' }
