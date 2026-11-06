<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createActivity,
  fetchManageDetail,
  updateActivity,
  type ActivitySavePayload
} from '@/api/activity-admin'
import { fetchMyClubs, type ClubOption } from '@/api/club'
import type { ActivityDetail } from '@/api/activity'
import { ApiError, ErrorCodes } from '@/utils/request'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const CATEGORIES = ['公益志愿', '兴趣艺术', '文体活动', '创新创业', '学术科技']
const COVER_COLORS = ['#c8d9ff', '#ffd9b8', '#d8c8ff', '#c8f0dd', '#ffe1e6', '#e6ecf7']

const editingId = ref<number | null>(null)
const loading = ref(false)
const submitting = ref(false)
const clubs = ref<ClubOption[]>([])
const formError = ref('')

const form = ref({
  clubId: 0,
  title: '',
  category: CATEGORIES[0],
  location: '',
  capacity: 20,
  startDate: '',
  startClock: '09:00',
  endDate: '',
  endClock: '11:00',
  deadlineDate: '',
  deadlineClock: '18:00',
  description: '',
  rules: '',
  coverColor: COVER_COLORS[0]
})

const isEdit = computed(() => editingId.value !== null)
const title = computed(() => (isEdit.value ? '编辑活动' : '新建活动'))
const clubNames = computed(() => clubs.value.map(club => club.name))
const selectedClubName = computed(() => clubs.value.find(club => club.id === form.value.clubId)?.name || '请选择社团')

onLoad((options) => {
  const id = options?.id ? Number(options.id) : null
  editingId.value = id && Number.isFinite(id) ? id : null
  init()
})

async function init() {
  loading.value = true
  formError.value = ''
  try {
    if (!isEdit.value) {
      await loadClubs()
      applyDefaults()
      // 只有一个可管理社团时直接选中，省一次点击
      if (clubs.value.length === 1) form.value.clubId = clubs.value[0].id
      return
    }
    const detail = await resolveEditingActivity()
    fillForm(detail)
  } catch (error) {
    formError.value = (error as ApiError)?.message || '活动加载失败'
  } finally {
    loading.value = false
  }
}

async function loadClubs() {
  clubs.value = await fetchMyClubs()
}

/**
 * 编辑的数据来源。
 *
 * 不能调 GET /api/activities/{id}：那个接口只返回已发布的活动，
 * 草稿和已下架的活动在它眼里是 404 —— 编辑页会白屏。
 * 走管理端单条详情（/api/activities/manage/{id}），它不带状态过滤，
 * 归属校验也在后端做（不存在 404、不属于你 403），前端不用再判断一次。
 */
async function resolveEditingActivity(): Promise<ActivityDetail> {
  if (!editingId.value) throw new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, '缺少活动 ID', 400)
  // 404（不存在）与 403（不属于你）都由 request 层抛出，交给下面的 catch 显示
  return fetchManageDetail(editingId.value)
}

function applyDefaults() {
  const now = new Date()
  const day = (offset: number) => {
    const date = new Date(now.getTime() + offset * 24 * 3600 * 1000)
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  }
  form.value.startDate = day(3)
  form.value.endDate = day(3)
  form.value.deadlineDate = day(2)
}

