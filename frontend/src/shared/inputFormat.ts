/*
 * 入力欄の表示の整え方（Should。分割払いの spec FR-015〜FR-018、リボ払いの spec FR-016〜FR-019 で共通）。
 * 整えるのは画面の中だけで、API の入力のルールは変えない。
 * 金額を Number や parseFloat で数に変えると大きな桁で丸められるので、どれも文字の操作だけで行う
 * （constitution「フロントで金額を計算しない」）。整えられない値は、入力されたまま残し、API の入力チェックに任せる
 */

const DIGITS = /^[0-9]+$/

/**
 * 前後の空白とカンマを取り除いた文字。半角数字だけにならなければ null。
 * 前後の空白は、コピーして貼り付けたときに付きやすいので、カンマを取り除く前に取り除く（API も前後の空白を取り除いてから確かめる）
 */
function digitsWithoutCommas(text: string): string | null {
  const digits = text.trim().replaceAll(',', '')
  return DIGITS.test(digits) ? digits : null
}

/** FR-015：利用金額を、フォーカスが外れたときに3桁区切りのカンマを付けて表示する（例：100000 → 100,000） */
export function formatAmountInput(text: string): string {
  const digits = digitsWithoutCommas(text)
  // 右から3桁ごとに、間にカンマを差し込む（文字の置き換えだけで、数に変えない）
  return digits === null ? text : digits.replace(/\B(?=(\d{3})+$)/g, ',')
}

/** FR-015・FR-017：API に送る利用金額。カンマを取り除く。半角数字だけにならない値は、入力されたまま送る */
export function amountForApi(text: string): string {
  return digitsWithoutCommas(text) ?? text
}

/** FR-016：実質年率は、整数が入力されたとき、フォーカスが外れたときに小数第1位まで表示する（例：15 → 15.0） */
export function formatRateInput(text: string): string {
  return DIGITS.test(text) ? `${text}.0` : text
}
