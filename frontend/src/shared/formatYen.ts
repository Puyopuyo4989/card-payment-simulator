const yenFormat = new Intl.NumberFormat('ja-JP')

/** 金額を3桁区切りにし「円」を付ける（FR-014）。表示だけで、金額の計算はしない。 */
export function formatYen(amount: number): string {
  return `${yenFormat.format(amount)}円`
}
