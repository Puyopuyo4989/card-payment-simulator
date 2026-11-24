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
    // Vitest は CSS の中身を空にするが、コントラスト比のテスト（src/styles/contrast.test.ts）が色を読めるよう、tokens.css だけは中身を返す
    css: { include: [/tokens\.css/] },
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
