<template>
  <div>
    <!-- ============ Doi lich: hai dong cung kieu (o chon ngay + ket qua + xem tren lich) ============ -->
    <el-card shadow="never" class="converter">
      <div class="conv-row">
        <span class="conv-label">Đổi Dương → Âm</span>
        <el-date-picker v-model="solarInput" type="date" value-format="YYYY-MM-DD" format="DD/MM/YYYY"
          placeholder="Chọn ngày dương lịch" :clearable="false" style="width: 220px" @change="toLunar" />
        <template v-if="lunarResult">
          <span class="arrow">→</span>
          <span class="result">{{ lunarResult.text }}</span>
          <el-button link type="primary" size="small" @click="showOnCalendar(lunarResult.solar)">Xem trên lịch</el-button>
        </template>
      </div>
      <div class="conv-row">
        <span class="conv-label">Đổi Âm → Dương</span>
        <LunarDatePicker v-model="lunarInput" width="220px" @change="toSolar" />
        <template v-if="solarResult">
          <span class="arrow">→</span>
          <span class="result">{{ solarResult.text }}</span>
          <el-button link type="primary" size="small" @click="showOnCalendar(solarResult.solar)">Xem trên lịch</el-button>
        </template>
      </div>
    </el-card>

    <!-- ============ Lich thang ============ -->
    <el-calendar v-model="current" v-loading="loading" class="month-calendar">
      <template #header>
        <div class="cal-header">
          <div class="cal-nav">
            <el-button :icon="ArrowLeft" title="Tháng trước" @click="shiftMonth(-1)" />
            <el-select v-model="viewMonth" class="month-select" aria-label="Chọn tháng">
              <el-option v-for="m in 12" :key="m" :value="m" :label="`Tháng ${m}`" />
            </el-select>
            <el-input-number v-model="viewYear" :min="1900" :max="2199" controls-position="right" class="year-input" aria-label="Chọn năm" />
            <el-button :icon="ArrowRight" title="Tháng sau" @click="shiftMonth(1)" />
            <el-button @click="goToday">Hôm nay</el-button>
          </div>
          <div v-if="lunarSpan" class="cal-lunar">Âm lịch: {{ lunarSpan }}</div>
        </div>
      </template>
      <template #date-cell="{ data }">
        <div class="cell" :class="{ other: data.type !== 'current-month', today: data.day === todayStr }">
          <div class="cell-top">
            <span class="solar">{{ Number(data.day.slice(8)) }}</span>
            <span class="lunar" :class="{ first: lunarOf(data.day)?.day === 1 }">{{ lunarText(data.day) }}</span>
          </div>
          <div v-for="(o, i) in eventsOf(data.day)" :key="i" class="ev" :class="o.type" :title="o.title">{{ o.title }}</div>
        </div>
      </template>
    </el-calendar>
  </div>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import api from '../api'
import { fmtDate } from '../constants'
import LunarDatePicker from './LunarDatePicker.vue'

const gp = inject('gp')
const WEEKDAYS = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy']
const todayStr = dayjs().format('YYYY-MM-DD')

// =====================================================================
// Lich thang: trang thai thang / nam dang xem
// =====================================================================

const current = ref(new Date())
const lunarMap = ref({})
const occMap = ref({})
const loading = ref(false)

/** Thang (1-12) va nam dang xem - doc / ghi truc tiep vao `current` cua el-calendar */
const viewMonth = computed({
  get: () => dayjs(current.value).month() + 1,
  set: (m) => (current.value = dayjs(current.value).date(1).month(m - 1).toDate())
})
const viewYear = computed({
  get: () => dayjs(current.value).year(),
  set: (y) => {
    if (Number.isInteger(y) && y >= 1900 && y <= 2199) current.value = dayjs(current.value).date(1).year(y).toDate()
  }
})
function shiftMonth(delta) {
  current.value = dayjs(current.value).date(1).add(delta, 'month').toDate()
}
function goToday() {
  current.value = new Date()
}
function showOnCalendar(solar) {
  current.value = dayjs(solar).toDate()
}

const lunarOf = (d) => lunarMap.value[d]
function lunarText(d) {
  const l = lunarMap.value[d]
  if (!l) return ''
  return l.day === 1 ? `${l.day}/${l.month}${l.leap ? 'N' : ''}` : String(l.day)
}
const eventsOf = (d) => occMap.value[d] || []

