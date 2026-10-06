<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { activities, clubs } from '@/data/mock'
import { addNotification, getFavoriteIds, getRegistrationIds, setFavoriteIds, setRegistrationIds } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const activityId = ref('')
const activity = computed(() => activities.find(item => item.id === activityId.value) || activities[0])
const club = computed(() => clubs.find(item => item.id === activity.value.clubId) || clubs[0])
const registrationIds = ref<string[]>([])
const favoriteIds = ref<string[]>([])
const registered = computed(() => registrationIds.value.includes(activity.value.id))
const favorite = computed(() => favoriteIds.value.includes(activity.value.id))
const full = computed(() => activity.value.joined >= activity.value.capacity && !registered.value)
const spots = computed(() => Math.max(0, activity.value.capacity - activity.value.joined))

onLoad((options) => { activityId.value = String(options?.id || '1'); refresh() })
onShow(() => refresh())
function refresh() { registrationIds.value = getRegistrationIds(); favoriteIds.value = getFavoriteIds() }
function goBack() { uni.navigateBack() }
function openClub() { uni.navigateTo({ url: `/pages/club-detail/index?id=${club.value.id}` }) }
function toggleFavorite() { favoriteIds.value = favorite.value ? favoriteIds.value.filter(id => id !== activity.value.id) : [...favoriteIds.value, activity.value.id]; setFavoriteIds(favoriteIds.value); uni.showToast({ title: favorite.value ? '已收藏' : '已取消收藏', icon: 'none' }) }
function register() {
  if (activity.value.tag === '已结束') return uni.showToast({ title: '活动已结束', icon: 'none' })
  if (registered.value || full.value) return
  uni.showModal({ title: '确认报名', content: `确定报名「${activity.value.title}」吗？`, success: (result) => { if (!result.confirm) return; registrationIds.value = [...registrationIds.value, activity.value.id]; setRegistrationIds(registrationIds.value); addNotification('报名成功', `你已成功报名「${activity.value.title}」。`, '报名'); uni.showToast({ title: '报名成功', icon: 'success' }) } })
}
function cancelRegistration() { uni.showModal({ title: '取消报名', content: '确定取消这次活动报名吗？', success: (result) => { if (!result.confirm) return; registrationIds.value = registrationIds.value.filter(id => id !== activity.value.id); setRegistrationIds(registrationIds.value); uni.showToast({ title: '已取消报名', icon: 'none' }) } }) }
function joinWaitlist() { uni.showModal({ title: '候补报名', content: '名额已满，确认加入候补名单吗？', success: (result) => { if (result.confirm) uni.showToast({ title: '已加入候补', icon: 'success' }) } }) }
function openMap() { uni.showToast({ title: '地图入口演示', icon: 'none' }) }
</script>
<template>
  <view class="activity-detail-page"><view class="page-container" :style="topSafeStyle">
    <view class="detail-nav"><view class="back-button glass-control tap-scale" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view><text>活动详情</text><view class="nav-spacer" /></view>
    <view class="activity-hero"><view class="hero-glow" /><text class="hero-label">校园活动</text><text class="hero-title">{{ activity.title }}</text><text class="hero-club" @tap="openClub">{{ club.name }} · {{ activity.tag }}</text></view>
    <view class="detail-card surface-card"><view class="meta-line"><UniIcons type="calendar" size="18" color="#3978FF" /><text>{{ activity.date }} {{ activity.time }}</text></view><view class="meta-line"><UniIcons type="location" size="18" color="#3978FF" /><text>{{ activity.location }}</text><text class="map-link" @tap="openMap">查看地图</text></view><view class="capacity-line"><text>报名进度</text><text>{{ activity.joined }}/{{ activity.capacity }} 人 · 剩余 {{ spots }} 名</text></view><view class="progress"><view class="progress-inner" :style="{ width: `${Math.min(100, activity.joined / activity.capacity * 100)}%` }" /></view><text class="deadline">报名截止：{{ activity.deadline }}</text></view>
    <view class="detail-card surface-card"><text class="section-title">活动介绍</text><text class="body-copy">{{ activity.description }}</text><text class="section-title rules-title">活动规则</text><view v-for="rule in activity.rules" :key="rule" class="rule-line"><UniIcons type="checkmarkempty" size="18" color="#3978FF" /><text>{{ rule }}</text></view></view>
    <view class="action-bar glass-control"><button class="favorite-button tap-scale" @tap="toggleFavorite"><UniIcons :type="favorite ? 'star-filled' : 'star'" size="19" color="#3978FF" />{{ favorite ? '已收藏' : '收藏活动' }}</button><button v-if="registered" class="primary-button tap-scale" @tap="cancelRegistration">取消报名</button><button v-else-if="full" class="primary-button tap-scale" @tap="joinWaitlist">候补报名</button><button v-else class="primary-button tap-scale" @tap="register">立即报名</button></view>
  </view></view>
