<script setup lang="ts">
import { ref } from 'vue'
import { login } from '@/api/auth'
import { ApiError, ErrorCodes } from '@/utils/request'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'
import { usePageSafeArea } from '@/utils/safe-area'

const { topSafeStyle } = usePageSafeArea()

const username = ref('')
const password = ref('')
const submitting = ref(false)

/** 演示账号，密码统一 123456，与 02_seed.sql 一致 */
const demoAccounts = [
  { label: '学生', username: 'student1' },
  { label: '社团负责人', username: 'linqingya' },
  { label: '平台管理员', username: 'admin' }
]

async function submit() {
  if (submitting.value) return
  const name = username.value.trim()
  if (!name || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const result = await login(name, password.value)
    uni.showToast({ title: `欢迎，${result.user.nickname}`, icon: 'none' })
    // 用 reLaunch 回到首页：登录页不留在返回栈里
    setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 400)
  } catch (error) {
    const apiError = error as ApiError
    // 401 不弹 toast（请求层已处理跳转），这里只处理凭证与禁用两种业务分支
    const message = apiError?.code === ErrorCodes.BAD_CREDENTIALS
      ? '用户名或密码错误'
      : apiError?.code === ErrorCodes.ACCOUNT_DISABLED
        ? '账号已被禁用，请联系管理员'
        : (apiError?.message || '登录失败，请确认后端服务已启动')
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function fill(account: { label: string; username: string }) {
  username.value = account.username
  password.value = '123456'
}

function browseAsGuest() {
  // 活动列表是公开接口，不登录也能浏览
  uni.reLaunch({ url: '/pages/activities/index' })
}
</script>

<template>
  <view class="login-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="brand">
        <view class="brand-badge"><UniIcons type="auth" size="26" color="#ffffff" /></view>
        <text class="brand-title">校园社团通</text>
        <text class="brand-subtitle">登录后可以报名活动、查看我的报名</text>
      </view>

      <view class="form-card surface-card">
        <view class="field">
          <text class="field-label">用户名</text>
          <input
            v-model="username"
            class="field-input"
            placeholder="请输入用户名"
            placeholder-class="field-placeholder"
            :disabled="submitting"
          />
        </view>
        <view class="field">
          <text class="field-label">密码</text>
          <input
            v-model="password"
            class="field-input"
            password
            placeholder="请输入密码"
            placeholder-class="field-placeholder"
            :disabled="submitting"
            @confirm="submit"
          />
        </view>
        <button class="primary-button" :disabled="submitting" @tap="submit">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
        <text class="guest-link" @tap="browseAsGuest">先随便逛逛</text>
      </view>

      <view class="demo-card surface-card">
        <text class="demo-title">演示账号（密码都是 123456）</text>
        <view class="demo-list">
          <text v-for="account in demoAccounts" :key="account.username" class="demo-chip" @tap="fill(account)">
            {{ account.label }} · {{ account.username }}
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
.login-page { min-height: 100vh; background: var(--app-bg); }
.login-page .page-container { padding: 0 32rpx 60rpx; }
.login-page .brand { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; padding: 90rpx 8rpx 46rpx; }
.login-page .brand-badge { width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: 26rpx; background: linear-gradient(140deg, #3978FF, #8f7df2); }
.login-page .brand-title { font-size: 44rpx; font-weight: 700; color: var(--app-text); }
.login-page .brand-subtitle { font-size: 24rpx; color: var(--app-muted); }
.login-page .form-card { padding: 34rpx 30rpx; }
.login-page .field { margin-bottom: 26rpx; }
.login-page .field-label { display: block; margin-bottom: 12rpx; font-size: 24rpx; color: var(--app-muted); }
.login-page .field-input { height: 88rpx; padding: 0 24rpx; border-radius: 18rpx; background: #f4f7fd; font-size: 28rpx; color: var(--app-text); }
.login-page .field-placeholder { color: #b3bccd; font-size: 26rpx; }
.login-page .primary-button { margin-top: 12rpx; height: 92rpx; line-height: 92rpx; border-radius: 20rpx; font-size: 30rpx; font-weight: 600; color: #fff; background: linear-gradient(120deg, #3978FF, #6f8ef7); }
.login-page .primary-button[disabled] { opacity: .6; }
.login-page .guest-link { display: block; margin-top: 24rpx; text-align: center; font-size: 24rpx; color: var(--app-primary); }
.login-page .demo-card { margin-top: 24rpx; padding: 28rpx 30rpx; }
.login-page .demo-title { display: block; font-size: 23rpx; color: var(--app-muted); }
.login-page .demo-list { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 18rpx; }
.login-page .demo-chip { padding: 12rpx 20rpx; border-radius: 14rpx; background: #eef3ff; color: #3978FF; font-size: 22rpx; }
</style>
