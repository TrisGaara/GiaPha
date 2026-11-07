<template>
  <div>
    <div class="toolbar">
      <el-select v-model="rootId" filterable clearable placeholder="Xem toàn bộ phả hệ" class="root-select">
        <el-option v-for="m in bloodline" :key="m.id" :value="m.id" :label="`${m.fullName}${m.generation ? ' – đời ' + m.generation : ''}`" />
      </el-select>
      <span class="depth">
        <el-input-number v-model="maxDepth" :min="1" :max="50" controls-position="right" style="width: 110px" />
        <span class="muted">đời hiển thị</span>
      </span>
      <el-button-group class="zoom-group">
        <el-button title="Thu nhỏ" @click="zoomOut">−</el-button>
        <el-button title="Về 100%" @click="setZoom(1)">{{ Math.round(zoom * 100) }}%</el-button>
        <el-button title="Phóng to" @click="zoomIn">+</el-button>
      </el-button-group>
      <!-- Co chu tren cac o: A- / A+ (nho lai tren trinh duyet nay) -->
      <el-button-group class="font-group">
        <el-button title="Giảm cỡ chữ" :disabled="fontIndex === 0" @click="changeFont(-1)">A−</el-button>
        <el-button title="Cỡ chữ mặc định" @click="changeFont(0)">{{ Math.round(fontScale * 100) }}%</el-button>
        <el-button title="Tăng cỡ chữ" :disabled="fontIndex === FONT_STEPS.length - 1" @click="changeFont(1)">A+</el-button>
      </el-button-group>
      <el-button @click="expandAll">Mở rộng tất cả</el-button>
      <el-switch v-model="focusMode" :active-text="isTouch ? 'Làm mờ nhánh khác khi chạm' : 'Làm mờ nhánh khác khi rê chuột'" />
      <!-- Che do nguoi xem: xem cay y het tai khoan Khach (an nut sua, che thong tin rieng tu) -->
      <el-tooltip v-if="gp.canPreview.value" content="Xem thử phả hệ dưới góc nhìn tài khoản Khách trước khi công khai" placement="bottom">
        <el-switch v-model="viewerMode" class="viewer-switch" active-text="Chế độ người xem" style="--el-switch-on-color: #e6a23c" />
      </el-tooltip>
      <!-- An khi dang o che do nguoi xem (canManage tinh tu quyen hieu luc) -->
      <el-button v-if="gp.canManage.value" type="primary" @click="gp.newMember({})">Thêm người</el-button>
      <!-- Xuat bao cao / In: Ban quan tri (an o Che do nguoi xem) -->
      <el-button v-if="gp.isAdmin.value" :icon="Printer" @click="exportOpen = true">Xuất / In</el-button>
      <span class="legend">
        <i class="dot male" /> Nam <i class="dot female" /> Nữ <i class="dot dead" /> Đã mất <i class="dot outsider" /> Dâu/Rể
        <span class="legend-sep">♥ vợ chồng · ⨯ ly hôn · ♡ góa · ①② đời vợ/chồng</span>
      </span>
    </div>

    <el-empty v-if="!roots.length" description="Phả hệ chưa có ai. Hãy thêm Thủy tổ (đời 1) để bắt đầu." />

    <!-- tree-stage: khung dinh vi cho cum nut zoom noi (khong cuon theo cay) -->
    <div v-else class="tree-stage">
    <div ref="viewport" class="tree-viewport" :style="{ height: viewportHeight ? `${viewportHeight}px` : null }"
      @mousedown="startPan" @click="onBackgroundClick" @wheel.ctrl.prevent="onWheel"
      @touchstart="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd" @touchcancel="onTouchEnd">
      <!--
        CSS zoom: min-width: 100% (trong CSS .gp-tree) KHONG bi nhan zoom, nen khoi cay luon rong bang khung
        va cay tu can giua khi thu nho - khong can bu tru theo zoom.
      -->
      <div ref="treeEl" class="gp-tree" :style="{ zoom, '--tree-fs': fontScale }">
        <!-- Lop duong noi cha me -> con: moi canh la mot <path> rieng de to sang dung tuyen -->
        <svg class="tree-links" :width="svgSize.w" :height="svgSize.h" aria-hidden="true">
          <path v-for="e in sortedEdges" :key="e.key" :d="e.d" :class="edgeClass(e)" :data-edge="e.key" />
        </svg>
        <ul ref="rootList" class="tree-root">
          <TreeNode v-for="r in roots" :key="r.id" :node="r" :depth="1" />
        </ul>
      </div>
    </div>

    <!-- Cum nut zoom noi: luon trong tam tay, nhat la tren dien thoai (khong co Ctrl + lan chuot) -->
    <div class="zoom-fab" @mousedown.stop @click.stop>
      <button type="button" title="Phóng to" aria-label="Phóng to" :disabled="zoom >= ZOOM_MAX" @click="zoomIn">+</button>
      <button type="button" title="Thu nhỏ" aria-label="Thu nhỏ" :disabled="zoom <= ZOOM_MIN" @click="zoomOut">−</button>
      <button type="button" class="pct" title="Về 100%" aria-label="Về 100%" @click="setZoom(1)">{{ Math.round(zoom * 100) }}%</button>
      <button type="button" title="Vừa màn hình" aria-label="Vừa màn hình" @click="fitToScreen">⤢</button>
    </div>
    </div>
    <!-- So do PDF ve o may chu theo dung goc + so doi dang xem -->
    <ExportDialog v-model="exportOpen" :root-id="rootId" :max-depth="maxDepth" />
    <div class="muted tree-hint">
      <template v-if="isTouch">Vuốt để di chuyển, chụm 2 ngón hoặc nút +/− để phóng to. Chạm 1 lần để làm nổi bật nhánh, chạm 2 lần để mở hồ sơ.</template>
      <template v-else>Kéo chuột để di chuyển, Ctrl + lăn chuột hoặc nút +/− để thu phóng. Nhấp 1 lần để làm nổi bật nhánh, nhấp đúp để mở hồ sơ.</template>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, nextTick, onBeforeUnmount, provide, reactive, ref, shallowRef, watch } from 'vue'
