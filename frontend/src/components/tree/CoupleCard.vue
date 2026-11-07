<template>
  <!--
    Khung the chung cho mot cap vo chong:
      [ nua trai: CHONG ] | ♥ | [ nua phai: VO ]
    - Nhieu doi vo/chong: chip ①②③ ben duoi de chuyen nguoi hien thi, nut ⋯ de xem ca danh sach.
    - Cham / nhap 1 lan: lam noi bat nhanh (giong hover); 2 lan lien tiep: mo ho so (xem FamilyTree.tap).
    - Nut ✎ (khi the dang noi bat): mo thang form sua - chi hien voi nguoi duoc sua ho so do.
    - Hover chi kich hoat voi chuot that (pointerType = mouse), tranh "hover dinh" tren man hinh cam ung.
  -->
  <div class="couple-card" :class="[{ single: !partner, expanded }, hoverState]"
    :data-node-id="node.id" :data-parent-id="parentId ?? ''"
    @pointerenter="(e) => e.pointerType === 'mouse' && ctx?.hoverEnter(node.id)"
    @pointerleave="(e) => e.pointerType === 'mouse' && ctx?.hoverLeave(node.id)">
    <span v-if="node.parentOrder" class="parent-badge" :title="`Con của ${node.parentName}`">{{ circled(node.parentOrder) }}</span>

    <div class="row">
      <!-- Nua trai = CHONG: chinh nguoi huyet thong (nam), hoac chong cua con gai dong ho -->
      <div v-if="primaryIsHusband" class="half primary" @click.stop="onTap(node.primary.id)">
        <PersonCard :m="node.primary" embedded />
        <button v-if="canQuickEdit(node.primary)" class="quick-edit" type="button" title="Sửa hồ sơ"
          @click.stop="ctx.edit(node.primary.id)">✎</button>
      </div>
      <template v-else-if="partner">
        <PartnerList v-if="expanded" :partners="node.partners" :selected="selected" @select="onTap" />
        <div v-else class="half" @click.stop="onTap(partner.id)">
          <PersonCard :m="partner" embedded />
          <button v-if="canQuickEdit(partner)" class="quick-edit" type="button" title="Sửa hồ sơ"
            @click.stop="ctx.edit(partner.id)">✎</button>
        </div>
      </template>

      <!-- Vach ngan giua: trang thai hon nhan (♥ dang chung song, ⨯ ly hon, ♡ goa) -->
      <div v-if="partner" class="divider">
        <span class="status" :class="relation.status" :title="statusTitle">{{ statusIcon }}</span>
      </div>

      <!-- Nua phai = VO: vo cua nguoi huyet thong, hoac chinh con gai dong ho -->
      <div v-if="!primaryIsHusband" class="half primary" @click.stop="onTap(node.primary.id)">
        <PersonCard :m="node.primary" embedded />
        <button v-if="canQuickEdit(node.primary)" class="quick-edit" type="button" title="Sửa hồ sơ"
          @click.stop="ctx.edit(node.primary.id)">✎</button>
      </div>
      <template v-else-if="partner">
        <PartnerList v-if="expanded" :partners="node.partners" :selected="selected" @select="onTap" />
        <div v-else class="half" @click.stop="onTap(partner.id)">
          <PersonCard :m="partner" embedded />
          <button v-if="canQuickEdit(partner)" class="quick-edit" type="button" title="Sửa hồ sơ"
            @click.stop="ctx.edit(partner.id)">✎</button>
        </div>
      </template>
    </div>

    <!-- Chuyen doi giua cac doi vo/chong -->
    <div v-if="node.partners.length > 1" class="partner-tabs" @mousedown.stop>
      <button v-for="(p, i) in node.partners" :key="p.marriage.id" class="chip" :class="{ active: i === selected, [p.marriage.status]: true }"
        :title="`${p.person.fullName} – ${statusLabel(p.marriage.status)}`" @click.stop="selected = i; expanded = false">
        {{ circled(p.marriage.marriageOrder) }}
      </button>
      <button class="chip more" :title="expanded ? 'Thu gọn' : 'Xem tất cả các đời'" @click.stop="expanded = !expanded">
        {{ expanded ? '×' : '⋯' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import PersonCard from '../PersonCard.vue'
import PartnerList from './PartnerList.vue'
import { circled } from './treeModel'
import { MARRIAGE_STATUS } from '../../constants'

const props = defineProps({
  node: { type: Object, required: true },
  /** id nut cha me tren cay (null voi nut goc) - FamilyTree dung de ve duong noi SVG */
  parentId: { type: Number, default: null }
})
const emit = defineEmits(['select'])

// Trang thai hover dung chung cho ca cay (FamilyTree cung cap); null neu the duoc dung ngoai cay
const ctx = inject('treeCtx', null)
/** { active: dang hover, related: cha me / con truc tiep cua nut dang hover, dimmed: ngoai nhanh dang tap trung } */
const hoverState = computed(() => ctx?.cardState(props.node.id) ?? {})

/** Cham/nhap vao mot nguoi: trong cay -> FamilyTree quyet dinh (1 lan = noi bat, 2 lan = mo ho so) */
function onTap(personId) {
  if (ctx) ctx.tap(props.node.id, personId)
  else emit('select', personId)
}
/** Nut Sua nhanh: chi voi nguoi dang xem duoc sua (canEdit tu backend - tu tat o Che do nguoi xem) */
const canQuickEdit = (m) => !!ctx?.edit && !!m?.canEdit && !m.placeholder

const selected = ref(props.node.defaultPartnerIndex)
const expanded = ref(false)

const current = computed(() => props.node.partners[selected.value] || props.node.partners[0] || null)
const partner = computed(() => current.value?.person || null)
const relation = computed(() => current.value?.marriage || {})

// Chong luon ben trai, vo ben phai - bat ke ai la nguoi huyet thong
const primaryIsHusband = computed(() => props.node.side === 'MALE')

const statusIcon = computed(() => ({ ACTIVE: '♥', DIVORCED: '⨯', DECEASED: '♡' })[relation.value.status] || '♥')
const statusLabel = (s) => MARRIAGE_STATUS[s] || s
const statusTitle = computed(() => {
  const order = relation.value.marriageOrder
  return `${statusLabel(relation.value.status)}${props.node.partners.length > 1 ? ` · đời thứ ${order}` : ''}`
})
</script>

<style scoped>
.couple-card {
  --card-border: var(--gp-line);
  --card-bg: #fff;
  --card-shadow: 0 1px 4px rgba(124, 90, 42, 0.12);
  position: relative;
  z-index: 1; /* nam tren lop duong noi SVG */
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
  background: var(--card-bg);
  border: 2px solid var(--card-border);
  border-radius: 10px;
  box-shadow: var(--card-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, background-color 0.2s ease,
    opacity 0.2s ease, filter 0.2s ease;
}

/* ---- Hover: the dang tro chuot (ca cap vo chong) ---- */
.couple-card:hover,
.couple-card.active {
  --card-border: var(--el-color-primary);
  --card-bg: #fffaf2;
  --card-shadow: 0 8px 20px rgba(124, 35, 35, 0.22);
  z-index: 3;
  transform: translateY(-2px) scale(1.02);
}
.couple-card:hover :deep(.name),
.couple-card.active :deep(.name) {
  color: var(--el-color-primary-dark-2);
}
.couple-card.active .status {
  border-color: var(--el-color-primary);
  transform: translate(-50%, -50%) scale(1.2); /* nhan manh duong noi vo chong */
}

/* ---- Cha me / con truc tiep cua nut dang hover ---- */
.couple-card.related {
  --card-border: #ea580c;
  --card-shadow: 0 3px 10px rgba(234, 88, 12, 0.18);
}

/* ---- Ngoai nhanh dang tap trung: lam mo ---- */
.couple-card.dimmed {
  opacity: 0.35;
  filter: grayscale(0.4);
}

/* The mot nguoi (khong co vo/chong): vien nam o .half */
.couple-card.single {
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}
.couple-card.single .half {
  border: 2px solid var(--card-border);
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--card-shadow);
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}
.row {
  display: flex;
  align-items: stretch;
}
.half {
  position: relative;
  cursor: pointer;
  touch-action: manipulation; /* bo tre 300ms va zoom-khi-cham-2-lan cua trinh duyet */
  -webkit-tap-highlight-color: transparent;
  border-radius: 8px;
  transition: background 0.12s;
}
/* Ten trong node: khong gach chan; hover = doi nen + doi mau chu (van click duoc de mo ho so) */
.half :deep(.name) {
  text-decoration: none;
  transition: color 0.12s;
}
.half:hover {
  background: #fdf3e1;
}
.half:hover :deep(.name) {
  color: var(--el-color-primary);
}
/* Nut ✎ Sua nhanh: chi hien khi the dang noi bat (hover chuot / da cham) */
.quick-edit {
  position: absolute;
  right: 2px;
  bottom: 2px;
  z-index: 2;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 50%;
  background: #fff;
  color: var(--el-color-primary);
  font-size: 13px;
  line-height: 22px;
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transform: scale(0.8);
  transition: opacity 0.15s ease, transform 0.15s ease, background-color 0.15s ease;
}
.couple-card:hover .quick-edit,
.couple-card.active .quick-edit {
  opacity: 1;
  pointer-events: auto;
  transform: scale(1);
}
.quick-edit:hover {
  background: var(--el-color-primary);
  color: #fff;
}
@media (hover: none) {
  .quick-edit {
    width: 30px; /* de cham bang ngon tay */
    height: 30px;
    line-height: 28px;
    font-size: 15px;
  }
}
.divider {
  position: relative;
  width: 1px;
  margin: 8px 0;
  background: linear-gradient(to bottom, transparent, var(--gp-line), transparent);
}
.status {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 18px;
  height: 18px;
  line-height: 16px;
  border-radius: 50%;
  border: 1px solid var(--gp-line);
  background: #fff;
  text-align: center;
  font-size: 11px;
  color: #c53030;
  transition: transform 0.2s ease, border-color 0.2s ease;
}
.status.DIVORCED,
.status.DECEASED {
  color: #9ca3af;
}
.partner-tabs {
  display: flex;
  justify-content: center;
  gap: 3px;
  padding: 2px 4px 4px;
  border-top: 1px dashed #eadfcd;
}
.chip {
  border: 1px solid #eadfcd;
  background: #fff;
  border-radius: 9px;
  font-size: calc(11px * var(--tree-fs, 1));
  line-height: 1.35; /* theo co chu (A-/A+) de chu to khong de len vien */
  padding: 0 5px;
  cursor: pointer;
  color: #7c5a2a;
}
.chip.active {
  background: var(--gp-female);
  border-color: var(--gp-female);
  color: #fff;
}
.chip.DIVORCED:not(.active),
.chip.DECEASED:not(.active) {
  color: #9ca3af;
}
.chip.more {
  font-weight: 700;
}
.parent-badge {
  position: absolute;
  top: -9px;
  right: 6px;
  z-index: 1;
  background: #fff;
  color: var(--gp-female);
  border: 1px solid var(--gp-female);
  border-radius: 8px;
  font-size: calc(11px * var(--tree-fs, 1));
  line-height: 1.3;
  padding: 0 3px;
}
</style>
