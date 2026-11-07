<template>
  <el-container class="layout">
    <el-header v-if="auth.loggedIn" class="header">
      <router-link to="/families" class="brand">
        <span class="seal">譜</span>
        <span>Gia Phả Việt</span>
      </router-link>
      <nav class="nav">
        <router-link to="/families">Dòng họ của tôi</router-link>
        <router-link v-if="auth.isSuperAdmin" to="/admin">Quản trị hệ thống</router-link>
      </nav>
      <div class="right">
        <router-link to="/notifications" class="bell" title="Thông báo">
          <el-badge :value="unread" :hidden="!unread" :max="99">
            <el-icon :size="20"><Bell /></el-icon>
          </el-badge>
        </router-link>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><User /></el-icon> {{ auth.user?.fullName || auth.user?.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">Hồ sơ tài khoản</el-dropdown-item>
              <el-dropdown-item command="logout" divided>Đăng xuất</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Bell, User, ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from './stores/auth'
import api from './api'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const unread = ref(0)
let timer

async function loadUnread() {
  if (!auth.loggedIn) return
  try {
    unread.value = (await api.get('/notifications/unread-count')).count
  } catch {
    /* bo qua */
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 60000)
  window.addEventListener('gp:notifications-changed', loadUnread)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('gp:notifications-changed', loadUnread)
})
watch(() => route.fullPath, loadUnread)
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: linear-gradient(90deg, #7c2323, #9b2c2c);
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fde68a;
  font-weight: 700;
  font-size: 18px;
  text-decoration: none;
  white-space: nowrap;
}
.seal {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 2px solid #fde68a;
  border-radius: 6px;
  font-size: 18px;
}
.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}
.nav a {
  color: #fbe9d0;
  text-decoration: none;
}
.nav a.router-link-active {
  color: #fff;
  font-weight: 600;
  border-bottom: 2px solid #fde68a;
}
.right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.bell {
  color: #fff;
  display: flex;
}
.user {
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  outline: none;
}
.main {
  padding: 0;
}
@media (max-width: 640px) {
  .header {
    gap: 10px;
    padding: 0 12px;
  }
  .brand span:last-child,
  .user {
    font-size: 0;
  }
  .user .el-icon {
    font-size: 18px;
  }
}
</style>
