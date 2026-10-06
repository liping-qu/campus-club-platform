<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getNotifications, setNotifications } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const list = ref<any[]>([])
const activeFilter = ref('全部')
const filters = ['全部', '未读']
const unread = computed(() => list.value.filter(item => !item.read).length)
const visibleList = computed(() => activeFilter.value === '未读' ? list.value.filter(item => !item.read) : list.value)

onShow(() => { list.value = getNotifications() })
function selectFilter(filter: string) { activeFilter.value = filter }
function markRead(event: any) {
  const id = event.currentTarget.dataset.id
  list.value = list.value.map(item => item.id === id ? { ...item, read: true } : item)
  setNotifications(list.value)
}
function markAllRead() {
  list.value = list.value.map(item => ({ ...item, read: true }))
  setNotifications(list.value)
  uni.showToast({ title: '已全部读完', icon: 'none' })
}
function iconType(type: string) { return type === '报名' ? 'calendar' : type === '签到' ? 'checkmarkempty' : type === '社团' ? 'staff' : 'chat' }
</script>

<template>
  <view class="messages-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="messages-nav"><view class="title-mark"><UniIcons type="chat" size="22" color="#3978FF" /></view><view class="nav-copy"><text class="nav-title">消息中心</text><text class="nav-subtitle">及时掌握你的校园动态</text></view><text v-if="unread" class="nav-badge">{{ unread }}</text></view>
      <view class="message-summary glass-control"><view class="summary-orb" /><view class="summary-copy"><text class="summary-eyebrow">CAMPUS UPDATE</text><text class="summary-title">今天有 {{ unread }} 条新消息</text><text class="summary-subtitle">报名、签到和社团动态都在这里</text></view><UniIcons type="chat" size="38" color="rgba(255,255,255,.9)" /></view>
      <view class="filter-row"><view class="filters glass-control"><text v-for="filter in filters" :key="filter" class="filter" :class="{ active: activeFilter === filter }" @tap="selectFilter(filter)">{{ filter }}<text v-if="filter === '未读' && unread" class="filter-count">{{ unread }}</text></text></view><text v-if="unread" class="mark-all" @tap="markAllRead">全部已读</text></view>
      <view v-if="visibleList.length === 0" class="empty-card surface-card"><view class="empty-icon"><UniIcons type="checkmarkempty" size="28" color="#3978FF" /></view><text>{{ activeFilter === '未读' ? '没有未读消息' : '暂时没有新消息' }}</text><text class="empty-subtitle">新的校园动态会出现在这里</text></view>
      <view v-else v-for="item in visibleList" :key="item.id" class="message-card surface-card" :class="{ unread: !item.read }" :data-id="item.id" @tap="markRead"><view class="message-icon" :class="{ 'message-icon--unread': !item.read }"><UniIcons :type="iconType(item.type)" size="20" color="#3978FF" /></view><view class="message-copy"><view class="message-title-row"><view class="title-wrap"><text class="message-title">{{ item.title }}</text><text v-if="!item.read" class="new-label">NEW</text></view><text class="message-time">{{ item.time }}</text></view><text class="message-content">{{ item.content }}</text><text class="read-hint">{{ item.read ? '已读' : '点击查看并标记已读' }}</text></view></view>
    </view>
    <AppTabBar active="messages" />
  </view>
</template>

