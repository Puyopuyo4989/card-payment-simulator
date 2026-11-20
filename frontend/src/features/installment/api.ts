import type {
  InstallmentSimulationRequest,
  InstallmentSimulationResult,
} from './types'

/** POST /api/installment-simulations を呼ぶ。入力は変換しないで、文字列のまま送る。エラーの扱いは US3 で足す */
export async function simulate(
  request: InstallmentSimulationRequest,
): Promise<InstallmentSimulationResult> {
  const response = await fetch('/api/installment-simulations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return response.json()
}
