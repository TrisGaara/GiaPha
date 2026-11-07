<template>
  <div class="auth-wrap">
    <el-card class="auth-card">
      <div class="logo">譜</div>
      <h2>Gia Phả Việt</h2>
      <p class="muted">Lưu giữ cội nguồn – Kết nối dòng tộc</p>
      <el-form :model="form" @submit.prevent="submit" label-position="top">
        <el-form-item label="Tên đăng nhập hoặc email">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="Mật khẩu">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">Đăng nhập</el-button>
      </el-form>
      <p class="switch">Chưa có tài khoản? <router-link :to="{ path: '/register', query: $route.query }">Đăng ký</router-link></p>
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
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push(route.query.redirect || '/families')
  } finally {
    loading.value = false
  }
}
</script>

<style>
.auth-wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 16px;
  background: radial-gradient(circle at top, #fdf3e1, #f2e6d4);
}
.auth-card {
  width: 100%;
  max-width: 400px;
  text-align: center;
}
.auth-card .logo {
  margin: 0 auto;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  font-size: 32px;
  color: #fde68a;
  background: #9b2c2c;
  border-radius: 10px;
}
.auth-card h2 {
  color: #9b2c2c;
  margin: 12px 0 4px;
}
.auth-card form {
  text-align: left;
  margin-top: 16px;
}
.auth-card .switch {
  margin-top: 16px;
  font-size: 14px;
}
</style>