<style lang="scss">
.messages-page { min-height:100vh; padding-bottom:170rpx; background:#f5f7fb; }.messages-page .messages-nav { display:flex; align-items:center; gap:16rpx; padding:10rpx 8rpx 30rpx; }.messages-page .title-mark { flex:none; width:64rpx; height:64rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; background:linear-gradient(145deg,#eaf1ff,#f2edff); box-shadow:0 8rpx 18rpx rgba(57,120,255,.12); }.messages-page .nav-copy { flex:1; min-width:0; }.messages-page .nav-title { display:block; color:#18233d; font-size:36rpx; font-weight:700; }.messages-page .nav-subtitle { display:block; margin-top:6rpx; color:#7e8aa3; font-size:21rpx; }.messages-page .nav-badge { min-width:40rpx; height:40rpx; display:flex; align-items:center; justify-content:center; padding:0 10rpx; border-radius:20rpx; color:#fff; background:#3978ff; font-size:21rpx; }.messages-page .message-summary { position:relative; overflow:hidden; display:flex; align-items:center; justify-content:space-between; min-height:150rpx; padding:24rpx 28rpx; border:0; border-radius:26rpx; color:#fff; background:linear-gradient(135deg,#3978ff,#897df2); box-shadow:0 16rpx 36rpx rgba(57,120,255,.2); }.messages-page .summary-orb { position:absolute; width:210rpx; height:210rpx; right:-70rpx; top:-90rpx; border-radius:50%; background:rgba(255,255,255,.18); }.messages-page .summary-copy { position:relative; z-index:1; }.messages-page .summary-eyebrow { display:block; color:rgba(255,255,255,.7); font-size:18rpx; letter-spacing:3rpx; font-weight:700; }.messages-page .summary-title { display:block; margin-top:12rpx; font-size:32rpx; font-weight:700; }.messages-page .summary-subtitle { display:block; margin-top:8rpx; color:rgba(255,255,255,.78); font-size:21rpx; }.messages-page .filter-row { display:flex; align-items:center; justify-content:space-between; margin:24rpx 0 16rpx; }.messages-page .filters { display:flex; padding:6rpx; border-radius:18rpx; }.messages-page .filter { display:flex; align-items:center; gap:8rpx; padding:13rpx 22rpx; color:#7e8aa3; font-size:23rpx; }.messages-page .filter.active { border-radius:13rpx; color:#3978ff; background:rgba(57,120,255,.12); font-weight:600; }.messages-page .filter-count { min-width:28rpx; height:28rpx; display:flex; align-items:center; justify-content:center; border-radius:14rpx; color:#fff; background:#3978ff; font-size:17rpx; }.messages-page .mark-all { color:#3978ff; font-size:22rpx; }.messages-page .message-card { display:flex; gap:18rpx; margin-top:14rpx; padding:24rpx; border:1px solid rgba(255,255,255,.85); box-shadow:0 8rpx 22rpx rgba(41,69,120,.05); }.messages-page .message-card.unread { border-color:rgba(57,120,255,.25); box-shadow:0 10rpx 26rpx rgba(57,120,255,.1); }.messages-page .message-icon { flex:none; width:62rpx; height:62rpx; display:flex; align-items:center; justify-content:center; border-radius:20rpx; background:#f0f4fb; }.messages-page .message-icon--unread { background:#eaf1ff; }.messages-page .message-copy { flex:1; min-width:0; }.messages-page .message-title-row { display:flex; align-items:center; justify-content:space-between; }.messages-page .title-wrap { display:flex; align-items:center; gap:10rpx; min-width:0; }.messages-page .message-title { color:#18233d; font-size:28rpx; font-weight:650; }.messages-page .new-label { padding:4rpx 8rpx; border-radius:7rpx; color:#3978ff; background:#eaf1ff; font-size:16rpx; font-weight:700; }.messages-page .message-time { flex:none; margin-left:12rpx; color:#9aa5b8; font-size:20rpx; }.messages-page .message-content { display:block; margin-top:12rpx; color:#687792; font-size:23rpx; line-height:1.55; }.messages-page .read-hint { display:block; margin-top:12rpx; color:#a3adc0; font-size:19rpx; }.messages-page .empty-card { display:flex; flex-direction:column; align-items:center; gap:12rpx; margin-top:20rpx; padding:72rpx 20rpx; color:#687792; font-size:25rpx; }.messages-page .empty-icon { width:70rpx; height:70rpx; display:flex; align-items:center; justify-content:center; border-radius:24rpx; background:#eaf1ff; }.messages-page .empty-subtitle { color:#a3adc0; font-size:21rpx; }
</style>
