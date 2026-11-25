import { expect, test } from 'vitest'
import { formatDateTime } from './formatDateTime'

// UTC の日付・時刻と違う値にしてあるので、実行環境のタイムゾーンに頼っていれば失敗する（tasks.md T043）
test.each([
  ['2026-11-24T15:00:00Z', '2026/11/25 00:00'],
  ['2026-11-25T01:30:00Z', '2026/11/25 10:30'],
  ['2026-01-04T23:05:00Z', '2026/01/05 08:05'],
  ['2026-11-25T14:59:00Z', '2026/11/25 23:59'],
])('%s は日本時間の %s と表示する', (value, expected) => {
  expect(formatDateTime(value)).toBe(expected)
})

test('マイクロ秒のある日時も分までで表示する', () => {
  expect(formatDateTime('2026-11-25T01:30:59.123456Z')).toBe('2026/11/25 10:30')
})