/** Vd: "tháng 9 – 10 năm Bính Ngọ" hoặc "tháng 12 Ất Tỵ – tháng 1 Bính Ngọ" */
const lunarSpan = computed(() => {
  const base = dayjs(current.value)
  const a = lunarMap.value[base.startOf('month').format('YYYY-MM-DD')]
  const b = lunarMap.value[base.endOf('month').format('YYYY-MM-DD')]
  if (!a || !b) return ''
  const m = (l) => `${l.month}${l.leap ? ' nhuận' : ''}`
  if (a.year === b.year) return a.month === b.month && a.leap === b.leap
    ? `tháng ${m(a)} năm ${a.yearName}`
    : `tháng ${m(a)} – ${m(b)} năm ${a.yearName}`
  return `tháng ${m(a)} ${a.yearName} – tháng ${m(b)} ${b.yearName}`
})

async function load() {
  loading.value = true
  try {
    const start = dayjs(current.value).startOf('month').subtract(7, 'day')
    const end = dayjs(current.value).endOf('month').add(7, 'day')
    const months = [start, dayjs(current.value), end]
    const lunar = {}
    const results = await Promise.all(months.map((m) => api.get('/calendar/month', { params: { year: m.year(), month: m.month() + 1 } })))
    for (const list of results) for (const d of list) lunar[d.solar] = d.lunar
    lunarMap.value = lunar

    const occ = await api.get(`/families/${gp.familyId.value}/events/occurrences`, {
      params: { from: start.format('YYYY-MM-DD'), to: end.format('YYYY-MM-DD') }
    })
    const map = {}
    for (const o of occ) (map[o.date] ||= []).push(o)
    occMap.value = map
  } finally {
    loading.value = false
  }
}
watch(() => dayjs(current.value).format('YYYY-MM'), load, { immediate: true })

// =====================================================================
// Doi lich
// =====================================================================

const solarInput = ref(todayStr)
const lunarResult = ref(null)
const lunarInput = ref(null)
const solarResult = ref(null)

async function toLunar() {
  if (!solarInput.value) return
  const r = await api.get('/calendar/to-lunar', { params: { date: solarInput.value } })
  lunarResult.value = { text: `Ngày ${r.lunar.label}`, solar: r.solar }
}

async function toSolar(v) {
  solarResult.value = null
  if (!v) return
  const r = await api.get('/calendar/to-solar', { params: { day: v.day, month: v.month, year: v.year, leap: v.leap } })
  solarResult.value = { text: `${WEEKDAYS[dayjs(r.solar).day()]}, ${fmtDate(r.solar)}`, solar: r.solar }
}

toLunar()
</script>

<style scoped>
.converter {
  margin-bottom: 12px;
}
.conv-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 6px 0;
}
.conv-label {
  width: 130px;
  font-weight: 600;
}
.arrow {
  color: #b7a88f;
}
.result {
  color: var(--el-color-primary);
  font-weight: 600;
}

/* ---- Tieu de lich thang ---- */
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
  width: 100%;
}
.cal-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.cal-nav .el-button + .el-button {
  margin-left: 0;
}
.month-select {
  width: 120px;
}
.year-input {
  width: 120px;
}
.cal-lunar {
  color: #8a6d3b;
  font-size: 13px;
}
.month-calendar :deep(.el-calendar__header) {
  padding: 12px 16px;
}

/* ---- O ngay ---- */
.cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}
.cell.other {
  opacity: 0.45;
}
.cell.today .solar {
  color: #fff;
  background: var(--el-color-primary);
  border-radius: 50%;
  width: 26px;
  height: 26px;
  line-height: 26px;
  text-align: center;
  font-size: 14px;
}
.cell-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.solar {
  font-size: 16px;
  font-weight: 600;
}
.lunar {
  font-size: 11px;
  color: #8a8178;
}
.lunar.first {
  color: #c53030;
  font-weight: 600;
}
.ev {
  font-size: 11px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 3px;
  background: #fde8e8;
  color: #9b2c2c;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ev.MEETING,
.ev.SPRING_FESTIVAL,
.ev.SCHOLARSHIP,
.ev.LONGEVITY,
.ev.OTHER {
  background: #e6f0fa;
  color: #2b6cb0;
}
:deep(.el-calendar-table .el-calendar-day) {
  height: 92px;
}

@media (max-width: 640px) {
  .conv-label {
    width: 100%;
  }
  :deep(.el-calendar-table .el-calendar-day) {
    height: 64px;
    padding: 4px;
  }
  .ev {
    font-size: 0; /* dien thoai: chi hien cham mau thay cho ten su kien */
    height: 4px;
    padding: 0;
  }
  .solar {
    font-size: 14px;
  }
}
</style>
