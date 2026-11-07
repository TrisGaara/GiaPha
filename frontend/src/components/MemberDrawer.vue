<template>
  <!-- Dien thoai: chiem toan man hinh; man hinh lon: 600px ben phai -->
  <el-drawer v-model="visible" :title="drawerTitle" :size="isMobile ? '100%' : '600px'" class="member-drawer">
    <!-- ============ NUT AN DANH (nguoi that da bi an khoi cay) ============ -->
    <el-empty v-if="mode === 'view' && current && current.placeholder" description="Thành viên này đã được Ban quản trị ẩn khỏi cây phả hệ." />

    <!-- ============ XEM HO SO ============ -->
    <template v-else-if="mode === 'view' && current">
      <div class="profile-head">
        <el-avatar :size="72" :src="current.avatarUrl || undefined" :class="current.gender === 'MALE' ? 'av-male' : 'av-female'">
          {{ initials(current.fullName) }}
        </el-avatar>
        <div>
          <h3>{{ current.fullName }}</h3>
          <div class="muted">
            <span v-if="current.otherName">{{ current.otherName }} · </span>
            {{ current.gender === 'MALE' ? 'Nam' : 'Nữ' }}
            <span v-if="current.generation"> · Đời thứ {{ current.generation }}</span>
            <span v-if="current.birthOrderLabel"> · {{ current.birthOrderLabel }}</span>
          </div>
          <div style="margin-top: 6px; display: flex; gap: 6px; flex-wrap: wrap">
            <el-tag v-if="!current.alive" type="info" size="small">Đã mất</el-tag>
            <el-tag v-if="!current.bloodLine" size="small" type="warning">{{ current.gender === 'MALE' ? 'Con rể' : 'Con dâu' }}</el-tag>
            <el-tag v-if="current.userId" size="small" type="success">Đã liên kết tài khoản</el-tag>
            <el-tag v-if="current.privacy && !current.privacy.visibleOnTree" size="small" type="danger" effect="plain">Ẩn khỏi cây phả hệ</el-tag>
            <el-tag v-if="current.privacy && !current.privacy.publicProfile" size="small" type="info" effect="plain">Không công khai với khách</el-tag>
            <el-tag v-if="current.hiddenFields?.length" size="small" type="info">🔒 Một số thông tin được ẩn</el-tag>
          </div>
        </div>
      </div>

      <div class="actions">
        <el-button v-if="current.canEdit" type="primary" :icon="Edit" @click="startEdit">Sửa</el-button>
        <el-button v-if="gp.canManage.value" @click="addChild">Thêm con</el-button>
        <el-button v-if="gp.canManage.value && current.bloodLine" @click="addSpouse">Thêm {{ current.gender === 'MALE' ? 'vợ' : 'chồng' }}</el-button>
        <el-button v-if="gp.isAdmin.value" type="danger" plain :icon="Delete" @click="remove">Xóa</el-button>
      </div>

      <el-descriptions :column="1" border size="small" class="desc">
        <el-descriptions-item label="Ngày sinh">
          <span v-if="masked('BIRTH_DATE')" class="locked">🔒 Đã ẩn</span>
          <template v-else>
            {{ fmtDate(current.birthDateSolar) || '—' }}
            <span v-if="current.birthLunar" class="muted"> (ÂL {{ current.birthLunar.label }})</span>
          </template>
        </el-descriptions-item>
        <template v-if="!current.alive">
          <el-descriptions-item v-if="masked('DEATH_DATE')" label="Ngày mất / giỗ"><span class="locked">🔒 Đã ẩn</span></el-descriptions-item>
          <template v-else>
            <el-descriptions-item label="Ngày mất">
              {{ fmtDate(current.deathDateSolar) || '—' }}
              <span v-if="current.deathLunar" class="muted"> (ÂL {{ current.deathLunar.label }})</span>
            </el-descriptions-item>
            <el-descriptions-item label="Ngày giỗ">
              <b v-if="current.deathLunar" style="color: #9b2c2c">{{ current.deathLunar.day }}/{{ current.deathLunar.month }} âm lịch</b>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="Nơi an táng">{{ current.burialPlace || '—' }}</el-descriptions-item>
          </template>
        </template>
        <el-descriptions-item label="Quê quán">
          <span v-if="masked('PROFILE')" class="locked">🔒 Đã ẩn</span><template v-else>{{ current.hometown || '—' }}</template>
        </el-descriptions-item>
        <el-descriptions-item label="Nghề nghiệp">
          <span v-if="masked('BIO')" class="locked">🔒 Đã ẩn</span><template v-else>{{ current.occupation || '—' }}</template>
        </el-descriptions-item>
        <el-descriptions-item label="Liên hệ">
          <span v-if="masked('CONTACT_INFO')" class="locked">🔒 Đã ẩn</span>
          <template v-else-if="current.phone || current.email || current.address">
            <div v-if="current.phone">☎ <a :href="'tel:' + current.phone">{{ current.phone }}</a></div>
            <div v-if="current.email">✉ <a :href="'mailto:' + current.email">{{ current.email }}</a></div>
            <div v-if="current.address">⌂ {{ current.address }}</div>
          </template>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="Cha">
          <a v-if="father" @click="open(father.id)">{{ father.fullName }}</a><span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="Mẹ">
          <a v-if="mother" @click="open(mother.id)">{{ mother.fullName }}</a><span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item :label="current.gender === 'MALE' ? 'Vợ' : 'Chồng'">
          <div v-if="spouses.length">
            <div v-for="s in spouses" :key="s.marriage.id" class="rel-row">
              <a @click="open(s.person.id)">{{ s.person.fullName }}</a>
              <span class="muted">({{ spouseTitle(s) }})</span>
              <el-dropdown v-if="gp.canManage.value" trigger="click" size="small" @command="(st) => changeMarriageStatus(s.marriage, st)">
                <el-tag size="small" :type="{ ACTIVE: 'success', DIVORCED: 'info', DECEASED: 'warning' }[s.marriage.status]" style="cursor: pointer">
                  {{ MARRIAGE_STATUS[s.marriage.status] }} ▾
                </el-tag>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="(label, st) in MARRIAGE_STATUS" :key="st" :command="st" :disabled="st === s.marriage.status">
                      {{ label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-tag v-else-if="s.marriage.status !== 'ACTIVE'" size="small" type="info">{{ MARRIAGE_STATUS[s.marriage.status] }}</el-tag>
              <el-button v-if="gp.canManage.value" link type="danger" size="small" @click="removeMarriage(s.marriage)">gỡ</el-button>
            </div>
          </div>
          <span v-else>—</span>
          <div v-if="gp.canManage.value" class="add-spouse">
            <el-select v-model="spouseForm.personId" filterable placeholder="Chọn người có sẵn" size="small" style="flex: 1">
              <el-option v-for="p in spouseCandidates" :key="p.id" :value="p.id" :label="`${p.fullName}${p.generation ? ' (đời ' + p.generation + ')' : ''}`" />
            </el-select>
            <el-input-number v-model="spouseForm.marriageOrder" :min="1" :max="20" size="small" placeholder="Thứ tự"
              :value-on-clear="null" controls-position="right" style="width: 100px" />
            <el-button size="small" :disabled="!spouseForm.personId" @click="linkSpouse">Gắn</el-button>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="Con">
          <!-- Che do xem: theo dung thu tu backend tra ve (quy uoc cua dong ho) -->
          <template v-if="!reordering">
            <ol v-if="children.length" class="kids">
              <li v-for="c in children" :key="c.id">
                <a @click="open(c.id)">{{ c.fullName }}</a>
                <span class="muted"> – {{ c.birthOrderLabel || `con thứ ${c.birthOrder}` }}</span>
                <span v-if="spouses.length > 1 && motherName(c)" class="muted"> (mẹ: {{ motherName(c) }})</span>
              </li>
            </ol>
            <span v-else>—</span>
            <el-button v-if="canReorder" link type="primary" size="small" @click="startReorder">⇅ Sắp xếp lại thứ tự</el-button>
          </template>

          <!-- Che do sap xep: keo tha hoac dung nut len/xuong -->
          <template v-else>
            <div class="muted hint">Kéo thả hoặc dùng ▲▼. Người trên cùng là con cả.</div>
            <ul class="reorder-list">
              <li v-for="(c, i) in orderDraft" :key="c.id" draggable="true" :class="{ dragging: dragIndex === i }"
                @dragstart="dragIndex = i" @dragover.prevent @drop="dropAt(i)" @dragend="dragIndex = null">
                <span class="handle">⠿</span>
                <b class="num">{{ i + 1 }}</b>
                <span class="name">{{ c.fullName }}</span>
                <span class="muted">{{ c.gender === 'MALE' ? 'nam' : 'nữ' }}</span>
                <el-button link size="small" :disabled="i === 0" @click="move(i, -1)">▲</el-button>
                <el-button link size="small" :disabled="i === orderDraft.length - 1" @click="move(i, 1)">▼</el-button>
              </li>
            </ul>
            <el-button size="small" @click="reordering = false">Hủy</el-button>
            <el-button size="small" type="primary" :loading="savingOrder" @click="saveOrder">Lưu thứ tự</el-button>
          </template>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="current.biography" class="block">
        <h4>Tiểu sử</h4>
        <p>{{ current.biography }}</p>
      </div>
      <div v-if="current.achievements" class="block">
        <h4>Thành tựu</h4>
        <p>{{ current.achievements }}</p>
      </div>
    </template>

    <!-- ============ SUA / THEM ============ -->
    <el-form v-else-if="mode !== 'view'" ref="formRef" :model="form" :rules="rules" label-position="top" class="member-form">
      <el-alert v-if="limitedEdit" type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="Bạn chỉ được cập nhật thông tin cá nhân. Quan hệ cha mẹ / đời do Ban quản trị quản lý." />
      <el-row :gutter="12">
        <el-col :xs="24" :span="14">
          <el-form-item label="Họ và tên" prop="fullName"><el-input v-model="form.fullName" /></el-form-item>
        </el-col>
        <el-col :xs="24" :span="10">
          <el-form-item label="Tên húy / tự / hiệu"><el-input v-model="form.otherName" /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :xs="24" :span="8">
          <el-form-item label="Giới tính">
            <el-radio-group v-model="form.gender">
              <el-radio-button value="MALE">Nam</el-radio-button>
              <el-radio-button value="FEMALE">Nữ</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="8">
          <el-form-item label="Đời thứ">
            <el-input-number v-model="form.generation" :min="1" :max="200" :disabled="limitedEdit" placeholder="Tự động" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="8">
          <el-form-item label="Con thứ">
            <el-input-number v-model="form.birthOrder" :min="1" :max="99" :disabled="limitedEdit" :value-on-clear="null"
              :placeholder="suggestion ? `Tự động: ${suggestion.nextBirthOrder}` : 'Tự động'" style="width: 100%" />
            <div v-if="mode === 'create' && suggestion" class="muted hint">
              Đã có {{ suggestion.childCount }} con<span v-if="suggestion.childCount"> (cuối: {{ suggestion.siblings.at(-1).fullName }})</span>.
              Bỏ trống để xếp thứ {{ suggestion.nextBirthOrder }}; nhập số nhỏ hơn để chèn, các con sau tự lùi.
            </div>
            <div v-else-if="mode === 'edit' && !limitedEdit && form.fatherId" class="muted hint">
              Đổi số sẽ chuyển người này đến vị trí đó, anh chị em khác tự dồn lại.
            </div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12">
          <el-form-item label="Cha">
            <el-select v-model="form.fatherId" filterable clearable :disabled="limitedEdit" style="width: 100%">
              <el-option v-for="p in fatherOptions" :key="p.id" :value="p.id" :label="personLabel(p)" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="12">
          <el-form-item label="Mẹ">
            <el-select v-model="form.motherId" filterable clearable :disabled="limitedEdit" style="width: 100%"
              :placeholder="form.fatherId && !motherOptions.length ? 'Người cha chưa có vợ trong phả hệ' : 'Chọn'">
              <el-option v-for="p in motherOptions" :key="p.id" :value="p.id" :label="spouseLabel(p, form.fatherId)" />
            </el-select>
            <div v-if="form.fatherId" class="muted hint">Chỉ hiển thị các đời vợ của người cha</div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item>
        <el-checkbox :model-value="!form.bloodLine" :disabled="limitedEdit" @update:model-value="(v) => (form.bloodLine = !v)">
          Người ngoài họ (con dâu / con rể)
        </el-checkbox>
      </el-form-item>

      <el-divider content-position="left">Ngày sinh</el-divider>
      <LunarSolarInput v-model:mode="birthMode" v-model:solar="form.birthDateSolar" v-model:day="form.birthLunarDay"
        v-model:month="form.birthLunarMonth" v-model:year="form.birthLunarYear" v-model:leap="form.birthLunarLeap" />

      <el-form-item style="margin-top: 12px">
        <el-switch v-model="form.alive" active-text="Còn sống" inactive-text="Đã mất" inline-prompt style="--el-switch-off-color: #6b7280" />
      </el-form-item>
      <template v-if="!form.alive">
        <el-divider content-position="left">Ngày mất (ngày giỗ)</el-divider>
        <LunarSolarInput v-model:mode="deathMode" v-model:solar="form.deathDateSolar" v-model:day="form.deathLunarDay"
          v-model:month="form.deathLunarMonth" v-model:year="form.deathLunarYear" v-model:leap="form.deathLunarLeap" allow-no-year />
        <el-form-item label="Nơi an táng" style="margin-top: 12px"><el-input v-model="form.burialPlace" /></el-form-item>
      </template>

      <el-divider content-position="left">Thông tin khác</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12"><el-form-item label="Quê quán"><el-input v-model="form.hometown" /></el-form-item></el-col>
        <el-col :xs="24" :span="12"><el-form-item label="Nghề nghiệp"><el-input v-model="form.occupation" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="Ảnh đại diện (URL)"><el-input v-model="form.avatarUrl" placeholder="https://..." /></el-form-item>
      <el-form-item label="Tiểu sử"><el-input v-model="form.biography" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="Thành tựu, khen thưởng"><el-input v-model="form.achievements" type="textarea" :rows="3" /></el-form-item>

      <el-divider content-position="left">Liên hệ</el-divider>
      <el-row :gutter="12">
        <el-col :xs="24" :span="10"><el-form-item label="Số điện thoại" prop="phone"><el-input v-model="form.phone" /></el-form-item></el-col>
        <el-col :xs="24" :span="14"><el-form-item label="Email" prop="email"><el-input v-model="form.email" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="Địa chỉ"><el-input v-model="form.address" /></el-form-item>

      <el-divider content-position="left">Quyền riêng tư</el-divider>
      <div class="privacy">
        <div class="privacy-row">
          <el-switch v-model="form.privacy.visibleOnTree" :disabled="!canChangeTreeVisibility" />
          <div>
            <b>Hiện trên cây phả hệ</b>
            <div class="muted">Tắt: chỉ Ban quản trị, Trưởng chi và chính người này nhìn thấy.
              <span v-if="!canChangeTreeVisibility">(Chỉ Ban quản trị / Trưởng chi được đổi)</span></div>
          </div>
        </div>
        <div class="privacy-row">
          <el-switch v-model="form.privacy.publicProfile" />
          <div>
            <b>Công khai chi tiết với khách (Viewer)</b>
            <div class="muted">Tắt: khách chỉ thấy tên, đời và quan hệ gia đình.</div>
          </div>
        </div>
        <div class="muted" style="margin: 4px 0 6px">Ẩn với khách (thành viên chính thức vẫn xem được):</div>
        <div class="privacy-grid">
          <el-checkbox v-model="form.privacy.hideBirthDate" :disabled="!form.privacy.publicProfile">Ngày sinh</el-checkbox>
          <el-checkbox v-model="form.privacy.hideDeathDate" :disabled="!form.privacy.publicProfile">Ngày mất / ngày giỗ</el-checkbox>
          <el-checkbox v-model="form.privacy.hideContactInfo" :disabled="!form.privacy.publicProfile">Điện thoại / địa chỉ</el-checkbox>
          <el-checkbox v-model="form.privacy.hideBio" :disabled="!form.privacy.publicProfile">Tiểu sử / nghề nghiệp</el-checkbox>
        </div>
      </div>

      <div class="form-footer">
        <el-button @click="cancel">Hủy</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ mode === 'create' ? 'Thêm' : 'Lưu' }}</el-button>
      </div>
    </el-form>
  </el-drawer>
