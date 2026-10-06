<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { activities } from '@/data/mock'
import { getFavoriteIds, setFavoriteIds } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const favoriteIds = ref<string[]>([])
const records = ref<typeof activities>([])
onShow(() => { favoriteIds.value = getFavoriteIds(); records.value = activities.filter(item => favoriteIds.value.includes(item.id)) })
function goBack() { uni.navigateBack() }
function openDetail(event: any) { const id = event.currentTarget.dataset.id; if (id) uni.navigateTo({ url: `/pages/activity-detail/index?id=${id}` }) }
function removeFavorite(event: any) { const id = event.currentTarget.dataset.id; favoriteIds.value = favoriteIds.value.filter(value => value !== id); setFavoriteIds(favoriteIds.value); records.value = activities.filter(item => favoriteIds.value.includes(item.id)); uni.showToast({ title: '已取消收藏', icon: 'none' }) }
</script>
<template>
  <view class="favorites-page"><view class="page-container" :style="topSafeStyle"><view class="page-nav"><view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view><text>我的收藏</text></view><view v-if="records.length === 0" class="empty-card surface-card"><UniIcons type="star" size="34" color="#9AA5B8" /><text>还没有收藏活动</text><text class="link" @tap="uni.navigateTo({ url: '/pages/activities/index' })">去发现活动</text></view><view v-else v-for="item in records" :key="item.id" class="favorite-card surface-card" :data-id="item.id" @tap="openDetail"><view class="cover"><text>活动</text></view><view class="copy"><text class="title">{{ item.title }}</text><text class="muted">{{ item.date }} · {{ item.location }}</text><text class="tag">{{ item.tag }}</text></view><view class="remove" :data-id="item.id" @tap.stop="removeFavorite"><UniIcons type="star-filled" size="20" color="#3978FF" /></view></view></view></view>
</template>
<style lang="scss">
.favorites-page { min-height:100vh; background:var(--app-bg); }.favorites-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }.favorites-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }.favorites-page .favorite-card { display:flex; align-items:center; margin-top:20rpx; padding:20rpx; }.favorites-page .cover { display:flex; align-items:center; justify-content:center; width:150rpx; height:130rpx; border-radius:16rpx; color:#fff; background:linear-gradient(140deg,#789ef3,#ae91f5); font-size:25rpx; }.favorites-page .copy { flex:1; margin-left:20rpx; min-width:0; }.favorites-page .title { display:block; font-size:27rpx; font-weight:600; }.favorites-page .muted { display:block; margin-top:10rpx; color:var(--app-muted); font-size:22rpx; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }.favorites-page .tag { display:inline-block; margin-top:12rpx; padding:5rpx 10rpx; border-radius:8rpx; color:#3978FF; background:#edf3ff; font-size:20rpx; }.favorites-page .remove { padding:16rpx; }.favorites-page .empty-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:24rpx; padding:70rpx 20rpx; color:var(--app-muted); font-size:25rpx; }.favorites-page .link { color:var(--app-primary); font-size:23rpx; }
</style>
