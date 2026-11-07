<template>
  <div class="person" :class="[m.gender === 'MALE' ? 'male' : 'female', {
    embedded, dead: !m.alive, outsider: !m.bloodLine, placeholder: m.placeholder, 'hidden-node': m.privacy && !m.privacy.visibleOnTree
  }]" :title="m.privacy && !m.privacy.visibleOnTree ? 'Đang ẩn khỏi cây với người dùng thông thường' : ''">
    <div class="gen" v-if="m.generation">Đ{{ m.generation }}</div>
    <span v-if="m.privacy && !m.privacy.visibleOnTree" class="eye" title="Đã ẩn khỏi cây">⊘</span>
    <div class="name">{{ m.fullName }}</div>
    <div class="sub" v-if="m.otherName">{{ m.otherName }}</div>
    <div class="years">{{ lifeSpan(m) }}</div>
    <div class="anniv" v-if="!m.alive && m.deathLunar">Giỗ {{ m.deathLunar.day }}/{{ m.deathLunar.month }} ÂL</div>
    <span v-if="m.userId" class="acc" title="Đã liên kết tài khoản">●</span>
  </div>
</template>

<script setup>
import { lifeSpan } from '../constants'
defineProps({
  m: { type: Object, required: true },
  /** Nam trong khung chung cua CoupleCard: bo vien rieng, chi giu vach mau theo gioi tinh o tren */
  embedded: { type: Boolean, default: false }
})
</script>

<style scoped>
/* --tree-fs: he so co chu cua cay (FamilyTree); ngoai cay = 1.
   CHI nhan vao co chu - kich thuoc o giu co dinh (A-/A+ khong phai zoom); chu to thi xuong dong, o cao them. */
.person {
  position: relative;
  width: 118px;
  min-height: 62px;
  overflow-wrap: anywhere;
  padding: 6px 6px 5px;
  border-radius: 8px;
  background: #fff;
  border: 2px solid;
  cursor: pointer;
  font-size: calc(12px * var(--tree-fs, 1));
  text-align: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: transform 0.1s, box-shadow 0.1s;
  box-sizing: border-box;
}
.person:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.15);
}
.person.male { border-color: var(--gp-male); }
.person.female { border-color: var(--gp-female); }
.person.dead {
  background: #f3f4f6;
  border-color: #9ca3af;
  color: #4b5563;
}
.person.outsider { border-style: dashed; }
/* ---- Che do nam trong the vo chong ---- */
.person.embedded {
  border: none;
  border-top: 3px solid;
  border-radius: 8px 8px 0 0;
  background: transparent;
  box-shadow: none;
  padding-top: 8px;
}
.person.embedded:hover {
  transform: none;
  box-shadow: none;
}
.person.embedded.male { border-top-color: var(--gp-male); }
.person.embedded.female { border-top-color: var(--gp-female); }
.person.embedded.dead { border-top-color: #9ca3af; background: transparent; }
.person.embedded.outsider { border-top-style: dashed; }
.person.embedded .gen {
  top: -10px;
}
/* Nut an danh (nguoi dung thong thuong): giu cau truc cay, khong co thong tin */
.person.placeholder {
  background: repeating-linear-gradient(45deg, #f3f4f6, #f3f4f6 6px, #e5e7eb 6px, #e5e7eb 12px);
  border-color: #cbd5e1;
  border-style: dotted;
  color: #6b7280;
  font-style: italic;
}
/* Nguoi bi an khoi cay - chi Ban quan tri thay, ve mo */
.person.hidden-node {
  opacity: 0.45;
}
.eye {
  position: absolute;
  top: 1px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: #9b2c2c;
}
.name {
  font-weight: 600;
  font-size: calc(13px * var(--tree-fs, 1));
  line-height: 1.25;
}
.sub {
  font-style: italic;
  color: #6b7280;
}
.years {
  color: #6b625a;
  margin-top: 2px;
}
.anniv {
  color: #9b2c2c;
  font-size: calc(11px * var(--tree-fs, 1));
}
.gen {
  position: absolute;
  top: -9px;
  left: 6px;
  background: var(--gp-gold);
  color: #fff;
  font-size: calc(10px * var(--tree-fs, 1));
  padding: 0 5px;
  border-radius: 6px;
}
.acc {
  position: absolute;
  top: 2px;
  right: 5px;
  color: #38a169;
  font-size: 10px;
}
</style>