</template>

<script setup>
import { computed, inject, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete } from '@element-plus/icons-vue'
import api from '../api'
import { fmtDate, MARRIAGE_STATUS, atLeast } from '../constants'
import LunarSolarInput from './LunarSolarInput.vue'
import { useIsMobile } from '../composables/useBreakpoint'

const gp = inject('gp')
const isMobile = useIsMobile()
const visible = ref(false)
const mode = ref('view') // view | edit | create
const currentId = ref(null)
const preset = ref({})
const formRef = ref()
const saving = ref(false)
const birthMode = ref('solar')
const deathMode = ref('lunar')
const spouseForm = reactive({ personId: null, marriageOrder: null }) // null = backend tu tang

// Mac dinh giong CSDL (V4): lien he an voi khach, con lai hien
const defaultPrivacy = () => ({
  visibleOnTree: true, publicProfile: true, hideBirthDate: false, hideDeathDate: false, hideContactInfo: true, hideBio: false
})
const emptyForm = () => ({
  fullName: '', otherName: '', gender: 'MALE', generation: null, birthOrder: null, fatherId: null, motherId: null,
  bloodLine: true, birthDateSolar: null, birthLunarDay: null, birthLunarMonth: null, birthLunarYear: null,
  birthLunarLeap: false, alive: true, deathDateSolar: null, deathLunarDay: null, deathLunarMonth: null,
  deathLunarYear: null, deathLunarLeap: false, burialPlace: '', hometown: '', occupation: '', biography: '',
  achievements: '', avatarUrl: '', phone: '', email: '', address: '',
  privacy: defaultPrivacy()
})
const form = reactive(emptyForm())
const canChangeTreeVisibility = computed(() => atLeast(gp.family.value.myRole, 'BRANCH_MANAGER'))
const masked = (group) => current.value?.hiddenFields?.includes(group)
const rules = {
  fullName: [{ required: true, message: 'Nhập họ tên' }],
  email: [{ type: 'email', message: 'Email không hợp lệ' }],
  phone: [{ pattern: /^[0-9+()\s.-]*$/, message: 'Số điện thoại không hợp lệ' }]
}

