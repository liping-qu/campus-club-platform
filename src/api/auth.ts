import { request, setToken, clearToken } from '@/utils/request'

export interface AuthUser {
  id: number
  username: string
  nickname: string
  role: string
  roleText: string
  studentNo: string | null
  college: string | null
  grade: string | null
  avatarUrl: string | null
}

export interface LoginResult {
  token: string
  tokenType: string
  /** 有效期，单位秒 */
  expiresIn: number
  user: AuthUser
}

const USER_KEY = 'campus_auth_user'

export async function login(username: string, password: string): Promise<LoginResult> {
  const result = await request<LoginResult>(
    '/api/auth/login',
    'POST',
    { username, password },
    { showError: false }
  )
  setToken(result.token)
  uni.setStorageSync(USER_KEY, result.user)
  return result
}

export function logout() {
  clearToken()
  uni.removeStorageSync(USER_KEY)
}

/** 本地缓存的用户信息，仅用于渲染，权威数据以 /api/auth/me 为准 */
export function getCachedUser(): AuthUser | null {
  const value = uni.getStorageSync(USER_KEY)
  return value || null
}

export function fetchMe() {
  return request<AuthUser>('/api/auth/me', 'GET', undefined, { showError: false })
}
