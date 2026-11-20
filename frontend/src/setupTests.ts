import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Vitest の globals を使っていないので、Testing Library は自動で片付けない。テストごとに描いた画面を消す
afterEach(() => {
  cleanup()
})
