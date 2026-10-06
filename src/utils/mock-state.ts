import { notifications as seedNotifications } from '@/data/mock'

const keys = {
  joinedClubs: 'campus_joined_clubs',
  pendingClubs: 'campus_pending_clubs',
  registrations: 'campus_registrations',
  favorites: 'campus_favorites',
  attendance: 'campus_attendance',
  volunteerHours: 'campus_volunteer_hours',
  notifications: 'campus_notifications',
  userProfile: 'campus_user_profile'
}

export interface UserProfile {
  nickname: string
  college: string
  grade: string
  studentId: string
  phone: string
}

const defaultUserProfile: UserProfile = {
  nickname: '同学',
  college: '信息学院',
  grade: '大一',
  studentId: '',
  phone: ''
}

function read<T>(key: string, fallback: T): T {
  const value = uni.getStorageSync(key)
  return value === undefined || value === null || value === '' ? fallback : value as T
}

function write<T>(key: string, value: T): T {
  uni.setStorageSync(key, value)
  return value
}

export const getJoinedClubIds = () => read<string[]>(keys.joinedClubs, ['1'])
export const setJoinedClubIds = (value: string[]) => write(keys.joinedClubs, value)
export const getPendingClubIds = () => read<string[]>(keys.pendingClubs, [])
export const setPendingClubIds = (value: string[]) => write(keys.pendingClubs, value)
export const getRegistrationIds = () => read<string[]>(keys.registrations, ['1'])
export const setRegistrationIds = (value: string[]) => write(keys.registrations, value)
export const getFavoriteIds = () => read<string[]>(keys.favorites, ['2'])
export const setFavoriteIds = (value: string[]) => write(keys.favorites, value)
export const getAttendanceIds = () => read<string[]>(keys.attendance, [])
export const setAttendanceIds = (value: string[]) => write(keys.attendance, value)
export const getVolunteerHours = () => read<number>(keys.volunteerHours, 3.5)
export const setVolunteerHours = (value: number) => write(keys.volunteerHours, value)
export const getNotifications = () => read(keys.notifications, seedNotifications.map(item => ({ ...item })))
export const setNotifications = (value: ReturnType<typeof getNotifications>) => write(keys.notifications, value)
export const getUserProfile = () => ({ ...defaultUserProfile, ...read<Partial<UserProfile>>(keys.userProfile, {}) })
export const setUserProfile = (value: UserProfile) => write(keys.userProfile, value)

export function addNotification(title: string, content: string, type = '动态') {
  const list = getNotifications()
  list.unshift({ id: `n-${Date.now()}`, title, content, type, read: false, time: '刚刚' })
  setNotifications(list)
}
