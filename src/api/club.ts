import { request } from '@/utils/request'

/** 可管理的社团选项，用于新建活动时选择 clubId */
export interface ClubOption {
  id: number
  name: string
}

/**
 * 「我能管哪些社团」由后端判定（负责人 = club.owner_user_id），前端不按角色猜。
 * 平台管理员会拿到全部正常社团，社团负责人只拿到自己的。
 */
export function fetchMyClubs() {
  return request<ClubOption[]>('/api/clubs/mine', 'GET', undefined, { showError: false })
}
