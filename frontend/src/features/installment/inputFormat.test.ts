// 入力欄の表示の整え方（spec FR-015〜FR-017）。どれも文字の操作だけで、数に変えない
import { describe, expect, test } from 'vitest'
import { amountForApi, formatAmountInput, formatRateInput } from './inputFormat'

describe('formatAmountInput（FR-015：フォーカスが外れたときの利用金額の表示）', () => {
  test.each([
    ['100000', '100,000'],
    ['1000', '1,000'],
    ['999', '999'],
    ['0', '0'],
    ['10000000', '10,000,000'],
    // 利用者が入れたカンマも、付け直す（位置が違っていても）
    ['100,000', '100,000'],
    ['1,00,000', '100,000'],
    // 大きな桁でも丸めない（数に変えないので、Number の精度を超えても桁が変わらない）
    ['12345678901234567890', '12,345,678,901,234,567,890'],
  ])('%s → %s', (input, expected) => {
    expect(formatAmountInput(input)).toBe(expected)
  })

  test.each([
    [''],
    ['abc'],
    ['1000.5'],
    ['-1000'],
    ['１００００'],
    [' 100000'],
    [','],
    ['1,000.5'],
  ])(
    'カンマを取り除いても半角数字だけにならない値（%j）は、入力されたまま残す',
    (input) => {
      expect(formatAmountInput(input)).toBe(input)
    },
  )
})

describe('amountForApi（FR-015・FR-017：API に送る利用金額）', () => {
  test.each([
    ['100,000', '100000'],
    ['1,00,000', '100000'],
    ['100000', '100000'],
    ['12,345,678,901,234,567,890', '12345678901234567890'],
  ])('%s → %s（カンマを取り除く）', (input, expected) => {
    expect(amountForApi(input)).toBe(expected)
  })

  test.each([[''], ['abc'], ['1,000.5'], ['-1,000'], [','], [' 100000']])(
    'カンマを取り除いても半角数字だけにならない値（%j）は、入力されたまま送る（API が形式の誤りにする）',
    (input) => {
      expect(amountForApi(input)).toBe(input)
    },
  )
})

describe('formatRateInput（FR-016：フォーカスが外れたときの実質年率の表示）', () => {
  test.each([
    ['15', '15.0'],
    ['0', '0.0'],
    ['015', '015.0'],
  ])('整数 %s → %s', (input, expected) => {
    expect(formatRateInput(input)).toBe(expected)
  })

  test.each([
    [''],
    ['15.0'],
    ['12.5'],
    ['12.25'],
    ['abc'],
    ['１５'],
    ['-1'],
    ['15.'],
  ])('整数でない値（%j）は、入力されたまま残す', (input) => {
    expect(formatRateInput(input)).toBe(input)
  })
})
