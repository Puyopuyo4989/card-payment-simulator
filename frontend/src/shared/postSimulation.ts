import type { SimulationErrorResponse, SimulationOutcome } from './types'

/** シミュレーションの API に POST する。入力は変換しないで、文字列のまま送る */
export async function postSimulation<Req, R, F extends string>(
  url: string,
  request: Req,
): Promise<SimulationOutcome<R, F>> {
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
    if (response.ok) {
      const result: R = await response.json()
      return { kind: 'success', result }
    }
    if (response.status === 400) {
      const body: Partial<SimulationErrorResponse<F>> = await response.json()
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
