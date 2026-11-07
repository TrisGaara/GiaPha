<template>
  <el-row :gutter="20">
    <el-col :md="14" :xs="24">
      <!-- Yeu cau tham gia -->
      <el-card shadow="never" class="section">
        <template #header><b>Yêu cầu tham gia đang chờ duyệt</b> <el-badge :value="requests.length" :hidden="!requests.length" /></template>
        <el-empty v-if="!requests.length" description="Không có yêu cầu nào" :image-size="50" />
        <div v-for="r in requests" :key="r.id" class="req">
          <div>
            <b>{{ r.fullName }}</b> <span class="muted">@{{ r.username }} · {{ r.email || r.phone || '' }}</span>
            <div class="msg" v-if="r.message">“{{ r.message }}”</div>
            <div class="muted">{{ fmtDateTime(r.createdAt) }}</div>
          </div>
          <div class="req-actions">
            <el-select v-model="approveForm[r.id].role" size="small" style="width: 150px">
              <el-option value="MEMBER" :label="ROLE_LABELS.MEMBER" />
              <el-option value="VIEWER" :label="ROLE_LABELS.VIEWER" />
              <el-option v-if="isOwner" value="ADMIN" :label="ROLE_LABELS.ADMIN" />
            </el-select>
            <el-select v-model="approveForm[r.id].memberId" size="small" filterable clearable placeholder="Gắn với người trên phả hệ" style="width: 220px">
              <el-option v-for="m in unlinkedMembers" :key="m.id" :value="m.id" :label="memberLabel(m)" />
            </el-select>
            <el-button size="small" type="primary" @click="approve(r)">Duyệt</el-button>
            <el-button size="small" @click="reject(r)">Từ chối</el-button>
          </div>
        </div>
      </el-card>

      <!-- Phan quyen -->
      <el-card shadow="never" class="section">
        <template #header><b>Phân quyền thành viên có tài khoản</b></template>
        <el-table :data="roles" size="small">
          <el-table-column label="Người dùng" min-width="160">
            <template #default="{ row }">
              <b>{{ row.fullName }}</b>
              <div class="muted">@{{ row.username }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Vai trò" width="160">
            <template #default="{ row }">
              <el-tag v-if="row.role === 'OWNER'" type="danger">{{ ROLE_LABELS.OWNER }}</el-tag>
              <el-select v-else v-model="edits[row.id].role" size="small" :disabled="row.role === 'ADMIN' && !isOwner">
                <el-option v-for="r in assignableRoles" :key="r" :value="r" :label="ROLE_LABELS[r]" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="Nhánh quản lý / Người trên phả hệ" min-width="220">
            <template #default="{ row }">
              <template v-if="row.role !== 'OWNER'">
                <el-select v-if="edits[row.id].role === 'BRANCH_MANAGER'" v-model="edits[row.id].branchRootMemberId" size="small"
                  filterable placeholder="Gốc nhánh (chi/phòng)" style="width: 100%; margin-bottom: 4px">
                  <el-option v-for="m in bloodline" :key="m.id" :value="m.id" :label="memberLabel(m)" />
                </el-select>
              </template>
              <el-select v-model="edits[row.id].linkedMemberId" size="small" filterable clearable placeholder="Chưa gắn" style="width: 100%">
                <el-option v-for="m in linkableFor(row)" :key="m.id" :value="m.id" :label="memberLabel(m)" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column width="120" align="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="saveRole(row)">Lưu</el-button>
              <el-button v-if="row.role !== 'OWNER'" link type="danger" size="small" @click="removeRole(row)">Xóa</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>

    <el-col :md="10" :xs="24">
      <!-- Ma moi + QR -->
      <el-card shadow="never" class="section">
        <template #header><b>Mời thành viên</b></template>
        <div class="invite">
          <img v-if="qr" :src="qr" alt="QR tham gia dòng họ" width="180" height="180" />
          <div>
            <div class="muted">Mã tham gia</div>
            <div class="code">{{ family.joinCode }}</div>
            <el-input :model-value="joinLink" readonly size="small" style="margin: 8px 0">
              <template #append><el-button @click="copy">Sao chép</el-button></template>
            </el-input>
            <el-button size="small" @click="regenerate">Tạo mã mới</el-button>
          </div>
        </div>
        <p class="muted">Người thân quét QR hoặc mở đường link, đăng nhập và gửi yêu cầu. Ban quản trị duyệt và gắn họ vào đúng vị trí trên phả hệ.</p>
      </el-card>

      <!-- Thong tin dong ho -->
      <el-card shadow="never" class="section">
        <template #header><b>Thông tin dòng họ</b></template>
        <el-form :model="info" label-position="top" size="small">
          <el-row :gutter="8">
            <el-col :xs="24" :span="8"><el-form-item label="Họ"><el-input v-model="info.surname" /></el-form-item></el-col>
            <el-col :xs="24" :span="16"><el-form-item label="Tên dòng họ"><el-input v-model="info.name" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="Nguyên quán"><el-input v-model="info.origin" /></el-form-item>
          <el-form-item label="Địa chỉ nhà thờ tổ"><el-input v-model="info.ancestralHallAddress" /></el-form-item>
          <el-form-item label="Giới thiệu"><el-input v-model="info.description" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="Thứ tự các con trên cây phả hệ">
            <el-radio-group v-model="info.childSortMode">
              <el-radio value="BIRTH_ORDER">Theo thứ tự sinh (con cả bên trái)</el-radio>
              <el-radio value="SONS_FIRST">Con trai trước, con gái sau</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Telegram chat ID (nhóm nhận nhắc lịch)"><el-input v-model="info.telegramChatId" placeholder="-100123456789" /></el-form-item>
          <el-button type="primary" @click="saveInfo">Lưu thông tin</el-button>
          <el-button v-if="isOwner" type="danger" plain @click="deleteFamily">Xóa dòng họ</el-button>
        </el-form>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import api from '../api'
import { ROLE_LABELS, fmtDateTime } from '../constants'

const gp = inject('gp')
const router = useRouter()
const family = computed(() => gp.family.value)
const isOwner = computed(() => family.value.myRole === 'OWNER')
const requests = ref([])
const roles = ref([])
const approveForm = reactive({})
const edits = reactive({})
const qr = ref('')
const info = reactive({})

const base = () => `/families/${gp.familyId.value}`
const joinLink = computed(() => `${location.origin}/join?code=${family.value.joinCode}`)
const assignableRoles = computed(() => (isOwner.value ? ['ADMIN', 'BRANCH_MANAGER', 'MEMBER', 'VIEWER'] : ['BRANCH_MANAGER', 'MEMBER', 'VIEWER']))
const bloodline = computed(() => gp.tree.value.members.filter((m) => m.bloodLine))
const unlinkedMembers = computed(() => gp.tree.value.members.filter((m) => !m.userId))
const memberLabel = (m) => `${m.fullName}${m.generation ? ' – đời ' + m.generation : ''}`
const linkableFor = (row) => gp.tree.value.members.filter((m) => !m.userId || m.userId === row.userId)

async function loadRequests() {
  requests.value = await api.get(`${base()}/join-requests`, { params: { status: 'PENDING' } })
  for (const r of requests.value) approveForm[r.id] ||= { role: 'MEMBER', memberId: null }
}
async function loadRoles() {
  roles.value = await api.get(`${base()}/roles`)
  for (const r of roles.value) {
    edits[r.id] = { role: r.role, branchRootMemberId: r.branchRootMemberId, linkedMemberId: r.linkedMemberId }
  }
}

async function approve(r) {
  await api.post(`${base()}/join-requests/${r.id}/approve`, approveForm[r.id])
  ElMessage.success(`Đã duyệt ${r.fullName}`)
  await Promise.all([loadRequests(), loadRoles(), gp.reloadTree(), gp.reloadFamily()])
}
async function reject(r) {
  await ElMessageBox.confirm(`Từ chối yêu cầu của ${r.fullName}?`, 'Xác nhận', { type: 'warning' })
  await api.post(`${base()}/join-requests/${r.id}/reject`)
  await loadRequests()
}

async function saveRole(row) {
  const e = edits[row.id]
  await api.put(`${base()}/roles/${row.id}`, {
    role: row.role === 'OWNER' ? 'OWNER' : e.role,
    branchRootMemberId: e.branchRootMemberId,
    linkedMemberId: e.linkedMemberId
  })
  ElMessage.success('Đã cập nhật')
  await Promise.all([loadRoles(), gp.reloadTree()])
}
async function removeRole(row) {
  await ElMessageBox.confirm(`Xóa ${row.fullName} khỏi dòng họ?`, 'Xác nhận', { type: 'warning' })
  await api.delete(`${base()}/roles/${row.id}`)
  await Promise.all([loadRoles(), gp.reloadTree(), gp.reloadFamily()])
}

async function regenerate() {
  await ElMessageBox.confirm('Mã cũ sẽ không dùng được nữa. Tiếp tục?', 'Tạo mã mới', { type: 'warning' })
  await api.post(`${base()}/join-code`)
  await gp.reloadFamily()
}
async function copy() {
  await navigator.clipboard.writeText(joinLink.value)
  ElMessage.success('Đã sao chép đường link')
}

function fillInfo() {
  const f = family.value
  Object.assign(info, {
    name: f.name, surname: f.surname, origin: f.origin, ancestralHallAddress: f.ancestralHallAddress,
    description: f.description, telegramChatId: f.telegramChatId, childSortMode: f.childSortMode
  })
}
async function saveInfo() {
  await api.put(base(), info)
  ElMessage.success('Đã lưu')
  await Promise.all([gp.reloadFamily(), gp.reloadTree()]) // cay sap xep lai theo quy uoc moi
}
async function deleteFamily() {
  const { value } = await ElMessageBox.prompt(
    `Toàn bộ phả hệ, sự kiện và dữ liệu sẽ bị xóa vĩnh viễn. Gõ "${family.value.surname}" để xác nhận.`,
    'Xóa dòng họ', { type: 'error', confirmButtonText: 'Xóa vĩnh viễn', confirmButtonClass: 'el-button--danger' }
  )
  if (value !== family.value.surname) return ElMessage.warning('Xác nhận không khớp')
  await api.delete(base())
  router.push('/families')
}

watch(() => family.value.joinCode, async () => {
  fillInfo()
  qr.value = await QRCode.toDataURL(joinLink.value, { width: 360, margin: 1, color: { dark: '#7c2323' } })
}, { immediate: true })

onMounted(() => Promise.all([loadRequests(), loadRoles()]))
</script>

<style scoped>
.section {
  margin-bottom: 16px;
}
.req {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 0;
  border-bottom: 1px dashed #eee;
}
.req-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}
.req-actions .el-button + .el-button {
  margin-left: 0;
}
.msg {
  font-style: italic;
  margin: 4px 0;
}
.invite {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.code {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--el-color-primary);
  font-family: monospace;
}
</style>
