<script setup lang="ts">
import { ref } from 'vue'
import { usePageSafeArea } from '@/utils/safe-area'
import { getUserProfile, setUserProfile } from '@/utils/mock-state'
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue'

const { topSafeStyle } = usePageSafeArea()
const form = ref(getUserProfile())

const goBack = () => uni.navigateBack({ delta: 1 })

const saveProfile = () => {
  const nickname = form.value.nickname.trim()
  if (!nickname) {
    uni.showToast({ title: '请填写昵称', icon: 'none' })
    return
  }
  setUserProfile({
    nickname,
    college: form.value.college.trim() || '未填写学院',
    grade: form.value.grade.trim() || '未填写年级',
    studentId: form.value.studentId.trim(),
    phone: form.value.phone.trim()
  })
  uni.showToast({ title: '资料已保存', icon: 'success' })
  setTimeout(() => uni.navigateBack({ delta: 1 }), 450)
}
</script>

<template>
  <view class="profile-edit-page">
    <view class="page-container" :style="topSafeStyle">
      <view class="edit-nav">
        <view class="back-button tap-scale" @tap="goBack"><UniIcons type="left" size="22" color="#18233D" /></view>
        <text class="edit-nav__title">编辑资料</text>
        <view class="edit-nav__placeholder" />
      </view>

      <view class="edit-avatar-card surface-card rise-in">
        <view class="edit-avatar">{{ form.nickname.slice(0, 1) || '我' }}</view>
        <view class="edit-avatar-copy"><text class="edit-avatar-title">个人头像</text><text class="edit-avatar-hint">头像暂使用昵称首字，后续可上传图片</text></view>
        <UniIcons type="right" size="17" color="#A3ADC0" />
      </view>

      <view class="form-card surface-card rise-in">
        <view class="form-row"><text class="form-label">昵称</text><input class="form-input" v-model="form.nickname" maxlength="20" placeholder="请输入昵称" placeholder-class="form-placeholder" /></view>
        <view class="form-row"><text class="form-label">学院</text><input class="form-input" v-model="form.college" maxlength="30" placeholder="请输入学院" placeholder-class="form-placeholder" /></view>
        <view class="form-row"><text class="form-label">年级</text><input class="form-input" v-model="form.grade" maxlength="12" placeholder="例如：大一" placeholder-class="form-placeholder" /></view>
        <view class="form-row"><text class="form-label">学号</text><input class="form-input" v-model="form.studentId" maxlength="24" placeholder="选填" placeholder-class="form-placeholder" /></view>
        <view class="form-row form-row--last"><text class="form-label">联系方式</text><input class="form-input" v-model="form.phone" maxlength="30" placeholder="选填" placeholder-class="form-placeholder" /></view>
      </view>

      <text class="privacy-hint">你的资料只保存在当前设备，用于展示校园参与信息。</text>
      <button class="save-button glass-control tap-scale" @tap="saveProfile">保存资料</button>
    </view>
  </view>
</template>

<style lang="scss">
.profile-edit-page { min-height: 100vh; background: #f5f7fb; color: #18233d; }
.profile-edit-page .page-container { padding-bottom: 64rpx; }
.profile-edit-page .edit-nav { display: flex; align-items: center; justify-content: space-between; min-height: 76rpx; margin-bottom: 22rpx; }
.profile-edit-page .back-button { display: flex; align-items: center; justify-content: center; width: 68rpx; height: 68rpx; border-radius: 22rpx; background: rgba(255,255,255,.86); box-shadow: 0 8rpx 20rpx rgba(44,68,120,.08); }
.profile-edit-page .edit-nav__title { color: #18233d; font-size: 36rpx; font-weight: 700; }
.profile-edit-page .edit-nav__placeholder { width: 68rpx; }
.profile-edit-page .edit-avatar-card { display: flex; align-items: center; padding: 28rpx 24rpx; }
.profile-edit-page .edit-avatar { display: flex; align-items: center; justify-content: center; flex: none; width: 112rpx; height: 112rpx; border: 4rpx solid #dce7ff; border-radius: 34rpx; color: #3978ff; background: #edf3ff; font-size: 42rpx; font-weight: 700; }
.profile-edit-page .edit-avatar-copy { flex: 1; min-width: 0; margin-left: 22rpx; }
.profile-edit-page .edit-avatar-title,.profile-edit-page .edit-avatar-hint { display: block; }
.profile-edit-page .edit-avatar-title { color: #18233d; font-size: 29rpx; font-weight: 700; }
.profile-edit-page .edit-avatar-hint { margin-top: 9rpx; color: #7e8aa3; font-size: 21rpx; line-height: 1.45; }
.profile-edit-page .form-card { margin-top: 22rpx; padding: 0 24rpx; }
.profile-edit-page .form-row { display: flex; align-items: center; min-height: 104rpx; border-bottom: 1px solid rgba(52,74,125,.1); }
.profile-edit-page .form-row--last { border-bottom: 0; }
.profile-edit-page .form-label { flex: none; width: 152rpx; color: #35415c; font-size: 27rpx; font-weight: 600; }
.profile-edit-page .form-input { flex: 1; min-width: 0; height: 74rpx; color: #18233d; font-size: 27rpx; text-align: right; }
.profile-edit-page .form-placeholder { color: #a3adc0; }
.profile-edit-page .privacy-hint { display: block; margin: 22rpx 8rpx 28rpx; color: #8d98ac; font-size: 21rpx; line-height: 1.5; }
.profile-edit-page .save-button { height: 92rpx; padding: 0; border: 0; border-radius: 24rpx; color: #fff; background: #3978ff; box-shadow: 0 14rpx 28rpx rgba(57,120,255,.22); font-size: 30rpx; line-height: 92rpx; }
.profile-edit-page .save-button::after { border: 0; }
</style>
