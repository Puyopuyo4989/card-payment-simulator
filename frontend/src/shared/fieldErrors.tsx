import type { FieldError } from './types'

/**
 * 項目ごとの入力エラーの表示。エラーは入力欄のすぐ下に赤字で出し、aria-describedby で入力欄と結び付ける（ポップアップは使わない）。
 * role="alert" で、出たことをスクリーンリーダーに伝える
 */
export function fieldErrors<F extends string>(errors: FieldError<F>[]) {
  function messageOf(field: F) {
    return errors.find((error) => error.field === field)?.message
  }

  return {
    /** エラーがある項目の入力欄に付ける属性 */
    attributes(field: F) {
      return messageOf(field)
        ? { 'aria-invalid': true, 'aria-describedby': `${field}-error` }
        : {}
    },

    /** 入力欄のすぐ下に出すメッセージ */
    message(field: F) {
      const message = messageOf(field)
      return (
        message && (
          <p id={`${field}-error`} className="field-error" role="alert">
            {message}
          </p>
        )
      )
    },
  }
}
