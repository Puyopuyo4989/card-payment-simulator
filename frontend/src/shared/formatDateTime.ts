// 日本時間で表示する。ブラウザのタイムゾーンには頼らない（003 の research.md R10）
const dateTimeFormat = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** ISO 8601 の日時を「2026/11/25 00:00」の形（日本時間、24時間制）にする */
export function formatDateTime(value: string): string {
  const parts = Object.fromEntries(
    dateTimeFormat
      .formatToParts(new Date(value))
      .map((part) => [part.type, part.value]),
  )
  return `${parts.year}/${parts.month}/${parts.day} ${parts.hour}:${parts.minute}`
}
