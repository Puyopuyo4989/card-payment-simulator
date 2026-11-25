const manYenFormat = new Intl.NumberFormat('ja-JP')

/** 年収（万円の整数）を3桁区切りにし「万円」を付ける（FR-011）。表示だけで、計算はしない */
export function formatManYen(value: number): string {
  return `${manYenFormat.format(value)}万円`
}
