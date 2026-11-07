<template>
  <div class="page page--fluid" v-loading="!family">
    <template v-if="family">
      <div class="page-title">
        <div>
          <h2>{{ family.name }}</h2>
          <div class="muted">
            Họ {{ family.surname }}
            <template v-if="family.origin"> · {{ family.origin }}</template>
            <template v-if="family.ancestralHallAddress"> · Nhà thờ tổ: {{ family.ancestralHallAddress }}</template>
          </div>
        </div>
        <div class="toolbar" style="margin: 0">
          <el-tag effect="plain">{{ ROLE_LABELS[family.myRole] }}</el-tag>
          <el-tag v-if="isViewerMode" type="warning" effect="dark">👁 Đang xem như Khách</el-tag>
          <el-tag :type="FAMILY_STATUS[family.status].type">{{ FAMILY_STATUS[family.status].label }}</el-tag>
          <el-button v-if="family.myRole !== 'OWNER' && !auth.isSuperAdmin" size="small" text type="danger" @click="leave">
            Rời dòng họ
          </el-button>
        </div>
      </div>

      <el-alert v-if="family.status === 'PENDING'" type="warning" show-icon :closable="false" style="margin-bottom: 12px"
        title="Dòng họ đang chờ Quản trị hệ thống duyệt. Bạn vẫn có thể xây dựng phả hệ, nhưng chưa gửi được nhắc lịch tự động." />

      <!-- Dai thong bao Che do nguoi xem -->
      <div v-if="isViewerMode" class="viewer-banner" role="status">
        <span class="eye">👁</span>
        <div class="text">
          <b>Đang hiển thị ở Chế độ người xem (Khách vãng lai)</b>
          <span>Thành viên bị ẩn, ngày sinh/giỗ, liên hệ, tiểu sử… được che đúng như tài khoản Khách nhìn thấy. Các nút chỉnh sửa tạm ẩn.</span>
        </div>
        <el-button size="small" type="warning" plain @click="isViewerMode = false">Thoát chế độ xem</el-button>
      </div>

      <el-tabs v-model="tab" class="family-tabs">
        <el-tab-pane label="Cây phả hệ" name="tree">
          <FamilyTree v-if="tab === 'tree'" />
        </el-tab-pane>
        <el-tab-pane label="Thành viên" name="members">
          <MembersTab v-if="tab === 'members'" />
        </el-tab-pane>
        <el-tab-pane label="Sự kiện & Ngày giỗ" name="events">
          <EventsTab v-if="tab === 'events'" :key="viewKey" />
        </el-tab-pane>
        <el-tab-pane label="Lịch Âm – Dương" name="calendar">
          <CalendarTab v-if="tab === 'calendar'" :key="viewKey" />
        </el-tab-pane>
        <el-tab-pane v-if="isAdmin" label="Quản trị dòng họ" name="admin">
          <FamilyAdminTab v-if="tab === 'admin'" />
        </el-tab-pane>
      </el-tabs>

      <MemberDrawer ref="drawer" />
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import api, { setViewAs } from '../api'
import { useAuthStore } from '../stores/auth'
import { ROLE_LABELS, FAMILY_STATUS, atLeast } from '../constants'
import FamilyTree from '../components/FamilyTree.vue'
import MembersTab from '../components/MembersTab.vue'
import EventsTab from '../components/EventsTab.vue'
import CalendarTab from '../components/CalendarTab.vue'
import FamilyAdminTab from '../components/FamilyAdminTab.vue'
import MemberDrawer from '../components/MemberDrawer.vue'

const props = defineProps({ id: { type: String, required: true } })
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const family = ref(null)
const tree = ref({ members: [], maritalRelations: [] })
const tab = ref(route.query.tab || 'tree')
const drawer = ref()

const familyId = computed(() => Number(props.id))
const byId = computed(() => new Map(tree.value.members.map((m) => [m.id, m])))
// =====================================================================
// Che do nguoi xem (Viewer Mode): xem thu dong ho duoi goc nhin Khach
// =====================================================================

/** isViewerMode luu tren URL (?as=viewer): tai lai trang van giu, gui link cho nguoi khac kiem tra duoc */
const isViewerMode = computed({
  get: () => route.query.as === 'viewer',
  set: (on) => router.replace({ query: { ...route.query, as: on ? 'viewer' : undefined } })
})
/** Chi nguoi co quyen cao hon Khach moi can xem thu */
const canPreview = computed(() => atLeast(family.value?.myRole, 'MEMBER'))
/** Quyen hieu luc tren giao dien: dang xem thu -> nhu Khach */
const effectiveRole = computed(() => (isViewerMode.value ? 'VIEWER' : family.value?.myRole))
const canManage = computed(() => atLeast(effectiveRole.value, 'BRANCH_MANAGER'))
const isAdmin = computed(() => atLeast(effectiveRole.value, 'ADMIN'))
/** Doi khoa -> cac tab tu tai du lieu rieng (Su kien, Lich) duoc tao lai voi goc nhin moi */
const viewKey = computed(() => (isViewerMode.value ? 'guest' : 'normal'))

setViewAs(isViewerMode.value ? 'VIEWER' : null) // truoc lan tai du lieu dau tien

watch(isViewerMode, async (on) => {
  setViewAs(on ? 'VIEWER' : null)
  drawer.value?.close()
  if (on && tab.value === 'admin') tab.value = 'tree'
  await reloadTree() // backend tra ve cay da loc / che y het tai khoan Khach
})

onBeforeUnmount(() => setViewAs(null)) // roi trang dong ho -> khong ha quyen cac trang khac

async function reloadFamily() {
  // Luon lay bang quyen THAT: de biet co duoc xem thu hay khong, va giu nut thoat
  family.value = await api.get(`/families/${familyId.value}`, { skipViewAs: true })
}
async function reloadTree() {
  tree.value = await api.get(`/families/${familyId.value}/tree`)
}

watch(familyId, async () => {
  family.value = null
  try {
    await Promise.all([reloadFamily(), reloadTree()])
  } catch {
    router.push('/families')
  }
}, { immediate: true })

watch(tab, (t) => router.replace({ query: { ...route.query, tab: t } }))

async function leave() {
  await ElMessageBox.confirm('Bạn chắc chắn muốn rời dòng họ này?', 'Xác nhận', { type: 'warning' })
  await api.post(`/families/${familyId.value}/leave`)
  ElMessage.success('Đã rời dòng họ')
  router.push('/families')
}

provide('gp', {
  familyId,
  family,
  tree,
  byId,
  canManage,
  isAdmin,
  isViewerMode,
  canPreview,
  reloadTree,
  reloadFamily,
  openMember: (id) => drawer.value?.open(id),
  editMember: (id) => drawer.value?.edit(id),
  newMember: (preset) => drawer.value?.create(preset)
})
</script>

<style>
/* Dai thong bao Che do nguoi xem */
.viewer-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  padding: 8px 14px;
  border: 1px solid #f5d38a;
  border-left: 4px solid #e6a23c;
  border-radius: 8px;
  background: #fdf6ec;
  color: #7a4b0c;
  font-size: 13px;
}
.viewer-banner .eye {
  font-size: 20px;
}
.viewer-banner .text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.family-tabs > .el-tabs__content {
  overflow: visible;
}
</style>
