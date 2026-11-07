<template>
  <div class="page" style="max-width: 760px">
    <div class="page-title">
      <h2>Thông báo</h2>
      <el-button size="small" @click="markAll" :disabled="!list.some((n) => !n.seen)">Đánh dấu tất cả đã đọc</el-button>
    </div>
    <el-empty v-if="!loading && !list.length" description="Chưa có thông báo" />
    <div v-loading="loading">
      <el-card v-for="n in list" :key="n.id" shadow="never" class="noti" :class="{ unread: !n.seen }" @click="open(n)">
        <div class="t">{{ n.title }}</div>
        <div v-if="n.content">{{ n.content }}</div>
        <div class="muted">{{ fmtDateTime(n.createdAt) }}</div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { fmtDateTime } from '../constants'

const router = useRouter()
const list = ref([])
const loading = ref(true)
const changed = () => window.dispatchEvent(new Event('gp:notifications-changed'))

async function load() {
  loading.value = true
  try {
    list.value = await api.get('/notifications')
  } finally {
    loading.value = false
  }
}

async function open(n) {
  if (!n.seen) {
    await api.post(`/notifications/${n.id}/seen`)
    n.seen = true
    changed()
  }
  if (n.familyId) router.push(`/families/${n.familyId}`)
}

async function markAll() {
  await api.post('/notifications/seen-all')
  list.value.forEach((n) => (n.seen = true))
  changed()
}

onMounted(load)
</script>

<style scoped>
.noti {
  margin-bottom: 8px;
  cursor: pointer;
}
.noti.unread {
  border-left: 4px solid var(--el-color-primary);
  background: #fffaf3;
}
.t {
  font-weight: 600;
  margin-bottom: 4px;
}
</style>