const members = computed(() => gp.tree.value.members)
const current = computed(() => gp.byId.value.get(currentId.value))
const father = computed(() => current.value && gp.byId.value.get(current.value.fatherId))
const mother = computed(() => current.value && gp.byId.value.get(current.value.motherId))
const spouses = computed(() => {
  if (!current.value) return []
  const id = current.value.id
  return gp.tree.value.maritalRelations
    .filter((x) => x.husbandId === id || x.wifeId === id)
    .map((x) => ({ marriage: x, person: gp.byId.value.get(x.husbandId === id ? x.wifeId : x.husbandId) }))
    .filter((s) => s.person)
    .sort((a, b) => a.marriage.marriageOrder - b.marriage.marriageOrder)
})
// Giu nguyen thu tu backend tra ve (da sap theo quy uoc thu tu con cua dong ho)
const children = computed(() =>
  current.value ? members.value.filter((c) => c.fatherId === current.value.id || c.motherId === current.value.id) : []
)
const motherName = (c) => gp.byId.value.get(c.motherId)?.fullName

// ---- Sap xep lai thu tu con (nhom anh chi em ma nguoi dang xem la dai dien, giong SiblingOrderService) ----
const reordering = ref(false)
const orderDraft = ref([])
const dragIndex = ref(null)
const savingOrder = ref(false)
const groupChildren = computed(() => {
  if (!current.value?.bloodLine) return []
  const id = current.value.id
  return members.value.filter((c) => {
    if (c.fatherId === id) return true
    if (c.motherId !== id) return false
    const f = gp.byId.value.get(c.fatherId)
    return !f || !f.bloodLine // con cua con gai voi con re
  })
})
const canReorder = computed(() => gp.canManage.value && groupChildren.value.length > 1)

