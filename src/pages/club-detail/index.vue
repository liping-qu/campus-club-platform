<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { clubs, activities } from '@/data/mock'
import { addNotification, getJoinedClubIds, getPendingClubIds, setPendingClubIds } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const clubId = ref('')
const club = computed(() => clubs.find(item => item.id === clubId.value) || clubs[0])
const clubActivities = computed(() => activities.filter(item => item.clubId === club.value.id))
const joinedIds = ref<string[]>([])
const pendingIds = ref<string[]>([])
const membershipStatus = computed(() => joinedIds.value.includes(club.value.id) ? '已加入' : pendingIds.value.includes(club.value.id) ? '审核中' : '申请加入')

onLoad((options) => { clubId.value = String(options?.id || '1'); joinedIds.value = getJoinedClubIds(); pendingIds.value = getPendingClubIds() })
function goBack() { uni.navigateBack() }
function openActivity(event: any) { const id = event.currentTarget.dataset.id; if (id) uni.navigateTo({ url: `/pages/activity-detail/index?id=${id}` }) }
function applyJoin() {
  if (membershipStatus.value !== '申请加入') return
  uni.showModal({ title: '申请加入社团', content: `确认申请加入「${club.value.name}」吗？`, success: (result) => {
    if (!result.confirm) return
    if (!pendingIds.value.includes(club.value.id)) pendingIds.value = [...pendingIds.value, club.value.id]
    setPendingClubIds(pendingIds.value)
    addNotification('社团申请已提交', `你已提交加入「${club.value.name}」的申请，等待负责人审核。`, '社团')
    uni.showToast({ title: '已提交申请', icon: 'success' })
  } })
}
</script>
<template>
  <view class="club-detail-page"><view class="page-container" :style="topSafeStyle">
    <view class="detail-nav"><view class="back-button glass-control tap-scale" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view><text>社团详情</text></view>
    <view class="club-hero" :style="{ background: `linear-gradient(135deg, ${club.color}, #3978FF)` }"><view class="hero-orb" /><view class="club-avatar"><UniIcons type="star" size="34" color="#3978FF" /></view><view class="hero-copy"><text class="hero-title">{{ club.name }}</text><text class="hero-subtitle">{{ club.category }} · {{ club.members }} 名成员</text></view></view>
    <view class="detail-card surface-card"><view class="title-row"><text class="section-title">关于社团</text><text class="status-pill">{{ membershipStatus }}</text></view><text class="body-copy">{{ club.description }}</text><view class="tag-list"><text v-for="tag in club.tags" :key="tag" class="tag">{{ tag }}</text></view><view class="info-grid"><view><text class="info-label">负责人</text><text class="info-value">{{ club.owner }}</text></view><view><text class="info-label">联系方式</text><text class="info-value">{{ club.contact }}</text></view></view><button class="primary-button tap-scale" :class="{ joined: membershipStatus !== '申请加入' }" @tap="applyJoin">{{ membershipStatus }}</button></view>
    <view class="detail-card surface-card"><text class="section-title">社团活动</text><view v-if="clubActivities.length === 0" class="empty-line">暂时没有活动</view><view v-for="item in clubActivities" :key="item.id" class="mini-activity" :data-id="item.id" @tap="openActivity"><view><text class="mini-title">{{ item.title }}</text><text class="muted">{{ item.date }} · {{ item.location }}</text></view><UniIcons type="right" size="18" color="#9AA5B8" /></view></view>
    <view class="detail-card surface-card"><text class="section-title">社团特色与荣誉</text><view v-for="honor in club.honors" :key="honor" class="honor-line"><UniIcons type="checkmarkempty" size="18" color="#3978FF" /><text>{{ honor }}</text></view></view>
  </view></view>
</template>
<style lang="scss">
.club-detail-page { min-height:100vh; background:var(--app-bg); }.club-detail-page .detail-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 24rpx; font-size:32rpx; font-weight:700; }.club-detail-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }.club-detail-page .club-hero { position:relative; overflow:hidden; display:flex; align-items:center; padding:36rpx 30rpx; border-radius:26rpx; color:#fff; }.club-detail-page .hero-orb { position:absolute; width:240rpx; height:240rpx; right:-60rpx; top:-80rpx; border-radius:50%; background:rgba(255,255,255,.2); }.club-detail-page .club-avatar { width:112rpx; height:112rpx; border-radius:34rpx; display:flex; align-items:center; justify-content:center; background:rgba(255,255,255,.9); }.club-detail-page .hero-copy { margin-left:24rpx; }.club-detail-page .hero-title { display:block; font-size:36rpx; font-weight:700; }.club-detail-page .hero-subtitle { display:block; margin-top:12rpx; font-size:23rpx; opacity:.86; }.club-detail-page .detail-card { margin-top:20rpx; padding:28rpx; }.club-detail-page .title-row { display:flex; align-items:center; justify-content:space-between; }.club-detail-page .section-title { font-size:30rpx; font-weight:700; color:var(--app-text); }.club-detail-page .status-pill,.club-detail-page .tag { color:var(--app-primary); background:rgba(57,120,255,.1); border-radius:12rpx; padding:8rpx 14rpx; font-size:22rpx; }.club-detail-page .body-copy { display:block; margin-top:18rpx; color:var(--app-muted); font-size:25rpx; line-height:1.7; }.club-detail-page .tag-list { display:flex; flex-wrap:wrap; gap:12rpx; margin-top:18rpx; }.club-detail-page .info-grid { display:flex; gap:40rpx; margin-top:26rpx; }.club-detail-page .info-grid view { flex:1; }.club-detail-page .info-label,.club-detail-page .info-value { display:block; }.club-detail-page .info-label { color:var(--app-muted); font-size:22rpx; }.club-detail-page .info-value { margin-top:8rpx; color:var(--app-text); font-size:24rpx; }.club-detail-page .primary-button { margin:28rpx 0 0; border:0; border-radius:18rpx; background:var(--app-primary); color:#fff; font-size:28rpx; }.club-detail-page .primary-button.joined { background:#b9c7e8; }.club-detail-page .mini-activity,.club-detail-page .honor-line { display:flex; align-items:center; justify-content:space-between; padding:22rpx 0; border-bottom:1px solid #edf0f6; }.club-detail-page .mini-activity:last-child,.club-detail-page .honor-line:last-child { border-bottom:0; }.club-detail-page .mini-title { display:block; color:var(--app-text); font-size:26rpx; }.club-detail-page .muted,.club-detail-page .empty-line { display:block; margin-top:8rpx; color:var(--app-muted); font-size:22rpx; }.club-detail-page .honor-line { justify-content:flex-start; gap:12rpx; color:var(--app-text); font-size:24rpx; }
</style>
