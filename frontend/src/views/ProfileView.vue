<template>
  <div class="page" style="max-width: 560px">
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header><b>Hồ sơ tài khoản</b></template>
      <el-form :model="profile" label-position="top">
        <el-form-item label="Tên đăng nhập"><el-input :model-value="auth.user?.username" disabled /></el-form-item>
        <el-form-item label="Họ và tên"><el-input v-model="profile.fullName" /></el-form-item>
        <el-form-item label="Email (nhận thông báo)"><el-input v-model="profile.email" /></el-form-item>
        <el-form-item label="Số điện thoại"><el-input v-model="profile.phone" /></el-form-item>
        <el-button type="primary" @click="saveProfile">Lưu</el-button>
      </el-form>
    </el-card>
    <el-card shadow="never">
      <template #header><b>Đổi mật khẩu</b></template>
      <el-form :model="pw" label-position="top">
        <el-form-item label="Mật khẩu hiện tại"><el-input v-model="pw.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="Mật khẩu mới (tối thiểu 6 ký tự)"><el-input v-model="pw.newPassword" type="password" show-password /></el-form-item>
        <el-button type="primary" @click="changePassword">Đổi mật khẩu</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const profile = reactive({ fullName: auth.user?.fullName, email: auth.user?.email, phone: auth.user?.phone })
const pw = reactive({ oldPassword: '', newPassword: '' })

async function saveProfile() {
  await api.put('/auth/me', profile)
  await auth.refresh()
  ElMessage.success('Đã lưu hồ sơ')
}

async function changePassword() {
  await api.post('/auth/change-password', pw)
  pw.oldPassword = pw.newPassword = ''
  ElMessage.success('Đã đổi mật khẩu')
}
</script>
