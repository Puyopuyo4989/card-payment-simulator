import { describe, expect, test } from 'vitest'
import tokensCss from './tokens.css?raw'

/**
 * 文字と背景のコントラスト比（FR-021、WCAG 2.1 AA）を確かめる。
 * 色は tokens.css の CSS 変数から読むので、色を変えたときもここで確かめられる
 */

/** tokens.css の `--名前: #rrggbb;` を読み取る */
function readColors(css: string): Map<string, string> {
  const colors = new Map<string, string>()
  for (const match of css.matchAll(/(--[\w-]+)\s*:\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    colors.set(match[1], match[2])
  }
  return colors
}

/** WCAG 2.1 の相対輝度 */
function relativeLuminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((start) => {
    const value = parseInt(hex.slice(start, start + 2), 16) / 255
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG 2.1 のコントラスト比（1〜21） */
function contrastRatio(a: string, b: string): number {
  const [lighter, darker] = [relativeLuminance(a), relativeLuminance(b)].sort(
    (x, y) => y - x,
  )
  return (lighter + 0.05) / (darker + 0.05)
}

/** 通常の文字（WCAG 1.4.3） */
const TEXT = 4.5
/** 入力欄の枠・フォーカスの輪など、文字ではない部品（WCAG 1.4.11） */
const NON_TEXT = 3

/** [前景, 背景, 必要な比, 使う場所] */
const PAIRS: [string, string, number, string][] = [
  ['--color-text', '--color-surface', TEXT, '本文（カードの上）'],
  ['--color-text', '--color-bg', TEXT, '本文（ページの背景の上）'],
  ['--color-text', '--color-primary-soft', TEXT, '返済表の見出し・要約'],
  ['--color-text', '--color-row-alt', TEXT, '返済表の1行おきの行'],
  ['--color-text-muted', '--color-surface', TEXT, '補足の文字'],
  ['--color-text-muted', '--color-bg', TEXT, '補足の文字（ページの背景の上）'],
  ['--color-heading', '--color-surface', TEXT, '見出し'],
  ['--color-heading', '--color-primary-soft', TEXT, '要約の見出し'],
  ['--color-on-primary', '--color-primary', TEXT, 'ボタン・選んでいるタブ'],
  [
    '--color-on-primary',
    '--color-primary-strong',
    TEXT,
    'ボタン（マウスを重ねたとき）',
  ],
  ['--color-primary', '--color-surface', TEXT, '選んでいないタブ'],
  [
    '--color-primary-strong',
    '--color-primary-soft',
    TEXT,
    '選んでいないタブ（マウスを重ねたとき）',
  ],
  [
    '--color-primary',
    '--color-row-alt',
    TEXT,
    '申込の一覧の申込番号のボタン（1行おきの行）',
  ],
  ['--color-error', '--color-error-bg', TEXT, '入力エラーの赤字'],
  ['--color-error', '--color-surface', TEXT, '入力エラーの入力欄の枠'],
  ['--color-border-strong', '--color-surface', NON_TEXT, '入力欄の枠'],
  ['--color-focus', '--color-surface', NON_TEXT, 'フォーカスの輪'],
  ['--color-focus', '--color-bg', NON_TEXT, 'フォーカスの輪（タブの外側）'],
  ['--color-brand', '--color-surface', NON_TEXT, 'ロゴ'],
]

describe('contrastRatio', () => {
  test('黒と白は 21:1、同じ色は 1:1', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrastRatio('#1a7f45', '#1a7f45')).toBeCloseTo(1, 5)
  })
})

describe('tokens.css の色の組み合わせ', () => {
  const colors = readColors(tokensCss)

  test.each(PAIRS)('%s と %s は %d:1 以上（%s）', (fg, bg, minimum) => {
    const foreground = colors.get(fg)
    const background = colors.get(bg)
    expect(foreground, `${fg} が tokens.css にない`).toBeDefined()
    expect(background, `${bg} が tokens.css にない`).toBeDefined()
    expect(contrastRatio(foreground!, background!)).toBeGreaterThanOrEqual(
      minimum,
    )
  })
})
