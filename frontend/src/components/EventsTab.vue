<template>
  <el-row :gutter="20">
    <el-col :md="9" :xs="24">
      <el-card shadow="never">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <b>Sắp diễn ra</b>
            <el-select v-model="days" size="small" style="width: 120px" @change="loadUpcoming">
              <el-option :value="30" label="30 ngày tới" />
              <el-option :value="90" label="3 tháng tới" />
              <el-option :value="365" label="12 tháng tới" />
            </el-select>
          </div>
        </template>
        <el-empty v-if="!upcoming.length" description="Không có sự kiện" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item v-for="(o, i) in upcoming" :key="i" :timestamp="`${fmtDate(o.date)} · ÂL ${o.lunarLabel}`"
            :type="o.daysUntil <= 3 ? 'danger' : o.daysUntil <= 7 ? 'warning' : 'primary'" placement="top">
            <div class="occ-title">{{ o.title }}</div>
            <div class="muted">
              {{ EVENT_TYPES[o.type] }} ·
              <b>{{ o.daysUntil === 0 ? 'Hôm nay' : `còn ${o.daysUntil} ngày` }}</b>
              <span v-if="o.location"> · {{ o.location }}</span>
              <span v-if="o.auto"> · tự động từ hồ sơ</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </el-col>

    <el-col :md="15" :xs="24">
      <div class="toolbar">
        <b>Sự kiện của dòng họ</b>
        <span class="muted">Ngày giỗ của người đã mất được tự động lấy từ hồ sơ, không cần tạo thủ công.</span>
        <el-button v-if="gp.canManage.value" type="primary" style="margin-left: auto" @click="openForm()">Thêm sự kiện</el-button>
      </div>
      <el-table :data="events" stripe>
        <el-table-column prop="title" label="Sự kiện" min-width="180" />
        <el-table-column label="Loại" width="120">
          <template #default="{ row }">{{ EVENT_TYPES[row.type] }}</template>
        </el-table-column>
        <el-table-column label="Ngày" width="150">
          <template #default="{ row }">
            <template v-if="row.calendarType === 'LUNAR'">
              {{ row.lunarDay }}/{{ row.lunarMonth }}{{ row.lunarYear && !row.recurring ? '/' + row.lunarYear : '' }} ÂL
            </template>
            <template v-else>{{ row.recurring ? fmtDate(row.solarDate).slice(0, 5) : fmtDate(row.solarDate) }} DL</template>
            <div class="muted" v-if="row.recurring">hằng năm</div>
          </template>
        </el-table-column>
        <el-table-column label="Lần tới" width="110">
          <template #default="{ row }">{{ fmtDate(row.nextDate) || '—' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="Địa điểm" min-width="120" />
        <el-table-column v-if="gp.canManage.value" width="110" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openForm(row)">Sửa</el-button>
            <el-button link type="danger" @click="remove(row)">Xóa</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-col>
  </el-row>

  <el-dialog v-model="formVisible" :title="form.id ? 'Sửa sự kiện' : 'Thêm sự kiện'" width="560px">
    <el-form ref="formRef" :model="form" label-position="top">
      <el-form-item label="Tên sự kiện" prop="title" :rules="[{ required: true, message: 'Nhập tên sự kiện' }]">
        <el-input v-model="form.title" placeholder="VD: Giỗ Tổ dòng họ, Họp họ đầu xuân..." />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12">
          <el-form-item label="Loại">
            <el-select v-model="form.type" style="width: 100%">
              <el-option v-for="(label, k) in EVENT_TYPES" :key="k" :value="k" :label="label" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :span="12">
          <el-form-item label="Lặp lại">
            <el-switch v-model="form.recurring" active-text="Hằng năm" inactive-text="Một lần" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Theo lịch">
        <el-radio-group v-model="form.calendarType">
          <el-radio-button value="LUNAR">Âm lịch</el-radio-button>
          <el-radio-button value="SOLAR">Dương lịch</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.calendarType === 'SOLAR'" label="Ngày dương lịch">
        <el-date-picker v-model="form.solarDate" type="date" value-format="YYYY-MM-DD" format="DD/MM/YYYY" />
      </el-form-item>
      <el-form-item v-else label="Ngày / tháng âm lịch">
        <div style="display: flex; gap: 8px; align-items: center">
          <el-input-number v-model="form.lunarDay" :min="1" :max="30" controls-position="right" style="width: 100px" />
          /
          <el-input-number v-model="form.lunarMonth" :min="1" :max="12" controls-position="right" style="width: 100px" />
          <template v-if="!form.recurring">
            /
            <el-input-number v-model="form.lunarYear" :min="1900" :max="2200" controls-position="right" style="width: 120px" />
          </template>
        </div>
      </el-form-item>
      <el-form-item label="Gắn với thành viên (tùy chọn)">
        <el-select v-model="form.memberId" filterable clearable style="width: 100%">
          <el-option v-for="m in gp.tree.value.members" :key="m.id" :value="m.id" :label="m.fullName" />
        </el-select>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :xs="24" :span="14">
          <el-form-item label="Địa điểm"><el-input v-model="form.location" /></el-form-item>
        </el-col>
        <el-col :xs="24" :span="10">
          <el-form-item label="Nhắc trước (ngày)"><el-input v-model="form.remindDaysBefore" placeholder="VD: 7,3,1" /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Mô tả"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">Hủy</el-button>
      <el-button type="primary" :loading="saving" @click="save">Lưu</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { EVENT_TYPES, fmtDate } from '../constants'

const gp = inject('gp')
const events = ref([])
const upcoming = ref([])
const days = ref(90)
const formVisible = ref(false)
const formRef = ref()
const saving = ref(false)
const form = ref({})

const base = () => `/families/${gp.familyId.value}/events`

async function loadUpcoming() {
  upcoming.value = await api.get(`${base()}/upcoming`, { params: { days: days.value } })
}
async function load() {
  events.value = await api.get(base())
  await loadUpcoming()
}

function openForm(row) {
  form.value = row
    ? { ...row }
    : { title: '', type: 'ANCESTOR_DAY', calendarType: 'LUNAR', recurring: true, lunarDay: null, lunarMonth: null,
        lunarYear: null, solarDate: null, memberId: null, location: gp.family.value.ancestralHallAddress || '',
        remindDaysBefore: '7,3,1', description: '' }
  formVisible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const f = form.value
    if (f.id) await api.put(`${base()}/${f.id}`, f)
    else await api.post(base(), f)
    ElMessage.success('Đã lưu sự kiện')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`Xóa sự kiện "${row.title}"?`, 'Xác nhận', { type: 'warning' })
  await api.delete(`${base()}/${row.id}`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.occ-title {
  font-weight: 600;
}
</style>
