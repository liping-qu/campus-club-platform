<script setup lang="ts">
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'

const props = defineProps<{ active: string }>()

const items = [
  { id: 'home', label: '首页', icon: 'home', path: '/pages/index/index' },
  { id: 'clubs', label: '社团', icon: 'list', path: '/pages/clubs/index' },
  { id: 'activities', label: '活动', icon: 'calendar', path: '/pages/activities/index' },
  { id: 'messages', label: '消息', icon: 'chat', path: '/pages/messages/index' },
  { id: 'profile', label: '我的', icon: 'person', path: '/pages/profile/index' }
]

const go = (item: (typeof items)[number]) => {
  if (item.id !== props.active) uni.reLaunch({ url: item.path })
}
</script>

<template>
  <view class="tabbar-shell">
    <view class="tabbar glass-control">
      <view v-for="item in items" :key="item.id" class="tab-item tap-scale" @tap="go(item)">
        <view class="tab-icon" :class="{ selected: item.id === active }">
          <UniIcons :type="item.icon" :size="22" :color="item.id === active ? '#3978FF' : '#7E8AA3'" />
        </view>
        <text class="tab-label" :class="{ selected: item.id === active }">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.tabbar-shell { position: fixed; left: 0; right: 0; bottom: calc(18rpx + env(safe-area-inset-bottom)); z-index: 20; display: flex; justify-content: center; pointer-events: none; }
.tabbar { pointer-events: auto; display: flex; width: calc(100% - 48rpx); max-width: 700rpx; padding: 12rpx 10rpx; border-radius: 30rpx; }
.tab-item { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 5rpx; min-height: 76rpx; }
.tab-icon { width: 48rpx; height: 42rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; }
.tab-icon.selected { background: rgba(57, 120, 255, 0.13); }
.tab-label { font-size: 20rpx; color: var(--app-muted); }.tab-label.selected { color: var(--app-primary); font-weight: 600; }
</style>
