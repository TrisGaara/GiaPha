<template>
  <!--
    Hop thoai cau hinh xuat bao cao gia pha.
    - Danh sach: PDF (xem / in hoac tai ve) va Excel, tao o backend (OpenPDF / Apache POI) theo cac cot da chon.
    - So do cay: PDF vector ve o may chu (dung goc + so doi dang xem tren cay) - giong nhau tren moi may / trinh duyet.
  -->
  <el-dialog :model-value="modelValue" title="Xuất báo cáo / In gia phả" width="640px" @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top" class="export-form">
      <el-form-item label="Dạng xuất">
        <el-radio-group v-model="opt.format">
          <el-radio-button value="LIST">Danh sách thành viên</el-radio-button>
          <el-radio-button value="TREE">Sơ đồ cây</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="opt.format === 'LIST'">
        <el-form-item>
          <template #label>
            <span>Thông tin muốn xuất</span>
            <el-button link type="primary" size="small" style="margin-left: 8px" @click="selectAll(true)">Chọn tất cả</el-button>
            <el-button link size="small" @click="selectAll(false)">Bỏ chọn</el-button>
          </template>
          <el-checkbox-group v-model="opt.fields" class="field-grid">
            <el-checkbox v-for="f in FIELDS" :key="f.key" :value="f.key" :disabled="f.key === 'FULL_NAME'">
              {{ f.label }}
              <span v-if="f.hint" class="muted">· {{ f.hint }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-form-item label="Sắp xếp">
          <el-radio-group v-model="opt.sort">
            <el-radio value="BRANCH">Theo nhánh (phả ký: người → vợ/chồng → các con)</el-radio>
            <el-radio value="GENERATION">Theo từng đời</el-radio>
          </el-radio-group>
        </el-form-item>
      </template>

      <template v-else>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
          :title="`Sơ đồ được vẽ thành PDF vector: in khổ nào cũng nét, giống nhau trên mọi máy. Phạm vi: ${scopeLabel}, tối đa ${maxDepth} đời (theo lựa chọn trên cây).`" />
        <el-form-item>
          <el-switch v-model="opt.showDates" active-text="Hiện năm sinh – mất và ngày giỗ trên ô" />
        </el-form-item>
      </template>

      <el-row :gutter="12">
        <el-col :xs="24" :span="12">
          <el-form-item label="Khổ giấy">
            <el-radio-group v-model="opt.paper">
              <el-radio-button v-for="p in papers" :key="p" :value="p">{{ p }}</el-radio-button>
            </el-radio-group>
            <div v-if="opt.format === 'TREE'" class="muted hint">Cây lớn: chọn A2 / A1 / A0 để chữ rõ khi in khổ lớn (treo nhà thờ họ).</div>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="12">
          <el-form-item label="Hướng giấy">
            <el-radio-group v-model="opt.landscape">
              <el-radio-button :value="false">Dọc</el-radio-button>
              <el-radio-button :value="true">Ngang</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item>
        <el-switch v-model="opt.publicView" active-text="Bản công khai: che thông tin riêng tư như tài khoản Khách" />
        <div class="muted hint">
          Tắt = bản nội bộ đầy đủ (số điện thoại, người bị ẩn…). Bật khi in để phát cho họ hàng hoặc đăng công khai.
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="actions">
        <el-button @click="emit('update:modelValue', false)">Hủy</el-button>
        <template v-if="opt.format === 'LIST'">
          <el-button :loading="busy === 'excel'" @click="exportFile('excel')">Tải Excel</el-button>
          <el-button :loading="busy === 'pdf'" @click="exportFile('pdf')">Tải PDF</el-button>
          <el-button type="primary" :loading="busy === 'view'" @click="exportFile('pdf', true)">Xem &amp; In PDF</el-button>
        </template>
        <template v-else>
          <el-button :loading="busy === 'tree-pdf'" @click="exportFile('tree-pdf')">Tải PDF sơ đồ</el-button>
          <el-button type="primary" :loading="busy === 'view'" @click="exportFile('tree-pdf', true)">Xem &amp; In sơ đồ</el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, inject, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import api from '../api'

const props = defineProps({
  modelValue: Boolean,
  /** Nguoi goc dang chon tren cay (null = ca dong ho) */
  rootId: { type: Number, default: null },
  /** So doi dang hien thi tren cay */
  maxDepth: { type: Number, default: 50 }
})
const emit = defineEmits(['update:modelValue'])
const gp = inject('gp')

/** Khop voi enum ExportField o backend */
const FIELDS = [
  { key: 'FULL_NAME', label: 'Họ và tên', hint: 'luôn có' },
  { key: 'GENERATION', label: 'Đời thứ' },
  { key: 'GENDER', label: 'Giới tính' },
  { key: 'BIRTH', label: 'Ngày sinh / Năm sinh' },
  { key: 'DEATH', label: 'Ngày mất / Ngày giỗ ÂL' },
  { key: 'ROLE', label: 'Vai trò', hint: 'con trai, con dâu, tộc trưởng…' },
  { key: 'PARENTS', label: 'Cha / Mẹ' },
  { key: 'SPOUSES', label: 'Vợ / Chồng' },
  { key: 'HOMETOWN', label: 'Quê quán / Nghề nghiệp' },
  { key: 'CONTACT', label: 'Số điện thoại & Địa chỉ' },
  { key: 'NOTE', label: 'Tiểu sử / Ghi chú' }
]
const DEFAULTS = {
  format: 'LIST',
  fields: ['FULL_NAME', 'GENERATION', 'GENDER', 'BIRTH', 'DEATH', 'ROLE'],
  sort: 'BRANCH',
  paper: 'A4',
  landscape: false,
  publicView: false,
  showDates: true
}
const STORAGE_KEY = 'gp.export.options'

/** Nho lua chon lan truoc tren trinh duyet nay (tien loi, khong bat buoc) */
function loadOptions() {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') }
  } catch {
    return { ...DEFAULTS }
  }
}
const opt = reactive(loadOptions())
watch(opt, (v) => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(v))
  } catch {
    /* che do rieng tu / bi chan: bo qua */
  }
}, { deep: true })