function startReorder() {
  orderDraft.value = [...groupChildren.value].sort((a, b) => a.birthOrder - b.birthOrder)
  reordering.value = true
}
function move(i, delta) {
  const list = orderDraft.value
  ;[list[i], list[i + delta]] = [list[i + delta], list[i]]
}
function dropAt(i) {
  if (dragIndex.value === null || dragIndex.value === i) return
  const [item] = orderDraft.value.splice(dragIndex.value, 1)
  orderDraft.value.splice(i, 0, item)
  dragIndex.value = null
}
async function saveOrder() {
  savingOrder.value = true
  try {
    await api.put(`/families/${gp.familyId.value}/members/${current.value.id}/children/order`, {
      childIds: orderDraft.value.map((c) => c.id)
    })
    await gp.reloadTree()
    reordering.value = false
    ElMessage.success('Đã cập nhật thứ tự các con')
  } finally {
    savingOrder.value = false
  }
}

// ---- Goi y "con thu" khi them con: GET /members/{parentId}/next-birth-order ----
const suggestion = ref(null)
watch(
  () => [mode.value, form.fatherId, form.motherId],
  async ([m, fatherId, motherId]) => {
    suggestion.value = null
    if (m !== 'create' || (!fatherId && !motherId)) return
    const parentId = fatherId || motherId
    const spouseId = fatherId && motherId ? motherId : undefined
    suggestion.value = await api
      .get(`/families/${gp.familyId.value}/members/${parentId}/next-birth-order`, { params: { spouseId } })
      .catch(() => null)
  }
)
const spouseCandidates = computed(() => {
  if (!current.value) return []
  const taken = new Set(spouses.value.map((s) => s.person.id))
  return members.value.filter((p) => !p.placeholder && p.gender !== current.value.gender && !taken.has(p.id))
})
const males = computed(() => members.value.filter((p) => !p.placeholder && p.gender === 'MALE' && p.id !== currentId.value))
const females = computed(() => members.value.filter((p) => !p.placeholder && p.gender === 'FEMALE' && p.id !== currentId.value))
const limitedEdit = computed(() => mode.value === 'edit' && !atLeast(gp.family.value.myRole, 'BRANCH_MANAGER'))
const drawerTitle = computed(() =>
  mode.value === 'create' ? 'Thêm người vào phả hệ' : mode.value === 'edit' ? 'Sửa hồ sơ' : 'Hồ sơ thành viên'
)

