import type {
  ApiOutcome,
  ApplicationDetail,
  ApplicationList,
  ApplicationStatus,
  CardApplicationRequest,
  FieldError,
  RegistrationField,
  StatusChangeField,
  StatusChangeRequest,
} from './types'

const BASE = '/api/card-applications'

/** 409 の本文に detail がないときに出す文（contracts 4 の detail と同じ） */
// 409 の本文に title・detail がないときに出す文（contracts 4 と同じ）
const CONFLICT_TITLE = 'ほかの担当者が先に状態を変えました'
const CONFLICT_DETAIL = '最新の状態を確かめてから、もう一度操作してください'

/** POST /api/card-applications（contracts 1）。入力は変換しないで、文字列のまま送る */
export function register(
  request: CardApplicationRequest,
): Promise<ApiOutcome<ApplicationDetail, RegistrationField>> {
  return call(BASE, postJson(request))
}

/** GET /api/card-applications（contracts 2）。状態が空なら付けない（すべて） */
export function list(
  status: ApplicationStatus | '' = '',
  search?: ListSearch,
): Promise<ApiOutcome<ApplicationList, 'status'>> {
  const params = new URLSearchParams()
  if (status) {
    params.set('status', status)
  }
  // 【Should】キーワードとページ（contracts 6）
  if (search?.keyword) {
    params.set('keyword', search.keyword)
  }
  if (search) {
    params.set('page', String(search.page))
  }
  const encoded = params.toString()
  const query = encoded ? `?${encoded}` : ''
  return call(`${BASE}${query}`)
}

/** 【Should】一覧のキーワード（空なら絞り込まない）とページ（1から） */
export type ListSearch = { keyword: string; page: number }

/** GET /api/card-applications/{applicationNumber}（contracts 3） */
export function getDetail(
  applicationNumber: string,
): Promise<ApiOutcome<ApplicationDetail, never>> {
  return call(`${BASE}/${encodeURIComponent(applicationNumber)}`)
}

/** POST /api/card-applications/{applicationNumber}/status-changes（contracts 4） */
export function changeStatus(
  applicationNumber: string,
  request: StatusChangeRequest,
): Promise<ApiOutcome<ApplicationDetail, StatusChangeField>> {
  return call(
    `${BASE}/${encodeURIComponent(applicationNumber)}/status-changes`,
    postJson(request),
  )
}

function postJson(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

type ProblemBody<F extends string> = {
  title?: string
  detail?: string
  errors?: FieldError<F>[]
}

/**
 * API を呼び、結果の種類に分ける。分割払い・リボ払いの shared/postSimulation と違い、404 と 409 を返すので、この機能の中に書く
 */
async function call<R, F extends string>(
  url: string,
  init?: RequestInit,
): Promise<ApiOutcome<R, F>> {
  try {
    const response = await fetch(url, init)
    if (response.ok) {
      const result: R = await response.json()
      return { kind: 'success', result }
    }
    if (response.status === 404) {
      return { kind: 'notFound' }
    }
    if (response.status === 400 || response.status === 409) {
      const body: ProblemBody<F> = await response.json()
      if (response.status === 409) {
        return {
          kind: 'conflict',
          title: body.title ?? CONFLICT_TITLE,
          detail: body.detail ?? CONFLICT_DETAIL,
        }
      }
      // errors のない 400（本文が JSON として読めない）は、画面からは起きないので予期しないエラーとする
      if (body.errors) {
        return { kind: 'inputError', errors: body.errors }
      }
    }
    return { kind: 'unexpectedError' }
  } catch {
    return { kind: 'unexpectedError' }
  }
}
