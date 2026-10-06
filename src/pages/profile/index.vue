<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { activities } from '@/data/mock'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { usePageSafeArea } from '@/utils/safe-area'
import { getUserProfile } from '@/utils/mock-state'

const { topSafeStyle } = usePageSafeArea()
const userProfile = ref(getUserProfile())

onShow(() => {
  userProfile.value = getUserProfile()
})

const firstActivity = activities[0]
const secondActivity = activities[1]
const firstDate = firstActivity ? firstActivity.date.replace('月', '/') : '10/12'
const firstTime = firstActivity ? firstActivity.time.split('-')[0] : '09:00'
const firstTitle = firstActivity?.title || '校园环保志愿行动'
const secondDate = secondActivity ? secondActivity.date.replace('月', '/') : '10/13'
const secondTime = secondActivity ? secondActivity.time.split('-')[0] : '14:00'
const secondTitle = secondActivity?.title || '秋日校园摄影采风'

const showFeature = (label: string) => uni.showToast({ title: `${label}功能即将开放`, icon: 'none' })
const editProfile = () => uni.navigateTo({ url: '/pages/profile-edit/index' })
const openRegistrations = () => uni.navigateTo({ url: '/pages/registrations/index' })
const openClubs = () => uni.navigateTo({ url: '/pages/clubs/index' })
const openAttendance = () => uni.navigateTo({ url: '/pages/attendance/index' })
const openFavorites = () => uni.navigateTo({ url: '/pages/favorites/index' })
const openHistory = () => showFeature('全部动态')
const openActivity = () => uni.navigateTo({ url: `/pages/activity-detail/index?id=${firstActivity?.id || '1'}` })
const openSettings = () => showFeature('设置')
const openHelp = () => showFeature('帮助与反馈')
const openMessages = () => uni.navigateTo({ url: '/pages/messages/index' })
</script>

<template>
  <view class="profile-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="profile-hero rise-in">
        <view class="profile-hero__glow profile-hero__glow--one" />
        <view class="profile-hero__glow profile-hero__glow--two" />
        <view class="profile-hero__top">
          <view class="avatar-wrap"><view class="avatar">{{ userProfile.nickname.slice(0, 1) }}</view><view class="online-dot" /></view>
          <view class="profile-copy"><text class="eyebrow">MY CAMPUS</text><text class="name">{{ userProfile.nickname }}你好</text><text class="profile-meta">{{ userProfile.college }} · {{ userProfile.grade }}</text></view>
          <view class="edit-button tap-scale" @tap="editProfile"><UniIcons type="compose" size="17" color="#3978FF" /><text>编辑</text></view>
        </view>
        <view class="profile-hero__bottom"><view class="identity-tag"><UniIcons type="medal" size="14" color="#FFE7A6" /><text>校园活跃用户</text></view><text class="hero-hint">完善资料，发现更多志同道合的伙伴</text></view>
      </view>

      <view class="stats-grid">
        <view class="stat-card surface-card tap-scale"><view class="stat-icon tone-blue"><UniIcons type="staff" size="18" color="#3978FF" /></view><text class="stat-value">3</text><text class="stat-label">我的社团</text></view>
        <view class="stat-card surface-card tap-scale"><view class="stat-icon tone-purple"><UniIcons type="calendar" size="18" color="#7359D8" /></view><text class="stat-value">6</text><text class="stat-label">报名活动</text></view>
        <view class="stat-card surface-card tap-scale"><view class="stat-icon tone-green"><UniIcons type="medal" size="18" color="#22A56B" /></view><text class="stat-value">18h</text><text class="stat-label">志愿时长</text></view>
      </view>

      <view class="section-head"><view><text class="section-title">快捷入口</text><text class="section-subtitle">管理你的校园参与记录</text></view></view>
      <view class="quick-grid">
        <view class="quick-card surface-card tap-scale" @tap="openRegistrations"><view class="quick-card__icon tone-blue"><UniIcons type="paperclip" size="20" color="#3978FF" /></view><view class="quick-card__copy"><text class="quick-label">我的报名</text><text class="quick-description">查看活动安排</text></view><UniIcons class="arrow" type="right" size="16" color="#A3ADC0" /></view>
        <view class="quick-card surface-card tap-scale" @tap="openClubs"><view class="quick-card__icon tone-purple"><UniIcons type="staff" size="20" color="#7359D8" /></view><view class="quick-card__copy"><text class="quick-label">我的社团</text><text class="quick-description">管理加入的社团</text></view><UniIcons class="arrow" type="right" size="16" color="#A3ADC0" /></view>
        <view class="quick-card surface-card tap-scale" @tap="openAttendance"><view class="quick-card__icon tone-green"><UniIcons type="checkmarkempty" size="20" color="#22A56B" /></view><view class="quick-card__copy"><text class="quick-label">签到记录</text><text class="quick-description">累计签到 12 次</text></view><UniIcons class="arrow" type="right" size="16" color="#A3ADC0" /></view>
        <view class="quick-card surface-card tap-scale" @tap="openFavorites"><view class="quick-card__icon tone-orange"><UniIcons type="heart" size="20" color="#E58A38" /></view><view class="quick-card__copy"><text class="quick-label">我的收藏</text><text class="quick-description">保存感兴趣的活动</text></view><UniIcons class="arrow" type="right" size="16" color="#A3ADC0" /></view>
      </view>

      <view class="section-head section-head--recent"><view><text class="section-title">最近动态</text><text class="section-subtitle">记录每一次参与和成长</text></view><text class="section-link" @tap="openHistory">全部</text></view>
      <view class="recent-list surface-card">
        <view class="recent-item" @tap="openActivity"><view class="recent-date"><text>{{ firstDate }}</text><text>{{ firstTime }}</text></view><view class="recent-copy"><text class="recent-title">{{ firstTitle }}</text><text class="recent-meta">已报名 · 待签到</text></view><UniIcons type="right" size="16" color="#A3ADC0" /></view>
        <view class="recent-item recent-item--last" @tap="openActivity"><view class="recent-date"><text>{{ secondDate }}</text><text>{{ secondTime }}</text></view><view class="recent-copy"><text class="recent-title">{{ secondTitle }}</text><text class="recent-meta">已收藏 · 即将开始</text></view><UniIcons type="right" size="16" color="#A3ADC0" /></view>
      </view>

      <view class="section-head section-head--services"><view><text class="section-title">更多服务</text><text class="section-subtitle">让校园体验更顺手</text></view></view>
      <view class="service-list surface-card">
        <view class="service-item" @tap="openMessages"><view class="service-icon"><UniIcons type="chat" size="19" color="#3978FF" /></view><view class="service-copy"><text class="service-label">消息通知</text><text class="service-description">报名和社团动态</text></view><UniIcons type="right" size="16" color="#A3ADC0" /></view>
        <view class="service-item" @tap="openSettings"><view class="service-icon"><UniIcons type="gear" size="19" color="#3978FF" /></view><view class="service-copy"><text class="service-label">设置</text><text class="service-description">账号与隐私设置</text></view><UniIcons type="right" size="16" color="#A3ADC0" /></view>
        <view class="service-item service-item--last" @tap="openHelp"><view class="service-icon"><UniIcons type="info" size="19" color="#3978FF" /></view><view class="service-copy"><text class="service-label">帮助与反馈</text><text class="service-description">遇到问题可以告诉我们</text></view><UniIcons type="right" size="16" color="#A3ADC0" /></view>
      </view>
    </view>
    <AppTabBar active="profile" />
  </view>
