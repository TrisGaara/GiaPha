<template>
  <!--
    O chon ngay AM LICH, cung kieu voi el-date-picker (o nhap + icon lich + bang chon bat ra):
      - Bam vao: bang chon Nam (kem can chi) -> Thang (co thang NHUAN dung vi tri neu nam do co) -> Ngay (29 / 30 dung so ngay cua thang)
      - Hoac go truc tiep: 15/8/2026, 1/2n/2023 (n = thang nhuan)
    v-model: { day, month, year, leap } | null
  -->
  <el-popover v-model:visible="open" trigger="click" placement="bottom-start" :width="300" popper-class="lunar-picker-popper"
    @show="onShow">
    <template #reference>
      <el-input :model-value="typing ?? label" :size="size" :placeholder="placeholder" clearable class="lunar-input"
        :style="{ width }" @update:model-value="typing = $event" @change="parseTyped" @clear="clear"
        @keydown.enter.prevent="parseTyped(typing)">
        <template #prefix><el-icon><Calendar /></el-icon></template>
      </el-input>
    </template>

    <div class="lp" v-loading="loading">
      <!-- Nam -->
      <div class="lp-year">
        <button type="button" class="nav" title="Năm trước" @click="setYear(year - 1)">«</button>
        <div class="year-box">
          <el-input-number v-model="yearInput" :min="1900" :max="2199" :controls="false" size="small" class="year-num" @change="setYear" />
          <span class="can-chi">{{ info?.yearName }}</span>
        </div>
        <button type="button" class="nav" title="Năm sau" @click="setYear(year + 1)">»</button>
      </div>

      <!-- Thang: 12 hoac 13 o (thang nhuan dung ngay sau thang thuong cung so) -->
      <div class="lp-months">
        <button v-for="m in info?.months || []" :key="m.month + (m.leap ? 'n' : '')" type="button" class="cell month"
          :class="{ active: pick.month === m.month && pick.leap === m.leap, leap: m.leap }"
          :title="`Mùng 1 = ${fmt(m.startSolar)} · tháng ${m.days === 30 ? 'đủ (30 ngày)' : 'thiếu (29 ngày)'}`"
          @click="selectMonth(m)">
          Th.{{ m.month }}<small v-if="m.leap"> nhuận</small>
        </button>
      </div>

      <!-- Ngay -->
      <div v-if="curMonth" class="lp-days">
        <button v-for="d in curMonth.days" :key="d" type="button" class="cell day"
          :class="{ active: isSelected(d), today: isToday(d), first: d === 1 || d === 15 }"
          :title="solarOf(d)" @click="selectDay(d)">{{ d }}</button>
      </div>
      <div v-else class="muted hint">Chọn tháng âm lịch</div>

      <div class="lp-foot">
        <span class="muted">{{ curMonth ? `Tháng ${curMonth.month}${curMonth.leap ? ' nhuận' : ''} ${curMonth.days === 30 ? 'đủ' : 'thiếu'} · ${info?.yearName}` : '' }}</span>
        <el-button link type="primary" size="small" @click="pickToday">Hôm nay</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import api from '../api'