import TreeNode from './TreeNode.vue'
import ExportDialog from './ExportDialog.vue'
import { Printer } from '@element-plus/icons-vue'
import { buildForest } from './tree/treeModel'
import { isTouchDevice } from '../composables/useBreakpoint'

const gp = inject('gp')
const rootId = ref(null)
const maxDepth = ref(6)
const zoom = ref(1)
const focusMode = ref(true)
const exportOpen = ref(false)

// =====================================================================
// Co chu tren so do: bien CSS --tree-fs CHI nhan vao co chu (PersonCard, CoupleCard, PartnerList);
// be rong o giu nguyen (khac voi zoom) - chu to thi xuong dong, o cao them; duong noi tu ve lai.
// =====================================================================
const FONT_STEPS = [0.85, 1, 1.15, 1.3, 1.5]
const FONT_KEY = 'gp.tree.fontScale'
function loadFontIndex() {
  try {
    const i = FONT_STEPS.indexOf(Number(localStorage.getItem(FONT_KEY)))
    return i >= 0 ? i : 1
  } catch {
    return 1
  }
}
const fontIndex = ref(loadFontIndex())
const fontScale = computed(() => FONT_STEPS[fontIndex.value])
/** delta: -1 giam, +1 tang, 0 ve mac dinh */
function changeFont(delta) {
  fontIndex.value = delta === 0 ? 1 : Math.min(FONT_STEPS.length - 1, Math.max(0, fontIndex.value + delta))
  try {
    localStorage.setItem(FONT_KEY, String(fontScale.value))
  } catch {
    /* bo qua */
  }
}
/** Proxy hai chieu toi trang thai isViewerMode cua FamilyView (luu tren URL ?as=viewer) */
const viewerMode = computed({
  get: () => gp.isViewerMode.value,
  set: (on) => (gp.isViewerMode.value = on)
})
const viewport = ref()
const treeEl = ref()
const rootList = ref()
const collapsed = reactive(new Set())

const members = computed(() => gp.tree.value.members)
const bloodline = computed(() => members.value.filter((m) => m.bloodLine && !m.placeholder))

/** Du lieu phang tu API -> cay cac nut "cap vo chong" (xem tree/treeModel.js) */
const roots = computed(() => buildForest(members.value, gp.tree.value.maritalRelations, { rootId: rootId.value }))