function fillForm(detail: ActivityDetail) {
  form.value.clubId = detail.clubId
  form.value.title = detail.title
  form.value.category = detail.category || CATEGORIES[0]
  form.value.location = detail.location
  form.value.capacity = detail.capacity
  form.value.description = detail.description || ''
  form.value.rules = (detail.ruleList || []).join('\n')
  form.value.coverColor = detail.coverColor || COVER_COLORS[0]

  const start = splitDateTime(detail.startTime)
  const end = splitDateTime(detail.endTime)
  const deadline = splitDateTime(detail.signupDeadline)
  form.value.startDate = start.date
  form.value.startClock = start.clock
  form.value.endDate = end.date
  form.value.endClock = end.clock
  form.value.deadlineDate = deadline.date
  form.value.deadlineClock = deadline.clock

  if (!clubs.value.length) loadClubs().catch(() => undefined)
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function splitDateTime(value?: string) {
  const [date = '', clock = ''] = (value || '').split(' ')
  return { date, clock: clock.slice(0, 5) || '09:00' }
}

function joinDateTime(date: string, clock: string) {
  return `${date} ${clock || '00:00'}:00`
}

function goBack() { uni.navigateBack() }

function selectCategory(event: any) { form.value.category = CATEGORIES[Number(event.detail.value)] }
function selectClub(event: any) {
  const club = clubs.value[Number(event.detail.value)]
  if (club) form.value.clubId = club.id
}
function selectColor(color: string) { form.value.coverColor = color }
function onDate(field: 'startDate' | 'endDate' | 'deadlineDate', event: any) { form.value[field] = event.detail.value }
function onClock(field: 'startClock' | 'endClock' | 'deadlineClock', event: any) { form.value[field] = event.detail.value }
function onCapacity(event: any) { form.value.capacity = Number(event.detail.value) || 0 }

/**
 * 只做「填没填」和「是不是数字」的检查。
 * 时间先后、名额是否小于已报名人数这些规则只在后端定义 —— 前端再写一遍，
 * 迟早会和 40002 / 40003 / 40909 出现两种说法。提交失败时直接展示后端给的 message。
 */
function validate(): string {
  const value = form.value
  if (!value.title.trim()) return '请填写活动标题'
  if (!value.location.trim()) return '请填写活动地点'
  if (!Number.isInteger(value.capacity) || value.capacity < 1) return '人数上限至少为 1'
  if (!value.startDate || !value.endDate || !value.deadlineDate) return '请选择活动时间'
  if (!isEdit.value && !value.clubId) return '请选择所属社团'
  return ''
}

function buildPayload(): ActivitySavePayload {
  const value = form.value
  return {
    title: value.title.trim(),
    description: value.description.trim(),
    location: value.location.trim(),
    category: value.category,
    startTime: joinDateTime(value.startDate, value.startClock),
    endTime: joinDateTime(value.endDate, value.endClock),
    signupDeadline: joinDateTime(value.deadlineDate, value.deadlineClock),
    capacity: value.capacity,
    coverColor: value.coverColor,
    rules: value.rules.trim()
  }
}

async function submit() {
  if (submitting.value) return
  const message = validate()
  if (message) return uni.showToast({ title: message, icon: 'none' })

  submitting.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateActivity(editingId.value, buildPayload())
      uni.showToast({ title: '已保存', icon: 'success' })
    } else {
      await createActivity({ ...buildPayload(), clubId: form.value.clubId })
      // 新建一定是草稿：发布是状态机里单独的一条边，不在这里顺手做掉
      uni.showToast({ title: '已创建为草稿', icon: 'success' })
    }
    setTimeout(goBack, 600)
  } catch (error) {
    handleError(error as ApiError)
  } finally {
    submitting.value = false
  }
}

function handleError(error: ApiError) {
  switch (error?.code) {
    case ErrorCodes.CAPACITY_BELOW_JOINED:
      uni.showModal({ title: '名额不能小于已报名人数', content: error.message, showCancel: false })
      break
    case ErrorCodes.ACTIVITY_STATUS_CONFLICT:
      uni.showModal({ title: '当前状态不允许修改', content: error.message, showCancel: false })
      break
    case ErrorCodes.INVALID_TIME_RANGE:
    case ErrorCodes.SIGNUP_DEADLINE_AFTER_START:
    case ErrorCodes.INVALID_PARAM:
      uni.showToast({ title: error.message || '请检查填写内容', icon: 'none' })
      break
    case ErrorCodes.FORBIDDEN:
      uni.showToast({ title: '无权管理该社团的活动', icon: 'none' })
      break
    default:
      uni.showToast({ title: error?.message || '保存失败', icon: 'none' })
  }
}
</script>
<template>
  <view class="activity-edit-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="page-nav">
        <view class="back-button glass-control" @tap="goBack"><UniIcons type="left" size="20" color="#27324A" /></view>
        <text>{{ title }}</text>
      </view>

      <view v-if="loading" class="state-card surface-card"><text>加载中…</text></view>
      <view v-else-if="formError" class="state-card surface-card">
        <text>{{ formError }}</text>
        <text class="link" @tap="goBack">返回</text>
      </view>

      <template v-else>
        <view v-if="!isEdit" class="form-card surface-card">
          <text class="label">所属社团</text>
          <picker mode="selector" :range="clubNames" @change="selectClub">
            <view class="picker-value">{{ selectedClubName }}<UniIcons type="right" size="14" color="#A3ADC0" /></view>
          </picker>
          <text v-if="clubs.length === 0" class="hint">你没有可管理的社团，无法新建活动</text>
        </view>

        <view class="form-card surface-card">
          <text class="label">活动标题</text>
          <input v-model="form.title" class="input" maxlength="100" placeholder="例如：校园环保志愿行动" />

          <text class="label">活动分类</text>
          <picker mode="selector" :range="CATEGORIES" @change="selectCategory">
            <view class="picker-value">{{ form.category }}<UniIcons type="right" size="14" color="#A3ADC0" /></view>
          </picker>

          <text class="label">活动地点</text>
          <input v-model="form.location" class="input" maxlength="120" placeholder="例如：大学生活动中心" />

          <text class="label">人数上限</text>
          <input class="input" type="number" :value="String(form.capacity)" @input="onCapacity" placeholder="至少 1 人" />
          <text v-if="isEdit" class="hint">不能小于已报名人数，否则后端会拒绝（40909）</text>
        </view>

        <view class="form-card surface-card">
          <text class="label">开始时间</text>
          <view class="datetime-row">
            <picker mode="date" :value="form.startDate" @change="onDate('startDate', $event)">
              <view class="picker-value">{{ form.startDate || '选择日期' }}</view>
            </picker>
            <picker mode="time" :value="form.startClock" @change="onClock('startClock', $event)">
              <view class="picker-value">{{ form.startClock }}</view>
            </picker>
          </view>

          <text class="label">结束时间</text>
          <view class="datetime-row">
            <picker mode="date" :value="form.endDate" @change="onDate('endDate', $event)">
              <view class="picker-value">{{ form.endDate || '选择日期' }}</view>
            </picker>
            <picker mode="time" :value="form.endClock" @change="onClock('endClock', $event)">
              <view class="picker-value">{{ form.endClock }}</view>
            </picker>
          </view>

          <text class="label">报名截止</text>
          <view class="datetime-row">
            <picker mode="date" :value="form.deadlineDate" @change="onDate('deadlineDate', $event)">
              <view class="picker-value">{{ form.deadlineDate || '选择日期' }}</view>
            </picker>
            <picker mode="time" :value="form.deadlineClock" @change="onClock('deadlineClock', $event)">
              <view class="picker-value">{{ form.deadlineClock }}</view>
            </picker>
          </view>
          <text class="hint">时间先后由后端校验：结束必须晚于开始，截止不能晚于开始</text>
        </view>

        <view class="form-card surface-card">
          <text class="label">活动介绍</text>
          <textarea v-model="form.description" class="textarea" maxlength="2000" placeholder="活动内容、集合方式等" />

          <text class="label">活动规则</text>
          <textarea v-model="form.rules" class="textarea" maxlength="1000" placeholder="一行一条，例如：请提前 10 分钟到场" />

          <text class="label">封面配色</text>
          <view class="color-row">
            <view
              v-for="color in COVER_COLORS"
              :key="color"
              class="color-dot"
              :class="{ active: form.coverColor === color }"
              :style="{ background: color }"
              @tap="selectColor(color)"
            />
          </view>
        </view>

        <button class="submit-button" :disabled="submitting" @tap="submit">{{ submitting ? '保存中…' : (isEdit ? '保存修改' : '创建草稿') }}</button>
        <text v-if="!isEdit" class="submit-hint">新建的活动是草稿，需要在列表里点「发布」才会对同学可见</text>
      </template>
    </view>
  </view>
