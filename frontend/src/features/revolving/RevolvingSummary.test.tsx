import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import RevolvingSummary from './RevolvingSummary'

/** 項目名（dt）の次にある値（dd）を返す */
function valueOf(label: string) {
  return screen.getByText(label, { selector: 'dt' }).nextElementSibling
    ?.textContent
}

test('完済までの支払回数・手数料の合計・支払総額を表示し、金額は3桁区切りにする', () => {
  // spec US1 の1（100,000円・10,000円・15.0%）
  render(
    <RevolvingSummary
      result={{
        paymentCount: 11,
        totalFee: 7497,
        totalPayment: 107497,
        schedule: [],
      }}
    />,
  )

  expect(valueOf('完済までの支払回数')).toBe('11回')
  expect(valueOf('手数料の合計')).toBe('7,497円')
  expect(valueOf('支払総額')).toBe('107,497円')
})

test('手数料が0円のときも0円と表示する', () => {
  // spec US1 の2（100,000円・10,000円・0.0%）
  render(
    <RevolvingSummary
      result={{
        paymentCount: 10,
        totalFee: 0,
        totalPayment: 100000,
        schedule: [],
      }}
    />,
  )

  expect(valueOf('完済までの支払回数')).toBe('10回')
  expect(valueOf('手数料の合計')).toBe('0円')
  expect(valueOf('支払総額')).toBe('100,000円')
})

test('値（dd）には、右寄せで桁をそろえる class（num）を付ける（分割払いの FR-020）', () => {
  render(
    <RevolvingSummary
      result={{
        paymentCount: 11,
        totalFee: 7497,
        totalPayment: 107497,
        schedule: [],
      }}
    />,
  )

  for (const label of ['完済までの支払回数', '手数料の合計', '支払総額']) {
    expect(
      screen.getByText(label, { selector: 'dt' }).nextElementSibling,
    ).toHaveClass('num')
  }
})
