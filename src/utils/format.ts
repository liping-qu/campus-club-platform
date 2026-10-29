/**
 * 时间展示工具。
 *
 * 后端统一返回 "yyyy-MM-dd HH:mm:ss" 字符串，这里直接按字符切分，
 * 不经过 `new Date()` 解析，避免不同端（H5 / 小程序）对时区的处理差异
 * 导致日期整体偏移一天。
 */

export function formatMonthDay(value?: string): string {
  if (!value) return ''
  const datePart = value.split(' ')[0]
  const segments = datePart.split('-')
  if (segments.length < 3) return datePart
  return `${Number(segments[1])}月${Number(segments[2])}日`
}

export function formatClock(value?: string): string {
  if (!value) return ''
  const timePart = value.split(' ')[1]
  return timePart ? timePart.slice(0, 5) : ''
}

export function formatTimeRange(start?: string, end?: string): string {
  const startClock = formatClock(start)
  const endClock = formatClock(end)
  if (!startClock) return ''
  return endClock ? `${startClock}-${endClock}` : startClock
}

export function formatDeadline(value?: string): string {
  const day = formatMonthDay(value)
  const clock = formatClock(value)
  return day && clock ? `${day} ${clock}` : day
}
