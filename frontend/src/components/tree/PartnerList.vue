<template>
  <!--
    Danh sach thu gon tat ca cac doi vo/chong (khi bam ⋯ trong CoupleCard).
    Dung div/button thay vi ul/li: CSS ".gp-tree li" cua cay se ve duong noi len moi <li> ben trong cay.
  -->
  <div class="partner-list" role="list">
    <button v-for="(p, i) in partners" :key="p.marriage.id" type="button" role="listitem" class="row"
      :class="[p.marriage.status, { active: i === selected }]" :title="`Mở hồ sơ ${p.person.fullName}`"
      @click.stop="emit('select', p.person.id)">
      <span class="order">{{ circled(p.marriage.marriageOrder) }}</span>
      <span class="name">{{ p.person.fullName }}</span>
      <span class="years">{{ lifeSpan(p.person) }}</span>
    </button>
  </div>
</template>

<script setup>
import { circled } from './treeModel'
import { lifeSpan } from '../../constants'

defineProps({ partners: { type: Array, required: true }, selected: { type: Number, default: 0 } })
const emit = defineEmits(['select'])
</script>

<style scoped>
.partner-list {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1px;
  width: 160px; /* co dinh: A-/A+ chi doi co chu */
  overflow-wrap: anywhere;
  padding: 6px 4px;
  box-sizing: border-box;
}
.row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  width: 100%;
  padding: 3px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font: inherit;
  font-size: calc(12px * var(--tree-fs, 1));
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.row:hover {
  background: #fdf3e1;
}
.row.active .name {
  font-weight: 600;
}
.row.DIVORCED,
.row.DECEASED {
  color: #9ca3af;
}
.order {
  color: var(--gp-female);
}
.name {
  flex: 1;
}
.years {
  font-size: 10px;
  color: #8a8178;
  white-space: nowrap;
}
</style>
