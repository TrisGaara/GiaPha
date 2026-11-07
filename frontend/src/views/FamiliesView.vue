<template>
  <div class="page page--fluid">
    <div class="page-title">
      <h2>Dòng họ của tôi</h2>
      <div class="toolbar" style="margin: 0">
        <el-input v-model="code" placeholder="Nhập mã tham gia" style="width: 180px" @keyup.enter="goJoin" />
        <el-button @click="goJoin" :disabled="!code.trim()">Tham gia</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">Tạo dòng họ mới</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !families.length" description="Bạn chưa thuộc dòng họ nào. Hãy tạo mới hoặc tham gia bằng mã mời." />

    <div class="grid" v-loading="loading">
      <el-card v-for="f in families" :key="f.id" shadow="hover" class="family-card" @click="$router.push(`/families/${f.id}`)">
        <div class="fc-head">
          <div class="surname">{{ f.surname }}</div>
          <div>
            <div class="fname">{{ f.name }}</div>
            <div class="muted">{{ f.origin || 'Chưa cập nhật nguyên quán' }}</div>
          </div>
        </div>
        <div class="fc-meta">
          <el-tag size="small" effect="plain">{{ ROLE_LABELS[f.myRole] }}</el-tag>
          <el-tag size="small" :type="FAMILY_STATUS[f.status].type">{{ FAMILY_STATUS[f.status].label }}</el-tag>
          <span class="muted">{{ f.memberCount }} người trong phả · {{ f.userCount }} tài khoản</span>
        </div>
      </el-card>
    </div>

    <template v-if="requests.length">
      <h3 style="margin-top: 32px">Yêu cầu tham gia đã gửi</h3>
      <el-table :data="requests" size="small">
        <el-table-column prop="familyName" label="Dòng họ" />
        <el-table-column prop="message" label="Lời nhắn" />
        <el-table-column label="Trạng thái" width="140">
          <template #default="{ row }">
            <el-tag :type="{ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[row.status]" size="small">
              {{ { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Ngày gửi" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="createVisible" title="Tạo dòng họ mới" width="560px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
        title="Bạn sẽ là Tộc trưởng (quản trị cao nhất) của dòng họ này. Dòng họ mới có thể cần Quản trị hệ thống duyệt." />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="12">
          <el-col :xs="24" :span="8">
            <el-form-item label="Họ" prop="surname"><el-input v-model="form.surname" placeholder="Nguyễn" /></el-form-item>
          </el-col>
          <el-col :xs="24" :span="16">
            <el-form-item label="Tên dòng họ / chi tộc" prop="name">
              <el-input v-model="form.name" placeholder="Họ Nguyễn Đình – làng Đông Ngạc" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Nguyên quán"><el-input v-model="form.origin" /></el-form-item>
        <el-form-item label="Địa chỉ nhà thờ tổ"><el-input v-model="form.ancestralHallAddress" /></el-form-item>
        <el-form-item label="Giới thiệu / truyền thống"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">Hủy</el-button>
        <el-button type="primary" :loading="saving" @click="create">Tạo</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { ROLE_LABELS, FAMILY_STATUS, fmtDateTime } from '../constants'

const router = useRouter()
const families = ref([])
const requests = ref([])
const loading = ref(true)
const code = ref('')
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({ surname: '', name: '', origin: '', ancestralHallAddress: '', description: '' })
const rules = {
  surname: [{ required: true, message: 'Nhập họ' }],
  name: [{ required: true, message: 'Nhập tên dòng họ' }]
}

async function load() {
  loading.value = true
  try {
    ;[families.value, requests.value] = await Promise.all([api.get('/families'), api.get('/join-requests/mine')])
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { surname: '', name: '', origin: '', ancestralHallAddress: '', description: '' })
  createVisible.value = true
}

async function create() {
  await formRef.value.validate()
  saving.value = true
  try {
    const f = await api.post('/families', form)
    ElMessage.success(f.status === 'PENDING' ? 'Đã tạo dòng họ – đang chờ Quản trị hệ thống duyệt' : 'Đã tạo dòng họ')
    createVisible.value = false
    router.push(`/families/${f.id}`)
  } finally {
    saving.value = false
  }
}

function goJoin() {
  if (code.value.trim()) router.push({ path: '/join', query: { code: code.value.trim().toUpperCase() } })
}

onMounted(load)
</script>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  min-height: 60px;
}
.family-card {
  cursor: pointer;
  border-top: 4px solid var(--el-color-primary);
}
.fc-head {
  display: flex;
  gap: 14px;
  align-items: center;
}
.surname {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #fdf3e1;
  color: var(--el-color-primary);
  font-weight: 700;
  font-size: 15px;
  border: 2px solid var(--gp-gold);
  flex-shrink: 0;
  text-align: center;
}
.fname {
  font-weight: 600;
  font-size: 16px;
}
.fc-meta {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