const personLabel = (p) => `${p.fullName}${p.generation ? ' – đời ' + p.generation : ''}`

// ---- Rang buoc cha / me: me phai la mot doi vo cua cha (dong bo voi backend) ----
const relationsOf = (id) =>
  gp.tree.value.maritalRelations.filter((r) => r.husbandId === id || r.wifeId === id)
const fatherOptions = computed(() => {
  if (!form.motherId || form.fatherId) return males.value
  const husbands = new Set(relationsOf(form.motherId).map((r) => r.husbandId))
  return males.value.filter((p) => husbands.has(p.id))
})
const motherOptions = computed(() => {
  if (!form.fatherId) return females.value
  const wives = new Set(relationsOf(form.fatherId).map((r) => r.wifeId))
  return females.value.filter((p) => wives.has(p.id))
})
function spouseLabel(p, fatherId) {
  const r = fatherId && relationsOf(fatherId).find((x) => x.wifeId === p.id)
  return r ? `${p.fullName} (${r.marriageOrder === 1 ? 'vợ cả' : 'vợ ' + r.marriageOrder})` : personLabel(p)
}
// Doi cha -> neu me hien tai khong phai vo cua cha thi bo chon; neu cha chi co 1 vo thi tu chon
watch(() => form.fatherId, (fid) => {
  if (mode.value === 'view' || !fid) return
  const ids = motherOptions.value.map((p) => p.id)
  if (form.motherId && !ids.includes(form.motherId)) form.motherId = null
  if (!form.motherId && ids.length === 1) form.motherId = ids[0]
})
/** Danh xung giong backend (SpouseResponseDto.titleOf) */
function spouseTitle(s) {
  const o = s.marriage.marriageOrder
  if (s.person.gender === 'FEMALE') return ['Vợ cả', 'Vợ hai', 'Vợ ba'][o - 1] || `Vợ thứ ${o}`
  return o === 1 ? 'Chồng' : `Chồng thứ ${o}`
}
const initials = (n) => n.split(' ').slice(-1)[0]?.charAt(0) || '?'

