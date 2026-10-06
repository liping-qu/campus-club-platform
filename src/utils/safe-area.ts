import { ref } from 'vue'

type WindowMetrics = {
  statusBarHeight?: number
  safeAreaInsets?: { top?: number }
  safeArea?: { top?: number }
}

type MenuMetrics = { top?: number; bottom?: number; height?: number }

function finite(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

export function calculateTopInset(windowMetrics: WindowMetrics = {}, menu?: MenuMetrics): number {
  const status = finite(windowMetrics.statusBarHeight) ? windowMetrics.statusBarHeight : 0
  const safeTop = finite(windowMetrics.safeAreaInsets?.top)
    ? windowMetrics.safeAreaInsets!.top!
    : finite(windowMetrics.safeArea?.top)
      ? windowMetrics.safeArea!.top!
      : 0
  const screenTop = Math.max(status, safeTop, 0)

  if (menu && finite(menu.top) && finite(menu.bottom) && menu.bottom > menu.top && menu.bottom >= screenTop) {
    const breathingRoom = Math.max(12, Math.min(22, menu.top - screenTop + 8))
    return Math.ceil(menu.bottom + breathingRoom)
  }
  return screenTop > 0 ? Math.ceil(screenTop + 18) : 0
}

function readWindowMetrics(): WindowMetrics {
  try {
    if (typeof uni.getWindowInfo === 'function') return uni.getWindowInfo() as WindowMetrics
    if (typeof uni.getSystemInfoSync === 'function') return uni.getSystemInfoSync() as WindowMetrics
  } catch (error) {
    console.warn('[safe-area] unable to read window metrics', error)
  }
  return {}
}

function readMenuMetrics(): MenuMetrics | undefined {
  try {
    if (typeof uni.getMenuButtonBoundingClientRect === 'function') {
      return uni.getMenuButtonBoundingClientRect() as MenuMetrics
    }
  } catch (error) {
    console.warn('[safe-area] unable to read menu button metrics', error)
  }
  return undefined
}

export function usePageSafeArea() {
  const topSafeStyle = ref('')
  const refresh = () => {
    const inset = calculateTopInset(readWindowMetrics(), readMenuMetrics())
    topSafeStyle.value = inset > 0 ? `padding-top: ${inset}px` : ''
  }
  // Read synchronously during setup so a runtime lifecycle-hook difference in
  // an older developer-tool base library can never prevent the first render.
  refresh()
  return { topSafeStyle, refresh }
}
