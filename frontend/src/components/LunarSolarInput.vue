<template>
  <div class="ls-input">
    <el-radio-group :model-value="mode" size="small" @update:model-value="emit('update:mode', $event)">
      <el-radio-button value="solar">Dương lịch</el-radio-button>
      <el-radio-button value="lunar">Âm lịch</el-radio-button>
    </el-radio-group>

    <div v-if="mode === 'solar'" class="row">
      <el-date-picker :model-value="solar" type="date" value-format="YYYY-MM-DD" format="DD/MM/YYYY"
        placeholder="Chọn ngày" style="width: 180px" @update:model-value="emit('update:solar', $event)" />
      <span v-if="preview" class="muted">→ Âm lịch: {{ preview }}</span>
    </div>

    <div v-else class="row">
      <el-input-number :model-value="day" :min="1" :max="30" placeholder="Ngày" controls-position="right" style="width: 100px"
        @update:model-value="emit('update:day', $event)" />
      <span>/</span>
      <el-input-number :model-value="month" :min="1" :max="12" placeholder="Tháng" controls-position="right" style="width: 100px"
        @update:model-value="emit('update:month', $event)" />
      <span>/</span>
      <el-input-number :model-value="year" :min="1" :max="2200" :placeholder="allowNoYear ? 'Năm (nếu biết)' : 'Năm'"
        controls-position="right" style="width: 140px" @update:model-value="emit('update:year', $event)" />
      <el-checkbox :model-value="leap" @update:model-value="emit('update:leap', $event)">Tháng nhuận</el-checkbox>
      <span v-if="preview" class="muted">→ Dương lịch: {{ preview }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { fmtDate } from '../constants'

// Dung fetch truc tiep (khong qua axios interceptor) de khong bung thong bao loi khi nguoi dung dang go do

const props = defineProps({
  mode: String,
  solar: String,
  day: Number,
  month: Number,
  year: Number,
  leap: Boolean,
  allowNoYear: Boolean
})
const emit = defineEmits(['update:mode', 'update:solar', 'update:day', 'update:month', 'update:year', 'update:leap'])
const preview = ref('')

let seq = 0
watch(
  () => [props.mode, props.solar, props.day, props.month, props.year, props.leap],
  async () => {
    const my = ++seq
    preview.value = ''
    try {
      let res
      if (props.mode === 'solar' && props.solar) {
        res = await fetch(`/api/calendar/to-lunar?date=${props.solar}`).then((r) => r.json())
        if (my === seq) preview.value = res.lunar.label
      } else if (props.mode === 'lunar' && props.day && props.month && props.year) {
        const q = new URLSearchParams({ day: props.day, month: props.month, year: props.year, leap: !!props.leap })
        const r = await fetch(`/api/calendar/to-solar?${q}`)
        if (my === seq) preview.value = r.ok ? fmtDate((await r.json()).solar) : 'ngày âm không hợp lệ'
      }
    } catch {
      /* bo qua */
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.ls-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
