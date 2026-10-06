<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { activities } from '@/data/mock'
import { addNotification, getAttendanceIds, getRegistrationIds, getVolunteerHours, setAttendanceIds, setVolunteerHours } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const registrationIds = ref<string[]>([])
const attendanceIds = ref<string[]>([])
const hours = ref(0)
const records = computed(() => activities.filter(item => registrationIds.value.includes(item.id)))
onShow(() => { registrationIds.value = getRegistrationIds(); attendanceIds.value = getAttendanceIds(); hours.value = getVolunteerHours() })
function goBack() { uni.navigateBack() }
function checkIn(event: any) { const id = event.currentTarget.dataset.id; const item = activities.find(activity => activity.id === id); if (!item) return; if (!registrationIds.value.includes(id)) return uni.showToast({ title: '请先报名', icon: 'none' }); if (attendanceIds.value.includes(id)) return uni.showToast({ title: '已完成签到', icon: 'none' }); uni.showModal({ title: '模拟扫码签到', content: `确认在「${item.location}」签到吗？`, success: (result) => { if (!result.confirm) return; attendanceIds.value = [...attendanceIds.value, id]; setAttendanceIds(attendanceIds.value); hours.value = Number((hours.value + 1.5).toFixed(1)); setVolunteerHours(hours.value); addNotification('签到成功', `你已完成「${item.title}」签到，志愿时长 +1.5 小时。`, '签到'); uni.showToast({ title: '签到成功', icon: 'success' }) } }) }
</script>
<template>
  <view class="attendance-page"><view class="page-container" :style="topSafeStyle"><view class="page-nav"><view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view><text>签到记录</text></view><view class="summary-card"><view><text class="summary-number">{{ attendanceIds.length }}</text><text class="summary-label">已签到活动</text></view><view><text class="summary-number">{{ hours }}</text><text class="summary-label">志愿时长 / 小时</text></view></view><view v-if="records.length === 0" class="empty-card surface-card"><UniIcons type="checkmarkempty" size="34" color="#9AA5B8" /><text>报名活动后就可以在这里签到</text></view><view v-else v-for="item in records" :key="item.id" class="attendance-card surface-card"><view><text class="title">{{ item.title }}</text><text class="muted">{{ item.date }} {{ item.time }} · {{ item.location }}</text></view><button v-if="attendanceIds.includes(item.id)" class="done">已签到</button><button v-else class="check-button tap-scale" :data-id="item.id" @tap="checkIn">模拟扫码签到</button><text v-if="attendanceIds.includes(item.id)" class="checked-time">签到完成，记录已保存</text></view></view></view>
</template>
<style lang="scss">
.attendance-page { min-height:100vh; background:var(--app-bg); }.attendance-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }.attendance-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }.attendance-page .summary-card { display:flex; gap:20rpx; padding:28rpx; border-radius:24rpx; color:#fff; background:linear-gradient(135deg,#3978FF,#9385f4); }.attendance-page .summary-card view { flex:1; }.attendance-page .summary-number,.attendance-page .summary-label { display:block; }.attendance-page .summary-number { font-size:42rpx; font-weight:700; }.attendance-page .summary-label { margin-top:6rpx; font-size:22rpx; opacity:.86; }.attendance-page .attendance-card { position:relative; display:flex; align-items:center; justify-content:space-between; margin-top:20rpx; padding:26rpx; }.attendance-page .title { display:block; max-width:410rpx; font-size:27rpx; font-weight:600; }.attendance-page .muted,.attendance-page .checked-time { display:block; max-width:430rpx; margin-top:10rpx; color:var(--app-muted); font-size:21rpx; }.attendance-page button { flex:none; margin:0; padding:0 18rpx; height:58rpx; border-radius:14rpx; font-size:21rpx; }.attendance-page .check-button { color:#fff; background:var(--app-primary); }.attendance-page .done { color:#319b70; background:#e7f8f0; }.attendance-page .checked-time { position:absolute; left:26rpx; bottom:10rpx; color:#319b70; }.attendance-page .empty-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:24rpx; padding:70rpx 20rpx; color:var(--app-muted); font-size:25rpx; }
</style>