</template>
<style lang="scss">
.activity-edit-page { min-height:100vh; background:var(--app-bg); padding-bottom:40rpx; }
.activity-edit-page .page-nav { display:flex; align-items:center; gap:20rpx; padding:8rpx 0 26rpx; font-size:32rpx; font-weight:700; }
.activity-edit-page .back-button { width:66rpx; height:66rpx; display:flex; align-items:center; justify-content:center; border-radius:22rpx; }
.activity-edit-page .state-card { display:flex; flex-direction:column; align-items:center; gap:16rpx; margin-top:20rpx; padding:60rpx 20rpx; color:var(--app-muted); font-size:25rpx; }
.activity-edit-page .link { color:var(--app-primary); font-size:23rpx; }
.activity-edit-page .form-card { margin-top:20rpx; padding:26rpx; }
.activity-edit-page .label { display:block; margin-top:22rpx; color:var(--app-text); font-size:24rpx; font-weight:600; }
.activity-edit-page .label:first-child { margin-top:0; }
.activity-edit-page .input,.activity-edit-page .picker-value { margin-top:12rpx; padding:20rpx; border-radius:14rpx; background:#f4f7fc; color:var(--app-text); font-size:24rpx; }
.activity-edit-page .picker-value { display:flex; align-items:center; justify-content:space-between; }
.activity-edit-page .datetime-row { display:flex; gap:16rpx; }
.activity-edit-page .datetime-row picker { flex:1; }
.activity-edit-page .textarea { width:100%; box-sizing:border-box; height:180rpx; margin-top:12rpx; padding:20rpx; border-radius:14rpx; background:#f4f7fc; color:var(--app-text); font-size:24rpx; }
.activity-edit-page .hint { display:block; margin-top:12rpx; color:var(--app-muted); font-size:21rpx; }
.activity-edit-page .color-row { display:flex; gap:18rpx; margin-top:16rpx; }
.activity-edit-page .color-dot { width:56rpx; height:56rpx; border-radius:50%; border:4rpx solid transparent; }
.activity-edit-page .color-dot.active { border-color:var(--app-primary); }
.activity-edit-page .submit-button { margin-top:28rpx; height:86rpx; border-radius:20rpx; color:#fff; background:var(--app-primary); font-size:27rpx; }
.activity-edit-page .submit-hint { display:block; margin-top:14rpx; text-align:center; color:var(--app-muted); font-size:21rpx; }
</style>