function expandAll() {
  collapsed.clear()
  maxDepth.value = 50
}

// =====================================================================
// Trang thai hover & nhanh dang tap trung
// =====================================================================

/** Chi muc tren cay: id nut -> id nut cha me, id nut -> danh sach id con */
const index = computed(() => {
  const parentOf = new Map()
  const childrenOf = new Map()
  const walk = (node, parentId) => {
    parentOf.set(node.id, parentId)
    childrenOf.set(node.id, node.children.map((c) => c.id))
    node.children.forEach((c) => walk(c, node.id))
  }
  roots.value.forEach((r) => walk(r, null))
  return { parentOf, childrenOf }
})

const hoveredId = ref(null)
/** Nut duoc ghim noi bat bang 1 lan cham / nhap (tren dien thoai thay cho hover) */
const pinnedId = ref(null)
/** Nut dang duoc tap trung: uu tien hover chuot, neu khong co thi nut da ghim */
const focusId = computed(() => hoveredId.value ?? pinnedId.value)
const isTouch = isTouchDevice
let leaveTimer = null

function hoverEnter(id) {
  clearTimeout(leaveTimer)
  hoveredId.value = id
}
function hoverLeave(id) {
  // tre nho de khong nhay mo/sang khi di chuot tu the nay sang the ke ben
  clearTimeout(leaveTimer)
  leaveTimer = setTimeout(() => {
    if (hoveredId.value === id) hoveredId.value = null
  }, 80)
}

/** Chuoi to tien cua nut dang hover (khong gom chinh no) */
const ancestors = computed(() => {
  const set = new Set()
  let p = focusId.value != null ? index.value.parentOf.get(focusId.value) : null
  while (p != null && !set.has(p)) {
    set.add(p)
    p = index.value.parentOf.get(p)
  }
  return set
})

/** Nhanh tap trung = to tien + chinh no + toan bo con chau */
const focusSet = computed(() => {
  if (focusId.value == null) return null
  const set = new Set(ancestors.value)
  const stack = [focusId.value]
  while (stack.length) {
    const id = stack.pop()
    if (set.has(id) && id !== focusId.value) continue
    set.add(id)
    stack.push(...(index.value.childrenOf.get(id) || []))
  }
  return set
})

/** Class trang thai cho moi CoupleCard */
function cardState(id) {
  const h = focusId.value
  if (h == null) return {}
  const { parentOf } = index.value
  return {
    active: id === h,
    related: parentOf.get(h) === id || parentOf.get(id) === h,
    dimmed: focusMode.value && !focusSet.value.has(id)
  }
}

/** Class cho moi duong noi (canh cha -> con) */
function edgeClass(e) {
  const h = focusId.value
  if (h == null) return null
  if (e.childId === h || e.parentId === h) return 'hl' // noi truc tiep voi nguoi dang hover
  if (ancestors.value.has(e.childId)) return 'trail' // tuyen tu to tien xuong
  if (focusMode.value && !focusSet.value.has(e.childId)) return 'dim'
  return null
}

// Ve duong noi sang mau sau cung de khong bi duong khac de len
const sortedEdges = computed(() => {
  const rank = (e) => ({ hl: 2, trail: 1 })[edgeClass(e)] || 0
  return [...edges.value].sort((a, b) => rank(a) - rank(b))
})

// =====================================================================
// Cham / nhap: 1 lan = noi bat (giong hover), 2 lan lien tiep = mo ho so
// =====================================================================

/** Khoang cach toi da giua 2 lan cham de tinh la "cham 2 lan" (ms) */
const DOUBLE_TAP_MS = 350
let lastTap = { personId: null, at: 0 }

/**
 * Tu nhan dien cham 2 lan thay vi dung su kien dblclick (Safari iOS khong ban dblclick on dinh).
 * Lan 1 thuc hien ngay (ghim noi bat) - khong phai cho xem co lan 2 hay khong, nen khong co do tre.
 */
