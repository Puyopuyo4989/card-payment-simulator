import { expect, test } from 'vitest'
import { formatManYen } from './formatManYen'

test.each([
  [0, '0万円'],
  [450, '450万円'],
  [9999, '9,999万円'],
])('年収 %d は「%s」と表示する', (value, expected) => {
  expect(formatManYen(value)).toBe(expected)
})
