import { useEffect, useRef } from 'react'

/**
 * 押せなくなったボタンからはフォーカスが外れるので、実行が終わったらボタンに戻す（キーボードの利用者のため）。
 * 入力欄で Enter を押したときなど、フォーカスがどこかにあるときは動かさない。戻す先のボタンの ref を返す
 */
export function useReturnFocus(submitting: boolean) {
  const buttonRef = useRef<HTMLButtonElement>(null)
  const wasSubmitting = useRef(false)

  useEffect(() => {
    if (
      wasSubmitting.current &&
      !submitting &&
      document.activeElement === document.body
    ) {
      buttonRef.current?.focus()
    }
    wasSubmitting.current = submitting
  }, [submitting])

  return buttonRef
}