function tap(nodeId, personId) {
  const now = Date.now()
  if (lastTap.personId === personId && now - lastTap.at <= DOUBLE_TAP_MS) {
    lastTap = { personId: null, at: 0 }
    gp.openMember(personId) // cham / nhap 2 lan -> mo ho so
    return
  }
  lastTap = { personId, at: now }
  pinnedId.value = nodeId // cham / nhap 1 lan -> lam noi bat nhanh
}

/** Cham vao nen trong cua khung cay -> bo ghim */
function onBackgroundClick(e) {
  if (!e.target.closest('.couple-card, .toggle, .zoom-fab')) pinnedId.value = null
}

provide('treeCtx', {
  maxDepth,
  collapsed,
  tap,
  /** Nut ✎ tren the: mo thang form sua */
  edit: (personId) => gp.editMember(personId),
  hoverEnter,
  hoverLeave,
  cardState
})

// =====================================================================
// Duong noi SVG: do vi tri cac the tren bo cuc that roi ve duong bac thang bo goc
// =====================================================================

const edges = shallowRef([])
const svgSize = reactive({ w: 0, h: 0 })

/**
 * Toa do cua el trong he toa do cua container, dung offsetLeft/offsetTop (KHONG bi anh huong boi
 * transform scale khi hover, va cung don vi voi SVG nam trong cung khoi bi zoom).
 */
function offsetWithin(el, container) {
  let x = 0
  let y = 0
  while (el && el !== container) {
    x += el.offsetLeft
    y += el.offsetTop
    el = el.offsetParent
  }
  return { x, y }
}

/** Duong bac thang: xuong tu day the cha me -> ngang -> xuong dinh the con, bo goc ban kinh r */
function elbowPath(px, py, cx, cy) {
  const mid = cy - 16 // thanh ngang chung cho ca hang anh chi em
  const dx = cx - px
  if (Math.abs(dx) < 1) return `M${px},${py} V${cy}`
  const r = Math.min(8, Math.abs(dx) / 2, (mid - py) / 2)
  const s = Math.sign(dx)
  return `M${px},${py} V${mid - r} Q${px},${mid} ${px + s * r},${mid} H${cx - s * r} Q${cx},${mid} ${cx},${mid + r} V${cy}`
}

function recomputeLinks() {
  const container = treeEl.value
  if (!container) return
  const cards = container.querySelectorAll('.couple-card[data-node-id]')
  const pos = new Map()
  for (const card of cards) {
    const { x, y } = offsetWithin(card, container)
    pos.set(Number(card.dataset.nodeId), {
      cx: x + card.offsetWidth / 2,
      top: y,
      bottom: y + card.offsetHeight,
      parentId: card.dataset.parentId ? Number(card.dataset.parentId) : null
    })
    resizeObserver?.observe(card) // the doi kich thuoc (mo danh sach doi vo, doi chip) -> ve lai
  }
  const list = []
  for (const [childId, c] of pos) {
    const p = c.parentId != null ? pos.get(c.parentId) : null
    if (!p) continue
    list.push({ key: `${c.parentId}-${childId}`, parentId: c.parentId, childId, d: elbowPath(p.cx, p.bottom, c.cx, c.top) })
  }
  edges.value = list
  svgSize.w = container.scrollWidth
  svgSize.h = container.scrollHeight
}

let frame = 0
function scheduleLinks() {
  cancelAnimationFrame(frame)
  frame = requestAnimationFrame(recomputeLinks)
}

// Ve lai khi: DOM cay doi (mo/thu nhanh, du lieu moi) hoac kich thuoc the / cay doi
let mutationObserver = null
let resizeObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(scheduleLinks) : null

watch(rootList, (el) => {
  mutationObserver?.disconnect()
  if (!el) return
  mutationObserver = new MutationObserver(scheduleLinks)
  mutationObserver.observe(el, { childList: true, subtree: true }) // khong theo doi class -> hover khong gay ve lai
  resizeObserver?.observe(el)
  scheduleLinks()
})
watch([roots, zoom, () => collapsed.size, maxDepth, fontScale], () => nextTick(scheduleLinks))

// =====================================================================
// Khung cay lap day phan con lai cua man hinh (khong de khoang trong ben duoi)
// =====================================================================

const viewportHeight = ref(null)
/** Chua cho dong huong dan ben duoi khung + le duoi cua trang */
const BOTTOM_RESERVE = 40