const busy = ref(null)

/** Danh sach: A4/A3; so do (vector): toi A0 */
const papers = computed(() => (opt.format === 'TREE' ? ['A4', 'A3', 'A2', 'A1', 'A0'] : ['A4', 'A3']))
watch(() => opt.format, (f) => {
  if (f === 'LIST' && !['A4', 'A3'].includes(opt.paper)) opt.paper = 'A3'
  if (f === 'TREE') opt.landscape = true
})
const scopeLabel = computed(() => {
  const m = props.rootId ? gp.byId.value.get(props.rootId) : null
  return m ? `nhánh của ${m.fullName}` : 'toàn bộ dòng họ'
})

function selectAll(on) {
  opt.fields = on ? FIELDS.map((f) => f.key) : ['FULL_NAME']
}

/** Ten file giong backend: GiaPha_<Ho khong dau>_<yyyyMMdd>.<ext> */
function fileName(ext) {
  const surname = (gp.family.value.surname || 'dongho').normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd').replace(/Đ/g, 'D').replace(/[^A-Za-z0-9]+/g, '')
  return `${ext === 'tree' ? 'SoDo' : 'GiaPha'}_${surname || 'dongho'}_${dayjs().format('YYYYMMDD')}.${ext === 'tree' ? 'pdf' : ext}`
}

/**
 * Goi API xuat file (kem token) roi:
 *  - inline: mo PDF o tab moi de xem / in bang trinh xem PDF cua trinh duyet
 *  - con lai: tai file ve may
 */
async function exportFile(kind, inline = false) {
  // Mo tab TRUOC khi cho mang (neu mo sau await, trinh duyet coi la popup va chan)
  const tab = inline ? window.open('', '_blank') : null
  busy.value = inline ? 'view' : kind
  try {
    const params = kind === 'tree-pdf'
      ? { paper: opt.paper, landscape: opt.landscape, publicView: opt.publicView, showDates: opt.showDates,
          rootId: props.rootId || undefined, maxDepth: props.maxDepth, inline }
      : { fields: opt.fields.join(','), sort: opt.sort, paper: opt.paper, landscape: opt.landscape, publicView: opt.publicView, inline }
    const blob = await api.get(`/families/${gp.familyId.value}/export/${kind}`, {
      params,
      responseType: 'blob',
      timeout: 120000
    })
    const url = URL.createObjectURL(blob)
    if (tab) {
      tab.location.href = url
    } else {
      const a = document.createElement('a')
      a.href = url
      a.download = fileName(kind === 'excel' ? 'xlsx' : kind === 'tree-pdf' ? 'tree' : 'pdf')
      document.body.appendChild(a)
      a.click()
      a.remove()
      ElMessage.success('Đã tạo file ' + a.download)
    }
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    emit('update:modelValue', false)
  } catch {
    tab?.close()
  } finally {
    busy.value = null
  }
}
</script>

<style scoped>
.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 16px;
  width: 100%;
}
.field-grid :deep(.el-checkbox) {
  margin-right: 0;
  white-space: normal;
  height: auto;
  min-height: 28px;
}
.hint {
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.actions .el-button + .el-button {
  margin-left: 0;
}
@media (max-width: 640px) {
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
