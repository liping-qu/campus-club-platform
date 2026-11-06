<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  fetchMyAttendances,
  METHOD_LABELS,
  type MyAttendanceItem,
  type MyAttendanceSummary
} from '@/api/attendance'
import { ApiError } from '@/utils/request'
import { formatDeadline, formatMonthDay, formatTimeRange } from '@/utils/format'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

/**
 * 这一页只读：签到动作发生在活动详情页。
 * 数据全部来自 /api/attendances/me —— 累计时长也由后端算，
 * 前端不再自己把列表加一遍（口径只能有一处，见 README「签到」一节）。
 */
const summary = ref<MyAttendanceSummary>({ totalHours: 0, count: 0, records: [] })
const loading = ref(false)
const loadError = ref('')

const records = computed(() => summary.value.records)
const totalHours = computed(() => Number(summary.value.totalHours || 0).toFixed(1))

onShow(() => load())

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    summary.value = await fetchMyAttendances()
  } catch (error) {
    loadError.value = (error as ApiError)?.message || '签到记录加载失败'
    summary.value = { totalHours: 0, count: 0, records: [] }
  } finally {
    loading.value = false
  }
}

function goBack() { uni.navigateBack() }
function goActivities() { uni.navigateTo({ url: '/pages/activities/index' }) }

function openDetail(event: any) {
  const id = event.currentTarget.dataset.id
  if (id) uni.navigateTo({ url: `/pages/activity-detail/index?id=${id}` })
}

function showActivityTime(item: MyAttendanceItem) {
  return `${formatMonthDay(item.startTime)} ${formatTimeRange(item.startTime, item.endTime)}`
}

function showHours(hours: number) {
  return `${Number(hours || 0).toFixed(1)} 小时`
}
</script>
<template>
  <view class="attendance-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="page-nav">
        <view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view>
        <text>志愿时长</text>
      </view>

      <view class="summary-card">
        <view>
          <text class="summary-number">{{ totalHours }}</text>
          <text class="summary-label">累计志愿时长 / 小时</text>
        </view>
        <view>
          <text class="summary-number">{{ summary.count }}</text>
          <text class="summary-label">已签到活动</text>
        </view>
      </view>

      <view v-if="loading" class="empty-card surface-card"><text>加载中…</text></view>
      <view v-else-if="loadError" class="empty-card surface-card">
        <text>{{ loadError }}</text>
        <text class="link" @tap="load">重新加载</text>
      </view>
      <view v-else-if="records.length === 0" class="empty-card surface-card">
        <UniIcons type="checkmarkempty" size="34" color="#9AA5B8" />
        <text>还没有签到记录</text>
        <text class="link" @tap="goActivities">去发现活动</text>
      </view>

      <template v-else>
        <view v-for="item in records" :key="item.activityId" class="attendance-card surface-card" :data-id="item.activityId" @tap="openDetail">
          <view class="record-top">
            <view>
              <text class="title">{{ item.title }}</text>
              <text class="muted">{{ item.clubName }} · {{ item.location }}</text>
            </view>
            <text class="hours">{{ showHours(item.volunteerHours) }}</text>
          </view>
          <view class="meta">
            <text>活动时间 {{ showActivityTime(item) }}</text>
            <text class="method">{{ METHOD_LABELS[item.method] || item.method }}</text>
          </view>
          <text class="checked-time">签到于 {{ formatDeadline(item.checkedInAt) }}</text>
        </view>
      </template>
    </view>
  </view>
</template>
<style lang="scss">
.attendance-page { min-height:100vh; background:var(--app-bg); }
.attendance-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }
.attendance-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }
.attendance-page .summary-card { display:flex; gap:20rpx; padding:28rpx; border-radius:24rpx; color:#fff; background:linear-gradient(135deg,#3978FF,#9385f4); }
.attendance-page .summary-card view { flex:1; }
.attendance-page .summary-number,.attendance-page .summary-label { display:block; }
.attendance-page .summary-number { font-size:42rpx; font-weight:700; }
.attendance-page .summary-label { margin-top:6rpx; font-size:22rpx; opacity:.86; }
.attendance-page .attendance-card { margin-top:20rpx; padding:26rpx; }
.attendance-page .record-top { display:flex; justify-content:space-between; align-items:flex-start; gap:18rpx; }
.attendance-page .title { display:block; max-width:430rpx; font-size:27rpx; font-weight:600; }
.attendance-page .hours { flex:none; color:#3978FF; background:#eaf1ff; border-radius:12rpx; padding:8rpx 12rpx; font-size:21rpx; }
.attendance-page .muted { display:block; max-width:430rpx; margin-top:10rpx; color:var(--app-muted); font-size:21rpx; }
.attendance-page .meta { display:flex; justify-content:space-between; align-items:center; margin-top:18rpx; padding-top:18rpx; border-top:1px solid #edf0f6; color:var(--app-muted); font-size:21rpx; }
.attendance-page .method { color:#7b61d8; }
.attendance-page .checked-time { display:block; margin-top:12rpx; color:#319b70; font-size:21rpx; }
.attendance-page .empty-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:24rpx; padding:70rpx 20rpx; color:var(--app-muted); font-size:25rpx; }
.attendance-page .link { color:var(--app-primary); font-size:23rpx; }
</style>