</template>
<style lang="scss">
.activity-detail-page { min-height:100vh; background:var(--app-bg); padding-bottom:40rpx; }.activity-detail-page .detail-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 24rpx; font-size:32rpx; font-weight:700; }.activity-detail-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }.activity-detail-page .nav-spacer { flex:1; }.activity-detail-page .activity-hero { position:relative; overflow:hidden; min-height:260rpx; padding:32rpx; border-radius:26rpx; color:#fff; background:linear-gradient(140deg,#3978FF,#8f7df2); }.activity-detail-page .hero-glow { position:absolute; width:260rpx; height:260rpx; right:-80rpx; top:-90rpx; border-radius:50%; background:rgba(255,255,255,.18); }.activity-detail-page .hero-label,.activity-detail-page .hero-club { display:block; font-size:23rpx; opacity:.85; }.activity-detail-page .hero-title { display:block; max-width:580rpx; margin-top:38rpx; font-size:40rpx; font-weight:700; line-height:1.3; }.activity-detail-page .hero-club { margin-top:18rpx; }.activity-detail-page .detail-card { margin-top:20rpx; padding:28rpx; }.activity-detail-page .meta-line,.activity-detail-page .capacity-line { display:flex; align-items:center; gap:12rpx; margin-bottom:22rpx; color:var(--app-text); font-size:25rpx; }.activity-detail-page .map-link { margin-left:auto; color:var(--app-primary); font-size:22rpx; }.activity-detail-page .capacity-line { justify-content:space-between; color:var(--app-muted); font-size:23rpx; margin-top:26rpx; margin-bottom:12rpx; }.activity-detail-page .progress { height:12rpx; overflow:hidden; border-radius:10rpx; background:#e8edf7; }.activity-detail-page .progress-inner { height:100%; border-radius:10rpx; background:linear-gradient(90deg,#3978FF,#9a86f5); }.activity-detail-page .deadline { display:block; margin-top:16rpx; color:var(--app-muted); font-size:22rpx; }.activity-detail-page .section-title { display:block; color:var(--app-text); font-size:30rpx; font-weight:700; }.activity-detail-page .body-copy { display:block; margin-top:18rpx; color:var(--app-muted); font-size:25rpx; line-height:1.7; }.activity-detail-page .rules-title { margin-top:28rpx; }.activity-detail-page .rule-line { display:flex; gap:12rpx; margin-top:18rpx; color:var(--app-text); font-size:24rpx; }.activity-detail-page .action-bar { position:sticky; bottom:24rpx; display:flex; gap:16rpx; margin-top:24rpx; padding:14rpx; border-radius:22rpx; }.activity-detail-page .action-bar button { flex:1; display:flex; align-items:center; justify-content:center; gap:8rpx; height:82rpx; margin:0; border:0; border-radius:18rpx; font-size:25rpx; }.activity-detail-page .favorite-button { color:var(--app-primary); background:rgba(57,120,255,.1); }.activity-detail-page .primary-button { color:#fff; background:var(--app-primary); }
</style>
