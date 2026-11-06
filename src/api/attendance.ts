import { request } from '@/utils/request'

/** 签到方式，与后端 AttendanceMethod 对应 */
export type AttendanceMethod = 'MANUAL' | 'ADMIN' | 'QR_CODE'

/**
 * 我在某个活动上的签到状态。
 * 未签到是正常状态（checkedIn=false），接口不会返回 404，
 * 所以这里不需要 try/catch 去区分「没签过」和「活动不存在」。
 */
export interface MyCheckIn {
  activityId: number
  checkedIn: boolean
  checkedInAt: string | null
  volunteerHours: number
  /** 是否在签到窗口内（开始前 30 分钟 ~ 结束）。未签到也返回，用于前端置灰按钮 */
  checkInOpen: boolean
  method: AttendanceMethod | null
}

/** 我的签到记录项（含活动信息） */
export interface MyAttendanceItem {
  activityId: number
  title: string
  clubName: string
  location: string
  startTime: string
  endTime: string
  checkedInAt: string
  volunteerHours: number
  method: AttendanceMethod
}

/** 我的签到汇总：累计时长 + 记录列表 */
export interface MyAttendanceSummary {
  totalHours: number
  count: number
  records: MyAttendanceItem[]
}

export const METHOD_LABELS: Record<AttendanceMethod, string> = {
  MANUAL: '本人签到',
  ADMIN: '负责人代签',
  QR_CODE: '扫码签到'
}

/**
 * 本人签到。
 * 失败原因由后端给出（40911 未开始 / 40912 已结束 / 40906 已签到 / 40903 未报名），
 * 页面按 code 分别提示，不在请求层弹 toast。
 */
export function checkIn(activityId: string | number) {
  return request<MyCheckIn>(
    `/api/activities/${activityId}/check-in`,
    'POST',
    undefined,
    { showError: false }
  )
}

export function fetchMyCheckIn(activityId: string | number) {
  // 游客也会调这个接口（答案是「没签到」），401 不该把人赶去登录页
  return request<MyCheckIn>(
    `/api/activities/${activityId}/check-in/me`,
    'GET',
    undefined,
    { showError: false, redirectOnUnauthorized: false }
  )
}

export function fetchMyAttendances() {
  return request<MyAttendanceSummary>('/api/attendances/me')
}
