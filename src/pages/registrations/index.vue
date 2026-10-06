<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { activities, clubs } from '@/data/mock'
import { getRegistrationIds, setRegistrationIds } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const activeTab = ref('全部')
const registrationIds = ref<string[]>([])
const tabs = ['全部', '待参加', '已完成']
const records = computed(() => activities.filter(item => registrationIds.value.includes(item.id) && (activeTab.value === '全部' || (activeTab.value === '待参加' ? item.tag !== '已结束' : item.tag === '已结束'))))
onShow(() => { registrationIds.value = getRegistrationIds() })
function goBack() { uni.navigateBack() }
function selectTab(tab: string) { activeTab.value = tab }
function openDetail(event: any) { const id = event.currentTarget.dataset.id; if (id) uni.navigateTo({ url: `/pages/activity-detail/index?id=${id}` }) }
function cancel(event: any) { const id = event.currentTarget.dataset.id; const item = activities.find(activity => activity.id === id); if (!item) return; uni.showModal({ title: '取消报名', content: `确定取消「${item.title}」吗？`, success: (result) => { if (!result.confirm) return; registrationIds.value = registrationIds.value.filter(value => value !== id); setRegistrationIds(registrationIds.value); uni.showToast({ title: '已取消报名', icon: 'none' }) } }) }
function clubName(id: string) { return clubs.find(item => item.id === id)?.name || '校园社团' }
</script>
<template>
  <view class="registrations-page"><view class="page-container" :style="topSafeStyle"><view class="page-nav"><view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view><text>我的报名</text></view><view class="tabs glass-control"><text v-for="tab in tabs" :key="tab" class="tab" :class="{ active: activeTab === tab }" @tap="selectTab(tab)">{{ tab }}</text></view><view v-if="records.length === 0" class="empty-card surface-card"><UniIcons type="calendar" size="34" color="#9AA5B8" /><text>还没有报名活动</text><text class="link" @tap="uni.navigateTo({ url: '/pages/activities/index' })">去发现活动</text></view><view v-else v-for="item in records" :key="item.id" class="record-card surface-card"><view class="record-top"><view><text class="title">{{ item.title }}</text><text class="muted">{{ clubName(item.clubId) }}</text></view><text class="status">已报名</text></view><view class="meta"><text>{{ item.date }} {{ item.time }}</text><text>{{ item.location }}</text></view><view class="record-actions"><button class="secondary" :data-id="item.id" @tap="openDetail">查看详情</button><button class="danger" :data-id="item.id" @tap="cancel">取消报名</button></view></view></view></view>
</template>
<style lang="scss">
.registrations-page { min-height:100vh; background:var(--app-bg); }.registrations-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }.registrations-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }.registrations-page .tabs { display:flex; justify-content:space-around; padding:8rpx; border-radius:18rpx; }.registrations-page .tab { flex:1; padding:16rpx; text-align:center; color:var(--app-muted); font-size:24rpx; }.registrations-page .tab.active { color:var(--app-primary); background:rgba(57,120,255,.1); border-radius:14rpx; }.registrations-page .record-card { margin-top:20rpx; padding:26rpx; }.registrations-page .record-top { display:flex; justify-content:space-between; align-items:flex-start; }.registrations-page .title { display:block; font-size:28rpx; font-weight:600; }.registrations-page .muted,.registrations-page .meta { display:block; margin-top:10rpx; color:var(--app-muted); font-size:22rpx; }.registrations-page .status { color:#3978FF; background:#eaf1ff; border-radius:10rpx; padding:8rpx 12rpx; font-size:21rpx; }.registrations-page .meta { display:flex; justify-content:space-between; padding:18rpx 0; border-bottom:1px solid #edf0f6; }.registrations-page .record-actions { display:flex; justify-content:flex-end; gap:16rpx; margin-top:18rpx; }.registrations-page button { margin:0; padding:0 22rpx; height:60rpx; border-radius:14rpx; font-size:22rpx; }.registrations-page .secondary { color:#3978FF; background:#edf3ff; }.registrations-page .danger { color:#db6370; background:#fff0f1; }.registrations-page .empty-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:24rpx; padding:70rpx 20rpx; color:var(--app-muted); font-size:25rpx; }.registrations-page .link { color:var(--app-primary); font-size:23rpx; }
</style>
