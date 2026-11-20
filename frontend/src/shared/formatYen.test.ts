import { expect, test } from 'vitest'
import { formatYen } from './formatYen'

test.each([
  [100000, '100,000円'],
  [0, '0円'],
  [9034, '9,034円'],
  [12479518, '12,479,518円'],
])('%d は %s と表示する', (amount, expected) => {
  expect(formatYen(amount)).toBe(expected)
})