function open(id) {
  currentId.value = id
  mode.value = 'view'
  spouseForm.personId = null
  spouseForm.marriageOrder = null
  reordering.value = false
  visible.value = true
}

function create(p = {}) {
  preset.value = p
  currentId.value = null
  Object.assign(form, emptyForm(), p.form || {})
  birthMode.value = 'solar'
  deathMode.value = 'lunar'
  mode.value = 'create'
  visible.value = true
}

function startEdit() {
  const m = current.value
  Object.assign(form, emptyForm(), {
    fullName: m.fullName, otherName: m.otherName || '', gender: m.gender, generation: m.generation,
    birthOrder: m.birthOrder, fatherId: m.fatherId, motherId: m.motherId, bloodLine: m.bloodLine,
    birthDateSolar: m.birthDateSolar, birthLunarDay: m.birthLunar?.day ?? null, birthLunarMonth: m.birthLunar?.month ?? null,
    birthLunarYear: m.birthLunar?.year ?? null, birthLunarLeap: !!m.birthLunar?.leap,
    alive: m.alive, deathDateSolar: m.deathDateSolar, deathLunarDay: m.deathLunar?.day ?? null,
    deathLunarMonth: m.deathLunar?.month ?? null, deathLunarYear: m.deathLunar?.year ?? null,
    deathLunarLeap: !!m.deathLunar?.leap, burialPlace: m.burialPlace || '', hometown: m.hometown || '',
    occupation: m.occupation || '', biography: m.biography || '', achievements: m.achievements || '',
    avatarUrl: m.avatarUrl || '', phone: m.phone || '', email: m.email || '', address: m.address || '',
    privacy: { ...defaultPrivacy(), ...(m.privacy || {}) }
  })
  birthMode.value = m.birthDateSolar || !m.birthLunar ? 'solar' : 'lunar'
  deathMode.value = m.deathDateSolar ? 'solar' : 'lunar'
  mode.value = 'edit'
}

