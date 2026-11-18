import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    // 除外は ADR 0006 に書いたものだけにする。vite.config.ts は include の外にある
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/setupTests.ts', 'src/**/*.test.{ts,tsx}'],
      thresholds: {
        lines: 90,
        branches: 90,
      },
    },
  },
})