function fitViewport() {
  const el = viewport.value
  if (!el) return
  const top = el.getBoundingClientRect().top + window.scrollY
  viewportHeight.value = Math.max(420, Math.floor(window.innerHeight - top - BOTTOM_RESERVE))
}

// Tinh lai khi: doi kich thuoc cua so, thanh cong cu xuong dong (doi chieu cao), khung duoc tao lai
const toolbarObserver = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(fitViewport) : null
watch(viewport, (el) => {
  toolbarObserver?.disconnect()
  if (!el) return
  const toolbar = el.parentElement?.querySelector('.toolbar')
  if (toolbar) toolbarObserver?.observe(toolbar)
  fitViewport()
})
window.addEventListener('resize', fitViewport)

onBeforeUnmount(() => {
  window.removeEventListener('resize', fitViewport)
  toolbarObserver?.disconnect()
  mutationObserver?.disconnect()
  resizeObserver?.disconnect()
  resizeObserver = null
  cancelAnimationFrame(frame)
  clearTimeout(leaveTimer)
})

// =====================================================================
// Keo de di chuyen, Ctrl + lan chuot de thu phong
// =====================================================================

function startPan(e) {
  if (e.button !== 0 || e.target.closest('.couple-card, .toggle')) return
  const el = viewport.value
  const sx = e.clientX, sy = e.clientY, sl = el.scrollLeft, st = el.scrollTop
  el.classList.add('panning')
  const move = (ev) => {
    el.scrollLeft = sl - (ev.clientX - sx)
    el.scrollTop = st - (ev.clientY - sy)
  }
  const up = () => {
    el.classList.remove('panning')
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

// =====================================================================
// Thu phong: nut +/− (thanh cong cu & cum nut noi), Ctrl + lan chuot, chum 2 ngon tren dien thoai
// =====================================================================

const ZOOM_MIN = 0.3
const ZOOM_MAX = 2
const ZOOM_STEP = 0.1

/**
 * Doi muc zoom nhung GIU NGUYEN diem dang nhin o giua khung (khong bi nhay ve goc trai tren).
 * @param {number} value  muc zoom moi (tu dong gioi han trong [ZOOM_MIN, ZOOM_MAX])
 */
async function setZoom(value) {
  const next = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(value * 100) / 100))
  const prev = zoom.value
  const el = viewport.value
  if (next === prev || !el) {
    zoom.value = next
    return
  }
  // Toa do (chua zoom) cua tam khung hien tai
  const cx = (el.scrollLeft + el.clientWidth / 2) / prev
  const cy = (el.scrollTop + el.clientHeight / 2) / prev
  zoom.value = next
  await nextTick()
  el.scrollLeft = cx * next - el.clientWidth / 2
  el.scrollTop = cy * next - el.clientHeight / 2
}
const zoomIn = () => setZoom(+(zoom.value + ZOOM_STEP).toFixed(2))
const zoomOut = () => setZoom(+(zoom.value - ZOOM_STEP).toFixed(2))

/** Thu nho / phong to de ca cay vua khung */
async function fitToScreen() {
  const el = viewport.value
  const tree = rootList.value
  if (!el || !tree) return
  // Kich thuoc that cua cay khi zoom = 1 (offsetWidth la don vi chua zoom cua cay)
  const w = tree.offsetWidth + 48 + 8 // + padding cua .gp-tree + chut le
  const h = tree.offsetHeight + 48 + 8
  // Lam tron XUONG (setZoom lam tron 2 chu so - lam tron len co the lam cay nhinh hon khung vai px)
  await setZoom(Math.floor(Math.min(1, el.clientWidth / w, el.clientHeight / h) * 100) / 100)
  el.scrollLeft = (el.scrollWidth - el.clientWidth) / 2
  el.scrollTop = 0
}

function onWheel(e) {
  setZoom(zoom.value - Math.sign(e.deltaY) * ZOOM_STEP)
}

// ---- Chum / mo 2 ngon tay ----
let pinch = null
const fingerDistance = (t) => Math.hypot(t[0].clientX - t[1].clientX, t[0].clientY - t[1].clientY)