function cancel() {
  if (mode.value === 'edit') mode.value = 'view'
  else visible.value = false
}

function buildPayload() {
  const p = { ...form }
  if (birthMode.value === 'solar') {
    Object.assign(p, { birthLunarDay: null, birthLunarMonth: null, birthLunarYear: null, birthLunarLeap: false })
  } else {
    p.birthDateSolar = null
  }
  if (deathMode.value === 'solar') {
    Object.assign(p, { deathLunarDay: null, deathLunarMonth: null, deathLunarYear: null, deathLunarLeap: false })
  } else {
    p.deathDateSolar = null
  }
  return p
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const fid = gp.familyId.value
    let saved
    if (mode.value === 'create') {
      const sp = preset.value.spouseOf
      if (sp) {
        // Tao con dau/con re + quan he hon nhan trong mot giao dich
        const res = await api.post(`/families/${fid}/members/${sp}/spouses`, {
          newSpouse: buildPayload(),
          marriageOrder: preset.value.spouseOrder || null
        })
        saved = { id: res.spouseId }
      } else if (preset.value.childOf) {
        // API them con: kiem tra me phai la mot doi vo cua cha
        saved = await api.post(`/families/${fid}/children`, buildPayload())
      } else {
        saved = await api.post(`/families/${fid}/members`, buildPayload())
      }
      ElMessage.success('Đã thêm vào phả hệ')
    } else {
      saved = await api.put(`/families/${fid}/members/${currentId.value}`, buildPayload())
      ElMessage.success('Đã lưu')
    }
    await gp.reloadTree()
    open(saved.id)
  } finally {
    saving.value = false
  }
}

function addChild() {
  const m = current.value
  const p = { childOf: m.id, form: {} }
  if (m.gender === 'MALE') {
    p.form.fatherId = m.id
    if (spouses.value.length === 1) p.form.motherId = spouses.value[0].person.id
    p.form.fullName = m.fullName.split(' ')[0] + ' '
  } else {
    p.form.motherId = m.id
    if (spouses.value.length === 1) p.form.fatherId = spouses.value[0].person.id
  }
  create(p) // "con thu" de trong -> backend tu danh so tiep theo
}

