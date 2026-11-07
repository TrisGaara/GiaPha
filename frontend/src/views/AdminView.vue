<template>
  <div class="page page--fluid">
    <div class="page-title">
      <h2>Quản trị hệ thống</h2>
      <el-button @click="runReminders">Chạy nhắc lịch ngay</el-button>
    </div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8"><el-card shadow="never"><el-statistic title="Dòng họ" :value="stats.families || 0" /></el-card></el-col>
      <el-col :span="8"><el-card shadow="never"><el-statistic title="Chờ duyệt" :value="stats.pendingFamilies || 0" /></el-card></el-col>
      <el-col :span="8"><el-card shadow="never"><el-statistic title="Người dùng" :value="stats.users || 0" /></el-card></el-col>
    </el-row>

    <el-tabs v-model="tab">
      <el-tab-pane label="Dòng họ" name="families">
        <el-table :data="families" stripe>
          <el-table-column label="Dòng họ" min-width="220">
            <template #default="{ row }">
              <router-link :to="`/families/${row.id}`"><b>{{ row.name }}</b></router-link>
              <div class="muted">Họ {{ row.surname }} · {{ row.origin }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="memberCount" label="Người trong phả" width="130" />
          <el-table-column prop="userCount" label="Tài khoản" width="100" />
          <el-table-column label="Ngày tạo" width="160">
            <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="Trạng thái" width="120">
            <template #default="{ row }">
              <el-tag :type="FAMILY_STATUS[row.status].type">{{ FAMILY_STATUS[row.status].label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column width="180" align="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="setStatus(row, 'ACTIVE')">Duyệt</el-button>
              <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" plain @click="setStatus(row, 'LOCKED')">Khóa</el-button>
              <el-button v-if="row.status === 'LOCKED'" size="small" @click="setStatus(row, 'ACTIVE')">Mở khóa</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="Người dùng" name="users">
        <el-table :data="users" stripe>
          <el-table-column prop="username" label="Tên đăng nhập" width="160" />
          <el-table-column prop="fullName" label="Họ tên" min-width="160" />
          <el-table-column prop="email" label="Email" min-width="180" />
          <el-table-column prop="phone" label="Điện thoại" width="130" />
          <el-table-column label="Vai trò" width="130">
            <template #default="{ row }">{{ row.systemRole === 'SUPER_ADMIN' ? 'Quản trị hệ thống' : 'Người dùng' }}</template>
          </el-table-column>
          <el-table-column label="Hoạt động" width="100">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" :disabled="row.systemRole === 'SUPER_ADMIN'" @change="(v) => setEnabled(row, v)" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { FAMILY_STATUS, fmtDateTime } from '../constants'

const tab = ref('families')
const stats = ref({})
const families = ref([])
const users = ref([])

async function load() {
  ;[stats.value, families.value, users.value] = await Promise.all([
    api.get('/admin/stats'), api.get('/admin/families'), api.get('/admin/users')
  ])
}

async function setStatus(row, status) {
  if (status === 'LOCKED') {
    await ElMessageBox.confirm(`Khóa dòng họ "${row.name}"? Thành viên sẽ không truy cập được.`, 'Xác nhận', { type: 'warning' })
  }
  await api.put(`/admin/families/${row.id}/status`, { status })
  ElMessage.success('Đã cập nhật')
  await load()
}

async function setEnabled(row, enabled) {
  await api.put(`/admin/users/${row.id}/enabled`, { enabled })
  row.enabled = enabled
}

async function runReminders() {
  const r = await api.post('/admin/reminders/run')
  ElMessage.success(`Đã gửi ${r.sent} thông báo nhắc lịch`)
}

onMounted(load)
</script>