</template>

<style lang="scss">
.profile-page { min-height: 100vh; background: #f5f7fb; }
.profile-page .profile-hero { position: relative; overflow: hidden; padding: 30rpx 28rpx 24rpx; border-radius: 28rpx; color: #fff; background: linear-gradient(135deg, #3978ff 0%, #647ef3 58%, #a98bff 125%); box-shadow: 0 18rpx 48rpx rgba(57, 120, 255, .22); }
.profile-page .profile-hero__glow { position: absolute; border-radius: 50%; opacity: .3; filter: blur(10px); pointer-events: none; }.profile-page .profile-hero__glow--one { width: 260rpx; height: 260rpx; right: -94rpx; top: -100rpx; background: #d8e1ff; }.profile-page .profile-hero__glow--two { width: 190rpx; height: 190rpx; left: -86rpx; bottom: -118rpx; background: #ffd8eb; }
.profile-page .profile-hero__top,.profile-page .profile-hero__bottom { position: relative; z-index: 1; display: flex; align-items: center; }.profile-page .profile-hero__bottom { justify-content: space-between; margin-top: 28rpx; padding-top: 20rpx; border-top: 1px solid rgba(255,255,255,.2); }
.profile-page .avatar-wrap { position: relative; flex: none; }.profile-page .avatar { width: 104rpx; height: 104rpx; border: 4rpx solid rgba(255,255,255,.7); border-radius: 34rpx; display: flex; align-items: center; justify-content: center; color: #3978ff; background: rgba(255,255,255,.9); font-size: 38rpx; font-weight: 700; box-shadow: 0 10rpx 24rpx rgba(34,67,150,.18); }.profile-page .online-dot { position: absolute; right: 2rpx; bottom: 3rpx; width: 20rpx; height: 20rpx; border: 4rpx solid #6d8df5; border-radius: 50%; background: #35d58a; }
.profile-page .profile-copy { min-width: 0; margin-left: 20rpx; }.profile-page .eyebrow { display: block; opacity: .78; font-size: 19rpx; letter-spacing: 3rpx; font-weight: 700; }.profile-page .name { display: block; margin-top: 8rpx; font-size: 36rpx; font-weight: 700; }.profile-page .profile-meta { display: block; margin-top: 8rpx; overflow: hidden; color: rgba(255,255,255,.78); font-size: 22rpx; white-space: nowrap; text-overflow: ellipsis; }
.profile-page .edit-button { flex: none; display: flex; align-items: center; margin-left: 12rpx; padding: 12rpx 16rpx; border-radius: 16rpx; color: #3978ff; background: rgba(255,255,255,.92); font-size: 22rpx; }.profile-page .edit-button text { margin-left: 5rpx; }.profile-page .identity-tag { display: flex; align-items: center; padding: 7rpx 12rpx; border-radius: 14rpx; color: #fff7da; background: rgba(255,255,255,.16); font-size: 20rpx; }.profile-page .identity-tag text { margin-left: 5rpx; }.profile-page .hero-hint { max-width: 56%; color: rgba(255,255,255,.74); font-size: 20rpx; text-align: right; }
.profile-page .stats-grid { display: flex; margin-top: 22rpx; }.profile-page .stat-card { flex: 1; min-width: 0; padding: 22rpx 10rpx; color: #18233d; text-align: center; }.profile-page .stat-card + .stat-card { margin-left: 14rpx; }.profile-page .stat-icon,.profile-page .quick-card__icon { display: flex; align-items: center; justify-content: center; }.profile-page .stat-icon { width: 48rpx; height: 48rpx; margin: 0 auto 12rpx; border-radius: 16rpx; }.profile-page .tone-blue { background: #eaf1ff; }.profile-page .tone-purple { background: #f1ebff; }.profile-page .tone-green { background: #e6f8ef; }.profile-page .tone-orange { background: #fff2df; }.profile-page .stat-value { display: block; color: #18233d; font-size: 34rpx; font-weight: 700; }.profile-page .stat-label { display: block; margin-top: 6rpx; color: #7e8aa3; font-size: 21rpx; }
.profile-page .section-head { display: flex; align-items: center; justify-content: space-between; margin: 34rpx 2rpx 18rpx; }.profile-page .section-head--recent { margin-top: 38rpx; }.profile-page .section-head--services { margin-top: 38rpx; }.profile-page .section-title { display: block; color: #18233d; font-size: 36rpx; font-weight: 700; }.profile-page .section-subtitle { display: block; margin-top: 6rpx; color: #7e8aa3; font-size: 21rpx; }.profile-page .section-link { color: #3978ff; font-size: 23rpx; }
.profile-page .quick-grid { display: flex; flex-wrap: wrap; margin: -7rpx; }.profile-page .quick-card { width: calc(50% - 14rpx); min-width: 0; display: flex; align-items: center; margin: 7rpx; padding: 22rpx 16rpx; color: #18233d; }.profile-page .quick-card__icon { flex: none; width: 56rpx; height: 56rpx; border-radius: 18rpx; }.profile-page .quick-card__copy { min-width: 0; margin-left: 14rpx; }.profile-page .quick-label,.profile-page .quick-description { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.profile-page .quick-label { color: #18233d; font-size: 25rpx; font-weight: 600; }.profile-page .quick-description { margin-top: 6rpx; color: #7e8aa3; font-size: 19rpx; }.profile-page .arrow { flex: none; margin-left: auto; }
.profile-page .recent-list,.profile-page .service-list { padding: 0 24rpx; }.profile-page .recent-item,.profile-page .service-item { display: flex; align-items: center; padding: 22rpx 0; border-bottom: 1px solid rgba(52,74,125,.1); }.profile-page .recent-item--last,.profile-page .service-item--last { border-bottom: 0; }.profile-page .recent-date { flex: none; width: 88rpx; padding: 10rpx 0; border-radius: 14rpx; color: #3978ff; background: #eaf1ff; text-align: center; }.profile-page .recent-date text { display: block; color: #3978ff; font-size: 19rpx; line-height: 1.5; }.profile-page .recent-copy,.profile-page .service-copy { flex: 1; min-width: 0; margin-left: 18rpx; }.profile-page .recent-title,.profile-page .recent-meta,.profile-page .service-label,.profile-page .service-description { display: block; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.profile-page .recent-title,.profile-page .service-label { color: #18233d; font-size: 25rpx; font-weight: 600; }.profile-page .recent-meta,.profile-page .service-description { margin-top: 7rpx; color: #7e8aa3; font-size: 20rpx; }.profile-page .service-icon { flex: none; width: 54rpx; height: 54rpx; border-radius: 17rpx; display: flex; align-items: center; justify-content: center; background: #eaf1ff; }
</style>
