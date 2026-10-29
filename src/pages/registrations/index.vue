<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  cancelRegistration as cancelRegistrationApi,
  fetchMyRegistrations,
  type RegistrationItem
} from '@/api/registration'
import { ApiError } from '@/utils/request'
import { formatMonthDay, formatTimeRange } from '@/utils/format'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const activeTab = ref('全部')
const tabs = ['全部', '待参加', '已完成']
const records = ref<RegistrationItem[]>([])
const loading = ref(false)
const loadError = ref('')
const cancelingId = ref<number | null>(null)

/**
 * 记录来自后端 /api/registrations/me，
 * 分类沿用后端推导出的展示阶段（FINISHED 即已完成），前端不再自己比时间。
 */
const visibleRecords = computed(() => records.value.filter((item) => {
  if (activeTab.value === '待参加') return item.phase !== 'FINISHED'
  if (activeTab.value === '已完成') return item.phase === 'FINISHED'
  return true
}))

onShow(() => load())

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    records.value = await fetchMyRegistrations()
  } catch (error) {
    loadError.value = (error as ApiError)?.message || '报名记录加载失败'
    records.value = []
  } finally {
    loading.value = false
  }
}

function goBack() { uni.navigateBack() }
function goActivities() { uni.navigateTo({ url: '/pages/activities/index' }) }
function selectTab(tab: string) { activeTab.value = tab }

function openDetail(event: any) {
  const id = event.currentTarget.dataset.id
  if (id) uni.navigateTo({ url: `/pages/activity-detail/index?id=${id}` })
}

function cancel(event: any) {
  const id = Number(event.currentTarget.dataset.id)
  const item = records.value.find(record => record.id === id)
  if (!item) return
  uni.showModal({
    title: '取消报名',
    content: `确定取消「${item.title}」吗？`,
    success: (result) => {
      if (result.confirm) doCancel(id)
    }
  })
}

async function doCancel(activityId: number) {
  cancelingId.value = activityId
  try {
    await cancelRegistrationApi(activityId)
    uni.showToast({ title: '已取消报名', icon: 'none' })
    await load()
  } catch (error) {
    const apiError = error as ApiError
    // 并发/重复取消：服务端说没报名，把列表刷新成真实状态即可
    uni.showToast({ title: apiError?.message || '取消失败', icon: 'none' })
    await load()
  } finally {
    cancelingId.value = null
  }
}

function showTime(item: RegistrationItem) {
  return `${formatMonthDay(item.startTime)} ${formatTimeRange(item.startTime, item.endTime)}`
}
</script>
<template>
  <view class="registrations-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="page-nav">
        <view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view>
        <text>我的报名</text>
      </view>
      <view class="tabs glass-control">
        <text
          v-for="tab in tabs"
          :key="tab"
          class="tab"
          :class="{ active: activeTab === tab }"
          @tap="selectTab(tab)"
        >{{ tab }}</text>
      </view>
      <view v-if="loading" class="empty-card surface-card"><text>加载中…</text></view>
      <view v-else-if="loadError" class="empty-card surface-card">
        <text>{{ loadError }}</text>
        <text class="link" @tap="load">重新加载</text>
      </view>
      <view v-else-if="visibleRecords.length === 0" class="empty-card surface-card">
        <UniIcons type="calendar" size="34" color="#9AA5B8" />
        <text>还没有报名活动</text>
        <text class="link" @tap="goActivities">去发现活动</text>
      </view>
      <view v-else v-for="item in visibleRecords" :key="item.registrationId" class="record-card surface-card">
        <view class="record-top">
          <view>
            <text class="title">{{ item.title }}</text>
            <text class="muted">{{ item.clubName }}</text>
          </view>
          <text class="status">{{ item.phaseText }}</text>
        </view>
        <view class="meta">
          <text>{{ showTime(item) }}</text>
          <text>{{ item.location }}</text>
        </view>
        <view class="record-actions">
          <button class="secondary" :data-id="item.id" @tap="openDetail">查看详情</button>
          <button class="danger" :data-id="item.id" :disabled="cancelingId === item.id" @tap="cancel">取消报名</button>
        </view>
      </view>
    </view>
  </view>
</template>
<style lang="scss">
.registrations-page { min-height:100vh; background:var(--app-bg); }.registrations-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }.registrations-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }.registrations-page .tabs { display:flex; justify-content:space-around; padding:8rpx; border-radius:18rpx; }.registrations-page .tab { flex:1; padding:16rpx; text-align:center; color:var(--app-muted); font-size:24rpx; }.registrations-page .tab.active { color:var(--app-primary); background:rgba(57,120,255,.1); border-radius:14rpx; }.registrations-page .record-card { margin-top:20rpx; padding:26rpx; }.registrations-page .record-top { display:flex; justify-content:space-between; align-items:flex-start; }.registrations-page .title { display:block; font-size:28rpx; font-weight:600; }.registrations-page .muted,.registrations-page .meta { display:block; margin-top:10rpx; color:var(--app-muted); font-size:22rpx; }.registrations-page .status { color:#3978FF; background:#eaf1ff; border-radius:10rpx; padding:8rpx 12rpx; font-size:21rpx; }.registrations-page .meta { display:flex; justify-content:space-between; padding:18rpx 0; border-bottom:1px solid #edf0f6; }.registrations-page .record-actions { display:flex; justify-content:flex-end; gap:16rpx; margin-top:18rpx; }.registrations-page button { margin:0; padding:0 22rpx; height:60rpx; border-radius:14rpx; font-size:22rpx; }.registrations-page .secondary { color:#3978FF; background:#edf3ff; }.registrations-page .danger { color:#db6370; background:#fff0f1; }.registrations-page .empty-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:24rpx; padding:70rpx 20rpx; color:var(--app-muted); font-size:25rpx; }.registrations-page .link { color:var(--app-primary); font-size:23rpx; }
</style>
