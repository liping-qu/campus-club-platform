<script setup lang="ts">
import { computed, ref } from 'vue'
import { activities } from '@/data/mock'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const query = ref('')
const activeStatus = ref('全部')
const statuses = ['全部', '报名中', '即将开始', '已结束']
const filteredActivities = computed(() => activities.filter(activity => {
  const keyword = query.value.trim().toLowerCase()
  const hitKeyword = !keyword || `${activity.title}${activity.location}${activity.tag}`.toLowerCase().includes(keyword)
  const hitStatus = activeStatus.value === '全部' || (activeStatus.value === '报名中' ? activity.tag === '报名中' : activeStatus.value === '即将开始' ? activity.tag === '即将开始' || activity.tag === '即将满员' : activity.tag === activeStatus.value)
  return hitKeyword && hitStatus
}))
function openActivity(event: any) {
  const id = event.currentTarget.dataset.id
  if (id) uni.navigateTo({ url: `/pages/activity-detail/index?id=${id}` })
}
function selectStatus(status: string) { activeStatus.value = status }
function clearSearch() { query.value = '' }
</script>

<template>
  <view class="activities-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="page-heading rise-in"><text class="eyebrow">CAMPUS EVENTS</text><text class="page-title">校园活动</text><text class="page-subtitle">把喜欢的事，变成一次真实相遇</text></view>
      <view class="search-box glass-control"><UniIcons type="search" size="20" color="#7E8AA3" /><input v-model="query" class="search-input" placeholder="搜索活动名称或地点" /><text v-if="query" class="clear-search" @tap="clearSearch">清除</text></view>
      <view class="tabs glass-control"><text v-for="status in statuses" :key="status" class="tab" :class="{ active: activeStatus === status }" @tap="selectStatus(status)">{{ status }}</text></view>
      <view v-if="filteredActivities.length === 0" class="state-card surface-card"><UniIcons type="calendar" size="32" color="#9AA5B8" /><text>暂时没有匹配的活动</text><text class="state-link" @tap="clearSearch">清除搜索条件</text></view>
      <view v-else v-for="activity in filteredActivities" :key="activity.id" class="activity-card surface-card tap-scale" :data-id="activity.id" @tap="openActivity">
        <view class="cover"><text>活动</text><view class="cover-glow" /></view>
        <view class="info"><text class="title">{{ activity.title }}</text><view class="meta"><UniIcons type="location" size="14" color="#7E8AA3" /><text class="muted">{{ activity.location }}</text></view><view class="meta"><UniIcons type="calendar" size="14" color="#7E8AA3" /><text class="muted">{{ activity.date }} {{ activity.time }}</text></view><view class="bottom"><text class="tag">{{ activity.tag }}</text><text class="muted">{{ activity.joined }}/{{ activity.capacity }}人</text></view></view>
      </view>
    </view>
    <AppTabBar active="activities" />
  </view>
</template>

<style lang="scss">
.activities-page { min-height:100vh; background:var(--app-bg); }.activities-page .page-heading { padding:8rpx 0 28rpx; }.activities-page .eyebrow { display:block; color:var(--app-primary); font-size:20rpx; letter-spacing:3rpx; font-weight:700; }.activities-page .page-title { display:block; margin-top:14rpx; color:var(--app-text); font-size:48rpx; font-weight:700; }.activities-page .page-subtitle { display:block; margin-top:8rpx; color:var(--app-muted); font-size:25rpx; }.activities-page .search-box { display:flex; align-items:center; gap:12rpx; padding:18rpx 24rpx; border-radius:20rpx; }.activities-page .search-input { flex:1; height:46rpx; color:var(--app-text); font-size:26rpx; }.activities-page .clear-search,.activities-page .state-link { color:var(--app-primary); font-size:23rpx; }.activities-page .tabs { display:flex; justify-content:space-between; margin:24rpx 0; padding:8rpx 12rpx; border-radius:18rpx; }.activities-page .tab { color:var(--app-muted); font-size:24rpx; padding:14rpx 14rpx; }.activities-page .tab.active { color:var(--app-primary); font-weight:600; background:rgba(57,120,255,.12); border-radius:14rpx; }.activities-page .activity-card { display:flex; padding:20rpx; margin-bottom:18rpx; }.activities-page .cover { position:relative; overflow:hidden; width:190rpx; height:180rpx; border-radius:16rpx; background:linear-gradient(140deg,#c8d9ff,#789ef3 55%,#ae91f5); color:#fff; display:flex; align-items:center; justify-content:center; font-size:30rpx; font-weight:600; }.activities-page .cover-glow { position:absolute; width:130rpx; height:130rpx; right:-40rpx; bottom:-50rpx; border-radius:50%; background:rgba(255,255,255,.3); filter:blur(10px); }.activities-page .info { flex:1; margin-left:22rpx; min-width:0; }.activities-page .title { display:block; font-size:29rpx; font-weight:600; margin:8rpx 0 18rpx; }.activities-page .meta { display:flex; align-items:center; gap:6rpx; margin-bottom:10rpx; }.activities-page .muted { font-size:23rpx; color:var(--app-muted); }.activities-page .bottom { display:flex; justify-content:space-between; align-items:center; margin-top:12rpx; }.activities-page .tag { color:var(--app-success); background:#e7f8f0; border-radius:10rpx; padding:6rpx 12rpx; font-size:22rpx; }.activities-page .state-card { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:16rpx; min-height:240rpx; color:var(--app-muted); font-size:25rpx; }
</style>
