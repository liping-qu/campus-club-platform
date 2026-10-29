import { request, toQuery, type PageResult } from '@/utils/request'

export interface ActivityListItem {
  id: number
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
  status: number
  /** OPEN / UPCOMING / FULL / FINISHED */
  phase: string
  phaseText: string
  coverColor: string
}

export interface ActivityDetail extends ActivityListItem {
  description: string
  ruleList: string[]
  createdBy: number
  creatorName: string
  createdAt: string
}

export type ActivityPhase = 'ALL' | 'OPEN' | 'UPCOMING' | 'FINISHED'

export interface ActivityQueryParams {
  page?: number
  size?: number
  keyword?: string
  category?: string
  phase?: ActivityPhase
}

export function fetchActivities(params: ActivityQueryParams = {}) {
  return request<PageResult<ActivityListItem>>(`/api/activities${toQuery(params)}`)
}

export function fetchActivityDetail(id: string | number) {
  // 404 属于页面要自己展示的分支（活动不存在），不由请求层弹 toast
  return request<ActivityDetail>(`/api/activities/${id}`, 'GET', undefined, { showError: false })
}
