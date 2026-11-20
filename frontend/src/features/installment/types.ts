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

/** 入力エラー（400）のレスポンス */
export type InstallmentSimulationErrorResponse = {
  errors: {
    field: 'amount' | 'paymentCount' | 'annualRate'
    message: string
  }[]
}
