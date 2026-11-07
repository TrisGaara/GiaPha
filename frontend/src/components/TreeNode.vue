<template>
  <!--
    Mot nut cay = mot cap vo chong (CoupleCard) + cac con.
    Duong noi KHONG ve bang CSS o day nua: FamilyTree ve tat ca bang mot lop SVG phia duoi,
    dua vao data-node-id / data-parent-id tren moi CoupleCard (xem FamilyTree.recomputeLinks).
  -->
  <li>
    <!-- Cham / nhap / sua nhanh duoc CoupleCard xu ly qua treeCtx (tap, edit) -->
    <CoupleCard :node="node" :parent-id="parentId" />
    <button v-if="node.children.length" class="toggle" :title="showChildren ? 'Thu gọn' : 'Mở rộng'" @click.stop="onToggle">
      {{ showChildren ? '−' : `+${node.descendantCount}` }}
    </button>
    <ul v-if="node.children.length && showChildren">
      <TreeNode v-for="c in node.children" :key="c.id" :node="c" :parent-id="node.id" :depth="depth + 1" />
    </ul>
  </li>
</template>

<script setup>
import { computed, inject } from 'vue'
import CoupleCard from './tree/CoupleCard.vue'

const props = defineProps({
  /** CoupleNode tu treeModel.buildForest */
  node: { type: Object, required: true },
  /** id nut cha me (null voi nut goc) - dung de ve duong noi */
  parentId: { type: Number, default: null },
  depth: { type: Number, default: 1 }
})
const ctx = inject('treeCtx')

const showChildren = computed(() => {
  if (ctx.collapsed.has(props.node.id)) return false
  if (ctx.collapsed.has(-props.node.id)) return true // da mo rong thu cong vuot gioi han so doi
  return props.depth < ctx.maxDepth.value
})

function onToggle() {
  const id = props.node.id
  if (showChildren.value) {
    ctx.collapsed.delete(-id)
    ctx.collapsed.add(id)
  } else {
    ctx.collapsed.delete(id)
    if (props.depth >= ctx.maxDepth.value) ctx.collapsed.add(-id)
  }
}
</script>

<style scoped>
/* Nut thu gon nam de len diem dau duong noi xuong con (nen trang che duong SVG) */
.toggle {
  position: relative;
  z-index: 2;
  margin-top: 6px;
  border: 1px solid var(--gp-line);
  background: #fff;
  color: #7c5a2a;
  border-radius: 10px;
  font-size: 11px;
  padding: 0 8px;
  line-height: 16px;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
}
.toggle:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
</style>