const props = defineProps({
  modelValue: { type: Object, default: null },
  size: { type: String, default: 'default' },
  width: { type: String, default: '220px' },
  placeholder: { type: String, default: 'Chọn ngày âm lịch' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const loading = ref(false)
const typing = ref(null) // chuoi nguoi dung dang go (null = hien nhan da dinh dang)
const year = ref(props.modelValue?.year || dayjs().year())
const yearInput = ref(year.value)
const pick = reactive({ month: props.modelValue?.month ?? null, leap: !!props.modelValue?.leap })
const todayLunar = ref(null)

// ---- Du lieu nam am lich (cache theo nam) ----
const cache = new Map()
const info = ref(null)
async function loadYear(y) {
  if (!cache.has(y)) {
    loading.value = true
    try {
      cache.set(y, await api.get('/calendar/lunar-year', { params: { year: y } }))
    } finally {
      loading.value = false
    }
  }
  return cache.get(y)
}
watch(year, async (y) => {
  yearInput.value = y
  info.value = await loadYear(y)
  // thang dang chon khong ton tai o nam moi (vd thang nhuan) -> bo chon
  if (pick.month && !info.value.months.some((m) => m.month === pick.month && m.leap === pick.leap)) pick.leap = false
}, { immediate: true })

const curMonth = computed(() => info.value?.months.find((m) => m.month === pick.month && m.leap === pick.leap) || null)

// ---- Hien thi ----
const label = computed(() => {
  const v = props.modelValue
  if (!v) return ''
  const name = cache.get(v.year)?.yearName
  return `${v.day}/${v.month}${v.leap ? ' nhuận' : ''}/${v.year}${name ? ` (${name})` : ''}`
})
const fmt = (d) => dayjs(d).format('DD/MM/YYYY')
const solarOf = (d) => (curMonth.value ? `Dương lịch: ${fmt(dayjs(curMonth.value.startSolar).add(d - 1, 'day'))}` : '')
const isSelected = (d) => {
  const v = props.modelValue
  return v && v.year === year.value && v.month === pick.month && !!v.leap === pick.leap && v.day === d
}
const isToday = (d) => {
  const t = todayLunar.value
  return t && t.year === year.value && t.month === pick.month && t.leap === pick.leap && t.day === d
}

// ---- Thao tac ----
function setYear(y) {
  if (y >= 1900 && y <= 2199) year.value = y
}
function selectMonth(m) {
  pick.month = m.month
  pick.leap = m.leap
}
function commit(v) {
  typing.value = null
  emit('update:modelValue', v)
  emit('change', v)
}
function selectDay(d) {
  commit({ day: d, month: pick.month, year: year.value, leap: pick.leap })
  open.value = false
}
function clear() {
  typing.value = null
  emit('update:modelValue', null)
  emit('change', null)
}
async function pickToday() {
  const t = await ensureToday()
  year.value = t.year
  pick.month = t.month
  pick.leap = t.leap
  await loadYear(t.year)
  selectDay(t.day)
}
async function ensureToday() {
  if (!todayLunar.value) {
    const r = await api.get('/calendar/to-lunar', { params: { date: dayjs().format('YYYY-MM-DD') } })
    todayLunar.value = r.lunar
  }
  return todayLunar.value
}
async function onShow() {
  ensureToday()
  const v = props.modelValue
  if (v) {
    year.value = v.year
    pick.month = v.month
    pick.leap = !!v.leap
  } else if (!pick.month) {
    const t = await ensureToday()
    year.value = t.year
    pick.month = t.month
    pick.leap = t.leap
  }
}

/** Go tay: "15/8/2026", "15-8-2026", "1/2n/2023", "1/2 nhuận/2023" */
async function parseTyped(text) {
  if (text == null) return
  const s = String(text).trim()
  if (!s) return clear()
  // Go sai: bao loi va tra o nhap ve ngay hop le gan nhat (khong de chu sai canh ket qua cu gay hieu nham)
  const invalid = (msg) => {
    typing.value = null
    ElMessage.warning(msg)
  }
  const m = s.match(/^(\d{1,2})\s*[/.-]\s*(\d{1,2})\s*(n|nhuận|nhuan)?\s*[/.-]\s*(\d{4})$/i)
  if (!m) return invalid('Nhập theo dạng ngày/tháng/năm, ví dụ 15/8/2026 hoặc 1/2n/2023 (tháng nhuận)')
  const [day, month, y] = [Number(m[1]), Number(m[2]), Number(m[4])]
  const leap = !!m[3]
  if (y < 1900 || y > 2199) return invalid('Năm âm lịch phải trong khoảng 1900 – 2199')
  const yi = await loadYear(y)
  const mi = yi.months.find((x) => x.month === month && x.leap === leap)
  if (!mi) return invalid(leap ? `Năm ${y} (${yi.yearName}) không có tháng ${month} nhuận` : 'Tháng không hợp lệ')
  if (day < 1 || day > mi.days) return invalid(`Tháng ${month}${leap ? ' nhuận' : ''} năm ${y} chỉ có ${mi.days} ngày`)
  year.value = y
  pick.month = month
  pick.leap = leap
  commit({ day, month, year: y, leap })
}

// Gia tri doi tu ben ngoai -> nap nam de hien ten can chi
watch(() => props.modelValue?.year, (y) => y && loadYear(y), { immediate: true })
</script>

<style scoped>
.lunar-input :deep(.el-input__inner) {
  cursor: pointer;
}
.lp {
  user-select: none;
}
.lp-year {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.nav {
  border: 0;
  background: transparent;
  font-size: 16px;
  color: #6b625a;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 6px;
}
.nav:hover {
  background: #fdf3e1;
  color: var(--el-color-primary);
}
.year-box {
  display: flex;
  align-items: center;
  gap: 6px;
}
.year-num {
  width: 72px;
}
.can-chi {
  font-weight: 600;
  color: var(--el-color-primary);
}
.lp-months {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0e6d6;
}
.lp-days {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 3px;
  margin-top: 8px;
}
.cell {
  border: 1px solid transparent;
  background: transparent;
  border-radius: 6px;
  padding: 5px 0;
  font: inherit;
  font-size: 13px;
  color: #2d2a26;
  cursor: pointer;
  touch-action: manipulation;
}
.cell:hover {
  background: #fdf3e1;
}
.cell.month small {
  font-size: 10px;
  color: var(--gp-female);
}
.cell.month.leap {
  border-style: dashed;
  border-color: #e9c6d8;
}
.cell.day.first {
  color: #c53030;
  font-weight: 600;
}
.cell.today {
  border-color: var(--el-color-primary);
}
.cell.active,
.cell.active small {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
.lp-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
}
.hint {
  text-align: center;
  padding: 12px 0;
}
</style>
