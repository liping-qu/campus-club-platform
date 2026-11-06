<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  deleteActivity,
  fetchManageableActivities,
  publishActivity,
  unpublishActivity,
  type ManageStatus
} from '@/api/activity-admin'
import type { ActivityDetail } from '@/api/activity'
import { getCachedUser } from '@/api/auth'
import { ApiError, ErrorCodes } from '@/utils/request'
import { formatDeadline, formatMonthDay, formatTimeRange } from '@/utils/format'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const PAGE_SIZE = 20

const TABS: { label: string; status?: ManageStatus }[] = [
  { label: '全部' },
  { label: '草稿', status: 0 },
  { label: '已发布', status: 1 },
  { label: '已下架', status: 2 }
]

const activeTab = ref(0)
const records = ref<ActivityDetail[]>([])
const total = ref(0)
const pageNo = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref('')
const actingId = ref<number | null>(null)

const role = getCachedUser()?.role || ''
const allowed = computed(() => role === 'CLUB_ADMIN' || role === 'PLATFORM_ADMIN')
const hasMore = computed(() => records.value.length < total.value)

onShow(() => {
  if (allowed.value) load(true)
})

async function load(reset: boolean) {
  if (reset) {
    pageNo.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  loadError.value = ''
  try {
    const page = await fetchManageableActivities({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      status: TABS[activeTab.value].status
    })
    records.value = reset ? page.records : [...records.value, ...page.records]
    total.value = page.total
  } catch (error) {
    loadError.value = (error as ApiError)?.message || '活动列表加载失败'
    if (reset) records.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function goBack() { uni.navigateBack() }

function selectTab(index: number) {
  if (activeTab.value === index) return
  activeTab.value = index
  load(true)
}

function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  pageNo.value += 1
  load(false)
}

function createActivity() {
  uni.navigateTo({ url: '/pages/activity-edit/index' })
}

function editActivity(item: ActivityDetail) {
  // 只传 id：编辑页自己调管理端详情接口回填，草稿与已下架也能读到。
  // 早先是把列表里这一条塞进 storage 带过去，活动超过一页就再也翻不到了。
  uni.navigateTo({ url: `/pages/activity-edit/index?id=${item.id}` })
}

function openDetail(item: ActivityDetail) {
  if (item.status !== 1) return uni.showToast({ title: '只有已发布的活动才有公开详情', icon: 'none' })
  uni.navigateTo({ url: `/pages/activity-detail/index?id=${item.id}` })
}

function confirmAction(title: string, content: string, run: () => Promise<void>) {
  uni.showModal({
    title,
    content,
    success: (result) => { if (result.confirm) run() }
  })
}

async function runAction(id: number, action: () => Promise<any>, successText: string) {
  actingId.value = id
  try {
    await action()
    uni.showToast({ title: successText, icon: 'success' })
    await load(true)
  } catch (error) {
    handleError(error as ApiError)
  } finally {
    actingId.value = null
  }
}

function publish(item: ActivityDetail) {
  confirmAction('发布活动', `「${item.title}」发布后对所有同学可见，确定发布吗？`,
    () => runAction(item.id, () => publishActivity(item.id), '已发布'))
}

function unpublish(item: ActivityDetail) {
  confirmAction('下架活动', `下架后同学将看不到「${item.title}」，已报名记录会保留。确定下架吗？`,
    () => runAction(item.id, () => unpublishActivity(item.id), '已下架'))
}

function remove(item: ActivityDetail) {
  confirmAction('删除草稿', `删除「${item.title}」后无法恢复，确定删除吗？`,
    () => runAction(item.id, () => deleteActivity(item.id), '已删除'))
}

function handleError(error: ApiError) {
  switch (error?.code) {
    case ErrorCodes.ACTIVITY_STATUS_CONFLICT:
      uni.showToast({ title: '状态已变化，请刷新后重试', icon: 'none' })
      load(true)
      break
    case ErrorCodes.FORBIDDEN:
      uni.showToast({ title: '无权操作该社团的活动', icon: 'none' })
      break
    case ErrorCodes.CAPACITY_BELOW_JOINED:
      uni.showModal({ title: '无法修改名额', content: error.message, showCancel: false })
      break
    default:
      uni.showToast({ title: error?.message || '操作失败', icon: 'none' })
  }
}

function showTime(item: ActivityDetail) {
  return `${formatMonthDay(item.startTime)} ${formatTimeRange(item.startTime, item.endTime)}`
}
</script>
<template>
  <view class="activity-manage-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="page-nav">
        <view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view>
        <text>活动管理</text>
        <view v-if="allowed" class="create-button tap-scale" @tap="createActivity">
          <UniIcons type="plus" size="16" color="#3978FF" /><text>新建</text>
        </view>
      </view>

      <view v-if="!allowed" class="state-card surface-card">
        <text>只有社团负责人或平台管理员可以管理活动</text>
        <text class="link" @tap="goBack">返回</text>
      </view>

      <template v-else>
        <view class="tabs glass-control">
          <text
            v-for="(tab, index) in TABS"
            :key="tab.label"
            class="tab"
            :class="{ active: activeTab === index }"
            @tap="selectTab(index)"
          >{{ tab.label }}</text>
        </view>

        <view v-if="loading" class="state-card surface-card"><text>加载中…</text></view>
        <view v-else-if="loadError" class="state-card surface-card">
          <text>{{ loadError }}</text>
          <text class="link" @tap="load(true)">重新加载</text>
        </view>
        <view v-else-if="records.length === 0" class="state-card surface-card">
          <UniIcons type="calendar" size="34" color="#9AA5B8" />
          <text>这个分类下还没有活动</text>
          <text class="link" @tap="createActivity">新建一个</text>
        </view>

        <template v-else>
          <view v-for="item in records" :key="item.id" class="manage-card surface-card">
            <view class="card-top">
              <view class="card-copy">
                <text class="title">{{ item.title }}</text>
                <text class="muted">{{ item.clubName }} · {{ item.category }}</text>
              </view>
              <text class="phase" :class="`phase-${item.status}`">{{ item.phaseText }}</text>
            </view>
            <view class="meta">
              <text>{{ showTime(item) }}</text>
              <text>{{ item.joinedCount }}/{{ item.capacity }} 人</text>
            </view>
            <text class="deadline">报名截止 {{ formatDeadline(item.signupDeadline) }}</text>
            <view class="actions">
              <button class="ghost" @tap="openDetail(item)">查看</button>
              <button v-if="item.status !== 2" class="secondary" @tap="editActivity(item)">编辑</button>
              <button
                v-if="item.status === 0 || item.status === 2"
                class="secondary"
                :disabled="actingId === item.id"
                @tap="publish(item)"
              >发布</button>
              <button v-if="item.status === 1" class="secondary" :disabled="actingId === item.id" @tap="unpublish(item)">下架</button>
              <button v-if="item.status === 0" class="danger" :disabled="actingId === item.id" @tap="remove(item)">删除</button>
            </view>
          </view>

          <view v-if="hasMore" class="load-more" @tap="loadMore">
            <text>{{ loadingMore ? '加载中…' : '加载更多' }}</text>
          </view>
          <text v-else class="list-end">共 {{ total }} 个活动</text>
        </template>
      </template>
    </view>
  </view>
</template>
<style lang="scss">
.activity-manage-page { min-height:100vh; background:var(--app-bg); padding-bottom:40rpx; }
.activity-manage-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }
.activity-manage-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }
.activity-manage-page .create-button { display:flex; align-items:center; gap:6rpx; margin-left:auto; padding:12rpx 20rpx; border-radius:16rpx; color:var(--app-primary); background:rgba(57,120,255,.1); font-size:23rpx; }
.activity-manage-page .tabs { display:flex; justify-content:space-around; padding:8rpx; border-radius:18rpx; }
.activity-manage-page .tab { flex:1; padding:16rpx; text-align:center; color:var(--app-muted); font-size:24rpx; }
.activity-manage-page .tab.active { color:var(--app-primary); background:rgba(57,120,255,.1); border-radius:14rpx; }
.activity-manage-page .state-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:24rpx; padding:70rpx 20rpx; color:var(--app-muted); font-size:25rpx; }
.activity-manage-page .link { color:var(--app-primary); font-size:23rpx; }
.activity-manage-page .manage-card { margin-top:20rpx; padding:26rpx; }
.activity-manage-page .card-top { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; }
.activity-manage-page .card-copy { min-width:0; }
.activity-manage-page .title { display:block; font-size:28rpx; font-weight:600; }
.activity-manage-page .muted { display:block; margin-top:10rpx; color:var(--app-muted); font-size:21rpx; }
.activity-manage-page .phase { flex:none; padding:8rpx 12rpx; border-radius:10rpx; font-size:21rpx; color:#3978FF; background:#eaf1ff; }
.activity-manage-page .phase-0 { color:#7e8aa3; background:#f0f3f8; }
.activity-manage-page .phase-2 { color:#db6370; background:#fff0f1; }
.activity-manage-page .meta { display:flex; justify-content:space-between; margin-top:18rpx; padding-top:18rpx; border-top:1px solid #edf0f6; color:var(--app-muted); font-size:22rpx; }
.activity-manage-page .deadline { display:block; margin-top:10rpx; color:var(--app-muted); font-size:21rpx; }
.activity-manage-page .actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:14rpx; margin-top:18rpx; }
.activity-manage-page .actions button { margin:0; padding:0 22rpx; height:60rpx; border-radius:14rpx; font-size:22rpx; }
.activity-manage-page .ghost { color:var(--app-muted); background:#f2f5fa; }
.activity-manage-page .secondary { color:#3978FF; background:#edf3ff; }
.activity-manage-page .danger { color:#db6370; background:#fff0f1; }
.activity-manage-page .load-more { margin-top:24rpx; text-align:center; color:var(--app-primary); font-size:24rpx; }
.activity-manage-page .list-end { display:block; margin-top:24rpx; text-align:center; color:var(--app-muted); font-size:21rpx; }
</style>
