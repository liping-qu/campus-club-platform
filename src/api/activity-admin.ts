import { request, toQuery, type PageResult } from '@/utils/request'
import type { ActivityDetail } from '@/api/activity'

/**
 * 活动管理端接口。
 *
 * 与展示端（api/activity.ts）分开，是因为两者的失败语义不同：
 * 展示端关心「活动不存在/已下架」，管理端关心「名额不能小于已报名」「状态不允许」，
 * 页面要按 code 分别提示，所以这里统一 showError: false，由页面自己决定说什么。
 */

/** 0 草稿 / 1 已发布 / 2 已下架 / 3 已结束（3 是后端按时间推导出来的，不是库里的状态） */
export type ManageStatus = 0 | 1 | 2 | 3

export interface ManageQuery {
  pageNo?: number
  pageSize?: number
  status?: ManageStatus
}

/**
 * 可编辑字段。PUT 是整体覆盖，所以更新时也必须传全量 —— 少传一个字段就等于清空它。
 * 注意没有 status / joinedCount：状态由状态机改，计数由报名事务维护。
 */
export interface ActivitySavePayload {
  title: string
  description: string
  location: string
  category: string
  /** 统一 "yyyy-MM-dd HH:mm:ss"，与后端 JacksonConfig 注册的格式一致 */
  startTime: string
  endTime: string
  signupDeadline: string
  capacity: number
  coverColor: string
  rules: string
}

export interface ActivityCreatePayload extends ActivitySavePayload {
  clubId: number
}

/** 管理端列表：含草稿与已下架，按归属过滤 */
export function fetchManageableActivities(query: ManageQuery = {}) {
  return request<PageResult<ActivityDetail>>(
    `/api/activities/manage${toQuery(query)}`,
    'GET',
    undefined,
    { showError: false }
  )
}

export function createActivity(payload: ActivityCreatePayload) {
  return request<ActivityDetail>('/api/activities', 'POST', payload, { showError: false })
}

export function updateActivity(id: number | string, payload: ActivitySavePayload) {
  return request<ActivityDetail>(`/api/activities/${id}`, 'PUT', payload, { showError: false })
}

export function publishActivity(id: number | string) {
  return request<ActivityDetail>(`/api/activities/${id}/publish`, 'POST', undefined, { showError: false })
}

export function unpublishActivity(id: number | string) {
  return request<ActivityDetail>(`/api/activities/${id}/unpublish`, 'POST', undefined, { showError: false })
}

export function deleteActivity(id: number | string) {
  return request<void>(`/api/activities/${id}`, 'DELETE', undefined, { showError: false })
}