function onTouchStart(e) {
  if (e.touches.length === 2) pinch = { start: fingerDistance(e.touches), zoom: zoom.value }
}
function onTouchMove(e) {
  if (!pinch || e.touches.length !== 2) return
  e.preventDefault() // khong de trinh duyet zoom ca trang
  setZoom(pinch.zoom * (fingerDistance(e.touches) / pinch.start))
}
function onTouchEnd(e) {
  if (e.touches.length < 2) pinch = null
}
</script>

<style>
.legend {
  margin-left: auto;
  font-size: 12px;
  color: #6b625a;
  display: flex;
  align-items: center;
  gap: 4px;
}
.legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  margin-left: 8px;
}
.dot.male { background: var(--gp-male); }
.dot.female { background: var(--gp-female); }
.dot.dead { background: #9ca3af; }
.dot.outsider { background: #fff; border: 1px dashed #9ca3af; }
.legend-sep {
  margin-left: 12px;
  color: #8a8178;
}

.root-select {
  width: 260px;
}
.depth {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tree-hint {
  margin-top: 6px;
}

/* Khung chua cay + cum nut zoom noi */
.tree-stage {
  position: relative;
}
.zoom-fab {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.zoom-fab button {
  width: 36px;
  height: 36px;
  border: 1px solid #e3d5bf;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
  color: #7c2323;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  touch-action: manipulation;
  transition: background-color 0.15s, transform 0.1s;
}
.zoom-fab button:hover:not(:disabled) {
  background: #fdf3e1;
}
.zoom-fab button:active:not(:disabled) {
  transform: scale(0.94);
}
.zoom-fab button:disabled {
  opacity: 0.4;
  cursor: default;
}
.zoom-fab .pct {
  font-size: 11px;
  font-weight: 600;
}

.tree-viewport {
  overflow: auto;
  /* Cho phep vuot de cuon; chum 2 ngon do onTouchMove xu ly (khong zoom ca trang) */
  touch-action: pan-x pan-y;
  -webkit-overflow-scrolling: touch;
  background: #fffdf8;
  border: 1px solid #eadfcd;
  border-radius: 8px;
  width: 100%;
  box-sizing: border-box;
  min-height: 420px; /* chieu cao that duoc tinh boi fitViewport() */
  cursor: grab;
  user-select: none;
}
.tree-viewport.panning {
  cursor: grabbing;
}
.gp-tree {
  position: relative; /* goc toa do cho lop SVG va offsetWithin() */
  display: inline-block;
  min-width: 100%;
  padding: 24px;
  box-sizing: border-box;
}

/* ---- Bo cuc cay (duong noi ve bang SVG, khong dung pseudo-element nua) ---- */
.gp-tree ul {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  margin: 0;
  padding: 34px 0 0; /* khoang trong cho thanh ngang noi anh chi em */
  position: relative;
  z-index: 1;
}
.gp-tree > ul {
  padding-top: 0;
}
.gp-tree li {
  list-style: none;
  position: relative;
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ---- Lop duong noi ---- */
.tree-links {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
  overflow: visible;
}
.tree-links path {
  fill: none;
  stroke: var(--gp-line);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: stroke 0.2s ease, stroke-width 0.2s ease, opacity 0.2s ease;
}
/* Noi truc tiep voi nguoi dang hover (cha me -> no, no -> cac con) */
.tree-links path.hl {
  stroke: #c2410c;
  stroke-width: 3.5;
}
/* Tuyen to tien -> nguoi dang hover (truy vet the he) */
.tree-links path.trail {
  stroke: #f97316;
  stroke-width: 2.5;
}
.tree-links path.dim {
  opacity: 0.25;
}

/* Dat cuoi file: phai ghi de cac quy tac goc phia tren */
@media (max-width: 640px) {
  /* Dien thoai: an chu thich mau (da the hien tren the), o chon goc rong het hang */
  .legend {
    display: none;
  }
  .root-select {
    width: 100%;
  }
  .zoom-group {
    display: none; /* da co cum nut zoom noi trong khung cay */
  }
  .zoom-fab button {
    width: 44px; /* vung cham toi thieu theo khuyen nghi ~44px */
    height: 44px;
    font-size: 20px;
  }
}
@media (hover: none) {
  .tree-viewport {
    cursor: default;
  }
}
</style>
