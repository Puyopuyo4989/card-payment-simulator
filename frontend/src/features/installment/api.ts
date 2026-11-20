import type {
  InstallmentSimulationErrorResponse,
  InstallmentSimulationRequest,
  InstallmentSimulationResult,
  SimulationOutcome,
} from './types'

/** POST /api/installment-simulations を呼ぶ。入力は変換しないで、文字列のまま送る */
export async function simulate(
  request: InstallmentSimulationRequest,
): Promise<SimulationOutcome> {
  try {
    const response = await fetch('/api/installment-simulations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
    if (response.ok) {
      const result: InstallmentSimulationResult = await response.json()
      return { kind: 'success', result }
    }
    if (response.status === 400) {
      const body: Partial<InstallmentSimulationErrorResponse> =
        await response.json()
      // errors のない 400（本文が JSON として読めない）は、画面からは起きないので予期しないエラーとする（contracts）
      if (body.errors) {
        return { kind: 'inputError', errors: body.errors }
      }
    }
    return { kind: 'unexpectedError' }
  } catch {
    return { kind: 'unexpectedError' }
  }
}
