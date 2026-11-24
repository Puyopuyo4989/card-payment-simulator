import { postSimulation } from '../../shared/postSimulation'
import type {
  InstallmentField,
  InstallmentSimulationRequest,
  InstallmentSimulationResult,
  SimulationOutcome,
} from './types'

/** POST /api/installment-simulations を呼ぶ。入力は変換しないで、文字列のまま送る */
export function simulate(
  request: InstallmentSimulationRequest,
): Promise<SimulationOutcome> {
  return postSimulation<
    InstallmentSimulationRequest,
    InstallmentSimulationResult,
    InstallmentField
  >('/api/installment-simulations', request)
}