function addSpouse() {
  const m = current.value
  create({
    spouseOf: m.id,
    spouseOrder: null, // backend tu tang theo thu tu ket hon
    form: { gender: m.gender === 'MALE' ? 'FEMALE' : 'MALE', bloodLine: false, generation: m.generation }
  })
}

async function linkSpouse() {
  await api.post(`/families/${gp.familyId.value}/members/${current.value.id}/spouses`, {
    existingSpouseId: spouseForm.personId,
    marriageOrder: spouseForm.marriageOrder
  })
  spouseForm.personId = null
  await gp.reloadTree()
}

async function removeMarriage(mr) {
  const other = gp.byId.value.get(mr.husbandId === currentId.value ? mr.wifeId : mr.husbandId)
  const removeSpouse = other && !other.bloodLine
  await ElMessageBox.confirm(
    removeSpouse
      ? `Gỡ quan hệ hôn nhân? "${other.fullName}" là người ngoài họ, sẽ bị xóa khỏi phả hệ nếu không còn liên kết nào khác.`
      : 'Gỡ quan hệ hôn nhân này?',
    'Xác nhận', { type: 'warning' }
  )
  await api.delete(`/families/${gp.familyId.value}/marital-relations/${mr.id}`, {
    params: { removeSpouseOnlyMember: removeSpouse }
  })
  await gp.reloadTree()
}

async function changeMarriageStatus(mr, status) {
  await api.put(`/families/${gp.familyId.value}/marital-relations/${mr.id}`, {
    marriageOrder: mr.marriageOrder, status, note: mr.note
  })
  await gp.reloadTree()
}

async function remove() {
  await ElMessageBox.confirm(`Xóa "${current.value.fullName}" khỏi phả hệ? Thao tác không thể hoàn tác.`, 'Xác nhận xóa', {
    type: 'warning', confirmButtonText: 'Xóa', confirmButtonClass: 'el-button--danger'
  })
  await api.delete(`/families/${gp.familyId.value}/members/${currentId.value}`)
  ElMessage.success('Đã xóa')
  visible.value = false
  await gp.reloadTree()
}

/** Mo thang form sua (nut ✎ tren o cay pha he) - chi khi nguoi dung duoc sua ho so nay */
function edit(id) {
  open(id)
  if (current.value?.canEdit) startEdit()
}

defineExpose({ open, edit, create, close: () => (visible.value = false) })
</script>

<style scoped>
.profile-head {
  display: flex;
  gap: 16px;
  align-items: center;
}
.profile-head h3 {
  margin: 0 0 4px;
  font-size: 20px;
}
.av-male { background: var(--gp-male); }
.av-female { background: var(--gp-female); }
.actions {
  margin: 16px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.actions .el-button + .el-button {
  margin-left: 0;
}
.desc a,
.kids a,
.rel-row a {
  color: var(--el-color-primary);
  cursor: pointer;
}
.kids {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
}
.reorder-list {
  list-style: none;
  padding: 0;
  margin: 6px 0 8px;
}
.reorder-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  margin-bottom: 4px;
  border: 1px solid #eadfcd;
  border-radius: 6px;
  background: #fffdf8;
  cursor: grab;
}
.reorder-list li.dragging {
  opacity: 0.4;
}
.reorder-list .handle {
  color: #b7a88f;
}
.reorder-list .num {
  width: 18px;
  color: var(--el-color-primary);
}
.reorder-list .name {
  flex: 1;
}
.rel-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.locked {
  color: #8a8178;
  font-style: italic;
}
.privacy-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}
.privacy-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 12px;
}
.hint {
  font-size: 12px;
  line-height: 1.4;
}
@media (max-width: 640px) {
  .privacy-grid {
    grid-template-columns: 1fr;
  }
  .profile-head h3 {
    font-size: 18px;
  }
  .reorder-list li {
    padding: 8px; /* vung cham lon hon */
  }
}
.add-spouse {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.block h4 {
  margin: 20px 0 6px;
  color: var(--el-color-primary);
}
.block p {
  white-space: pre-wrap;
  margin: 0;
  line-height: 1.6;
}
.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
  position: sticky;
  bottom: 0;
  background: #fff;
  padding: 12px 0;
}
</style>
