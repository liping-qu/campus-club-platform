<script setup lang="ts">
import { computed, ref } from 'vue'
import { clubs } from '@/data/mock'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const query = ref('')
const activeCategory = ref('全部')
const loading = ref(false)
const categories = ['全部', '公益志愿', '兴趣艺术', '文体活动', '创新创业']
const filteredClubs = computed(() => clubs.filter(club => {
  const hitCategory = activeCategory.value === '全部' || club.category === activeCategory.value
  const keyword = query.value.trim().toLowerCase()
  return hitCategory && (!keyword || `${club.name}${club.category}${club.description}`.toLowerCase().includes(keyword))
}))

function selectCategory(category: string) { activeCategory.value = category }
function openClub(event: any) {
  const id = event.currentTarget.dataset.id
  if (id) uni.navigateTo({ url: `/pages/club-detail/index?id=${id}` })
}
function clearSearch() { query.value = '' }
</script>

<template>
  <view class="clubs-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="page-heading rise-in"><text class="eyebrow">CAMPUS COMMUNITY</text><text class="page-title">探索社团</text><text class="page-subtitle">找到和你兴趣相投的伙伴</text></view>
      <view class="search-box glass-control">
        <UniIcons type="search" size="20" color="#7E8AA3" />
        <input v-model="query" class="search-input" placeholder="搜索社团名称或方向" confirm-type="search" />
        <text v-if="query" class="clear-search" @tap="clearSearch">清除</text>
      </view>
      <scroll-view scroll-x class="filters glass-control">
        <text v-for="category in categories" :key="category" class="filter" :class="{ active: activeCategory === category }" @tap="selectCategory(category)">{{ category }}</text>
      </scroll-view>
      <view v-if="loading" class="state-card surface-card"><text>正在加载社团…</text></view>
      <view v-else-if="filteredClubs.length === 0" class="state-card surface-card"><UniIcons type="search" size="32" color="#9AA5B8" /><text>没有找到匹配的社团</text><text class="state-link" @tap="clearSearch">清除搜索条件</text></view>
      <view v-else v-for="club in filteredClubs" :key="club.id" class="club-row surface-card tap-scale" :data-id="club.id" @tap="openClub">
        <view class="club-logo" :style="{ background: club.color }"><UniIcons type="star" size="23" color="#3978FF" /></view>
        <view class="club-main"><text class="club-name">{{ club.name }}</text><text class="muted">{{ club.category }} · {{ club.members }} 名成员</text><text class="club-desc">{{ club.description }}</text></view>
        <UniIcons type="right" size="18" color="#9AA5B8" />
      </view>
    </view>
    <AppTabBar active="clubs" />
  </view>
</template>

<style lang="scss">
.clubs-page { min-height:100vh; background:var(--app-bg); }.clubs-page .page-heading { padding:8rpx 0 28rpx; }.clubs-page .eyebrow { display:block; color:var(--app-primary); font-size:20rpx; letter-spacing:3rpx; font-weight:700; }.clubs-page .page-title { display:block; margin-top:14rpx; color:var(--app-text); font-size:48rpx; font-weight:700; }.clubs-page .page-subtitle { display:block; margin-top:8rpx; color:var(--app-muted); font-size:25rpx; }.clubs-page .search-box { display:flex; align-items:center; gap:12rpx; padding:18rpx 24rpx; border-radius:20rpx; }.clubs-page .search-input { flex:1; height:46rpx; color:var(--app-text); font-size:26rpx; }.clubs-page .clear-search,.clubs-page .state-link { color:var(--app-primary); font-size:23rpx; }.clubs-page .filters { display:flex; white-space:nowrap; margin:26rpx 0; padding:4rpx 8rpx; border-radius:18rpx; }.clubs-page .filter { display:inline-block; padding:15rpx 20rpx; color:var(--app-muted); font-size:24rpx; }.clubs-page .filter.active { color:var(--app-primary); font-weight:600; background:rgba(57,120,255,.12); border-radius:14rpx; }.clubs-page .club-row { display:flex; align-items:center; padding:24rpx; margin-bottom:18rpx; }.clubs-page .club-logo { flex:none; width:84rpx; height:84rpx; border-radius:24rpx; display:flex; align-items:center; justify-content:center; }.clubs-page .club-main { flex:1; margin-left:20rpx; min-width:0; }.clubs-page .club-name { display:block; font-size:30rpx; font-weight:600; margin-bottom:8rpx; }.clubs-page .muted,.clubs-page .club-desc { display:block; font-size:23rpx; color:var(--app-muted); }.clubs-page .club-desc { margin-top:10rpx; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }.clubs-page .state-card { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:16rpx; min-height:240rpx; color:var(--app-muted); font-size:25rpx; }
</style>
