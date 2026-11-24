import { postSimulation } from '../../shared/postSimulation'
import type {
  RevolvingField,
  RevolvingSimulationRequest,
  RevolvingSimulationResult,
  SimulationOutcome,
} from './types'

/** POST /api/revolving-simulations を呼ぶ。入力は変換しないで、文字列のまま送る */
export function simulate(
  request: RevolvingSimulationRequest,
): Promise<SimulationOutcome> {
  return postSimulation<
    RevolvingSimulationRequest,
    RevolvingSimulationResult,
    RevolvingField
  >('/api/revolving-simulations', request)
}
