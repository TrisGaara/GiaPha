<template>
  <div>
    <div class="toolbar">
      <el-input v-model="q" placeholder="Tìm theo tên, quê quán, nghề nghiệp..." clearable style="width: 280px" :prefix-icon="Search" />
      <el-select v-model="gen" placeholder="Đời" clearable style="width: 120px">
        <el-option v-for="g in generations" :key="g" :value="g" :label="`Đời ${g}`" />
      </el-select>
      <el-select v-model="status" style="width: 140px">
        <el-option value="all" label="Tất cả" />
        <el-option value="alive" label="Còn sống" />
        <el-option value="dead" label="Đã mất" />
      </el-select>
      <el-radio-group v-model="view">
        <el-radio-button value="list">Danh sách</el-radio-button>
        <el-radio-button value="gen">Bảng thế hệ</el-radio-button>
      </el-radio-group>
      <span class="muted">{{ filtered.length }} người</span>
      <el-button v-if="gp.canManage.value" type="primary" style="margin-left: auto" @click="gp.newMember({})">Thêm người</el-button>
    </div>

    <el-table v-if="view === 'list'" :data="filtered" stripe highlight-current-row @row-click="(r) => gp.openMember(r.id)" style="cursor: pointer">
      <el-table-column label="Họ tên" min-width="200">
        <template #default="{ row }">
          <b>{{ row.fullName }}</b>
          <span v-if="row.otherName" class="muted"> ({{ row.otherName }})</span>
          <el-tag v-if="!row.bloodLine" size="small" type="warning" style="margin-left: 6px">{{ row.gender === 'MALE' ? 'Rể' : 'Dâu' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="generation" label="Đời" width="70" sortable />
      <el-table-column label="Thứ bậc" min-width="150">
        <template #default="{ row }"><span class="muted">{{ row.birthOrderLabel }}</span></template>
      </el-table-column>
      <el-table-column label="Giới tính" width="90">
        <template #default="{ row }">{{ row.gender === 'MALE' ? 'Nam' : 'Nữ' }}</template>
      </el-table-column>
      <el-table-column label="Năm sinh – mất" width="130">
        <template #default="{ row }">{{ lifeSpan(row) }}</template>
      </el-table-column>
      <el-table-column label="Ngày giỗ (ÂL)" width="120">
        <template #default="{ row }">
          <span v-if="!row.alive && row.deathLunar" style="color: #9b2c2c">{{ row.deathLunar.day }}/{{ row.deathLunar.month }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Cha / Mẹ" min-width="180">
        <template #default="{ row }">{{ parentName(row) }}</template>
      </el-table-column>
      <el-table-column prop="hometown" label="Quê quán" min-width="140" />
      <el-table-column prop="occupation" label="Nghề nghiệp" min-width="120" />
    </el-table>

    <el-collapse v-else v-model="openGens">
      <el-collapse-item v-for="g in grouped" :key="g.gen" :name="g.gen">
        <template #title>
          <b style="color: #9b2c2c">{{ g.gen === 0 ? 'Chưa xác định đời' : `Đời thứ ${g.gen}` }}</b>
          <span class="muted" style="margin-left: 8px">{{ g.items.length }} người</span>
        </template>
        <div class="gen-grid">
          <div v-for="m in g.items" :key="m.id" class="gen-item" @click="gp.openMember(m.id)">
            <PersonCard :m="m" />
            <div class="muted parent">{{ parentName(m) }}</div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { lifeSpan } from '../constants'
import PersonCard from './PersonCard.vue'

const gp = inject('gp')
const q = ref('')
const gen = ref(null)
const status = ref('all')
const view = ref('list')
const openGens = ref([1, 2, 3])

const members = computed(() => gp.tree.value.members)
const generations = computed(() => [...new Set(members.value.map((m) => m.generation).filter(Boolean))].sort((a, b) => a - b))

const normalize = (s) => (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()

const filtered = computed(() => {
  const k = normalize(q.value)
  return members.value.filter((m) => {
    if (m.placeholder) return false // nut an danh chi de giu cau truc cay
    if (gen.value && m.generation !== gen.value) return false
    if (status.value === 'alive' && !m.alive) return false
    if (status.value === 'dead' && m.alive) return false
    if (!k) return true
    return [m.fullName, m.otherName, m.hometown, m.occupation].some((x) => normalize(x).includes(k))
  })
})

const grouped = computed(() => {
  const map = new Map()
  for (const m of filtered.value) {
    const g = m.generation || 0
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(m)
  }
  return [...map.entries()].sort((a, b) => (a[0] || 999) - (b[0] || 999)).map(([g, items]) => ({ gen: g, items }))
})

function parentName(m) {
  const f = gp.byId.value.get(m.fatherId)
  const mo = gp.byId.value.get(m.motherId)
  if (f && mo) return `${f.fullName} & ${mo.fullName}`
  return f?.fullName || mo?.fullName || ''
}
</script>

<style scoped>
.gen-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 8px 4px;
}
.gen-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 130px;
}
.parent {
  font-size: 11px;
  text-align: center;
}
</style>
