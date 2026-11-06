/**
 * 统一请求封装。
 *
 * 约定：
 * - 后端统一响应体为 { code, message, data }，code === 0 才算成功。
 * - HTTP 状态码表达传输层语义，业务错误码表达具体原因，两者都保留下来，
 *   页面可以按 code 精确区分「活动不存在」这类可预期分支。
 * - 认证凭证统一从这里带，页面不直接拼 header。
 */

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** 业务错误码，与后端 ErrorCode 对应 */
export const ErrorCodes = {
  SUCCESS: 0,
  INVALID_PARAM: 40000,
  INVALID_TIME_RANGE: 40002,
  SIGNUP_DEADLINE_AFTER_START: 40003,
  UNAUTHORIZED: 40100,
  BAD_CREDENTIALS: 40101,
  TOKEN_EXPIRED: 40102,
  INVALID_TOKEN: 40103,
  FORBIDDEN: 40300,
  ACCOUNT_DISABLED: 40301,
  NOT_FOUND: 40400,
  ACTIVITY_NOT_FOUND: 40401,
  ACTIVITY_FULL: 40901,
  ALREADY_REGISTERED: 40902,
  NOT_REGISTERED: 40903,
  ACTIVITY_CLOSED: 40904,
  SIGNUP_CLOSED: 40905,
  ALREADY_CHECKED_IN: 40906,
  REGISTER_CONFLICT: 40908,
  CAPACITY_BELOW_JOINED: 40909,
  ACTIVITY_STATUS_CONFLICT: 40910,
  CHECKIN_NOT_OPEN: 40911,
  CHECKIN_CLOSED: 40912,
  CHECKED_IN_CANNOT_CANCEL: 40913,
  ATTENDANCE_NOT_FOUND: 40405
} as const

export class ApiError extends Error {
  readonly code: number
  readonly httpStatus: number

  constructor(code: number, message: string, httpStatus: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

const TOKEN_KEY = 'campus_token'
const LOGIN_PAGE = '/pages/login/index'

/** 防止多个并发请求同时 401 时反复跳登录页 */
let redirectingToLogin = false

/**
 * 登录态失效的统一处理：清掉本地令牌，并把用户带去登录页。
 *
 * 用 reLaunch 而不是 navigateTo：登录页不该留在返回栈里，
 * 否则用户登录后一按返回又回到登录页。
 */
function handleUnauthorized() {
  clearToken()
  uni.removeStorageSync('campus_auth_user')
  if (redirectingToLogin) return
  redirectingToLogin = true
  uni.reLaunch({
    url: LOGIN_PAGE,
    complete: () => {
      // 页面切换完成后解锁，下一次失效还能正常跳转
      setTimeout(() => { redirectingToLogin = false }, 800)
    }
  })
}

// H5 开发环境走 Vite 代理（同源，无跨域）；其余平台直连后端。
// 注意用 let + 条件赋值而不是两个 const 声明：
// 条件编译注释由 uni 构建插件处理，vue-tsc 看不到它，
// 两个同名的 const 会被判定为重复声明。
let BASE_URL = ''
// #ifndef H5
BASE_URL = 'http://127.0.0.1:8090'
// #endif

export function getToken(): string {
  return (uni.getStorageSync(TOKEN_KEY) as string) || ''
}

export function setToken(token: string) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY)
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface RequestOptions {
  /** 失败时是否自动弹 toast，默认 true。页面需要自己处理错误时传 false */
  showError?: boolean
  /**
   * 401 时是否自动跳登录页，默认 true。
   * 「登录可选」的探测型请求（例如详情页顺带查我是否已报名）要传 false，
   * 否则游客一打开详情页就会被弹去登录。
   */
  redirectOnUnauthorized?: boolean
}

export function request<T>(
  path: string,
  method: Method = 'GET',
  data?: Record<string, any>,
  options: RequestOptions = {}
): Promise<T> {
  const { showError = true, redirectOnUnauthorized = true } = options
  const token = getToken()

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE_URL + path,
      method,
      data,
      timeout: 10000,
      header: {
        'Content-Type': 'application/json',
        // 身份只走令牌，不再有任何「前端指定用户」的通道
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        const body = res.data as ApiResponse<T> | undefined
        const httpOk = res.statusCode >= 200 && res.statusCode < 300

        if (httpOk && body && body.code === ErrorCodes.SUCCESS) {
          resolve(body.data)
          return
        }

        // 401：令牌缺失/过期/被篡改，统一清态并回登录页
        if (res.statusCode === 401) {
          if (redirectOnUnauthorized) {
            handleUnauthorized()
          } else {
            // 探测型请求：只清掉本地令牌，不打断页面
            clearToken()
          }
        }

        const error = new ApiError(
          body?.code ?? res.statusCode,
          body?.message || '请求失败',
          res.statusCode
        )
        if (showError && res.statusCode !== 401) {
          uni.showToast({ title: error.message, icon: 'none' })
        }
        reject(error)
      },
      fail: () => {
        const error = new ApiError(-1, '网络异常，请确认后端服务已启动', 0)
        if (showError) {
          uni.showToast({ title: error.message, icon: 'none' })
        }
        reject(error)
      }
    })
  })
}

/** 将对象拼成查询串，自动忽略空值 */
export function toQuery(params: Record<string, any>): string {
  const pairs = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}
