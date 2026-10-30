import { request } from '@/utils/request'

/** 报名成功后的最新计数，可直接用于刷新详情页进度条 */
export interface RegistrationResult {
  activityId: number
  registrationId: number
  joinedCount: number
  remaining: number
  registeredAt: string
}

/** NONE / REGISTERED / CANCELLED */
export type RegistrationState = 'NONE' | 'REGISTERED' | 'CANCELLED'

export interface MyRegistration {
  status: RegistrationState
  statusText: string
  registeredAt: string | null
}

export interface RegistrationItem {
  /** 活动 ID */
  id: number
  registrationId: number
  registrationStatus: number
  registeredAt: string
  title: string
  clubId: number
  clubName: string
  location: string
  category: string
  startTime: string
  endTime: string
  signupDeadline: string
  capacity: number
  joinedCount: number
  remaining: number
  phase: string
  phaseText: string
  coverColor: string
}

export function registerActivity(activityId: string | number) {
  // 名额已满/重复报名等 409 由页面自己决定怎么提示，不在请求层弹 toast
  return request<RegistrationResult>(
    `/api/activities/${activityId}/registrations`,
    'POST',
    undefined,
    { showError: false }
  )
}

export function cancelRegistration(activityId: string | number) {
  return request<void>(
    `/api/activities/${activityId}/registrations/me`,
    'DELETE',
    undefined,
    { showError: false }
  )
}

export function fetchMyRegistration(activityId: string | number) {
  // 「我是否已报名」对游客也成立（答案是 NONE），因此 401 不该把人赶去登录页
  return request<MyRegistration>(
    `/api/activities/${activityId}/registrations/me`,
    'GET',
    undefined,
    { showError: false, redirectOnUnauthorized: false }
  )
}

export function fetchMyRegistrations() {
  return request<RegistrationItem[]>('/api/registrations/me')
}
