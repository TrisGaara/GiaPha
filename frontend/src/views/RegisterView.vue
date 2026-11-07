<template>
  <div class="auth-wrap">
    <el-card class="auth-card">
      <div class="logo">譜</div>
      <h2>Đăng ký tài khoản</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="Họ và tên" prop="fullName">
          <el-input v-model="form.fullName" />
        </el-form-item>
        <el-form-item label="Tên đăng nhập" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="Email (để nhận thông báo)" prop="email">
          <el-input v-model="form.email" type="email" />
        </el-form-item>
        <el-form-item label="Số điện thoại" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="Mật khẩu" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">Đăng ký</el-button>
      </el-form>
      <p class="switch">Đã có tài khoản? <router-link :to="{ path: '/login', query: $route.query }">Đăng nhập</router-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ fullName: '', username: '', email: '', phone: '', password: '' })
const rules = {
  fullName: [{ required: true, message: 'Nhập họ tên' }],
  username: [
    { required: true, message: 'Nhập tên đăng nhập' },
    { pattern: /^[a-zA-Z0-9._-]{3,50}$/, message: '3-50 ký tự: chữ không dấu, số, . _ -' }
  ],
  email: [{ type: 'email', message: 'Email không hợp lệ' }],
  password: [{ required: true, min: 6, message: 'Mật khẩu tối thiểu 6 ký tự' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.register(form)
    router.push(route.query.redirect || '/families')
  } finally {
    loading.value = false
  }
}
</script>
