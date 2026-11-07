import { computed, ref } from 'vue'

/**
 * Chieu rong cua so dung chung cho toan app (mot listener duy nhat).
 * Dung de doi kich thuoc drawer / so cot form tren dien thoai.
 */
const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1280)
if (typeof window !== 'undefined') {
  window.addEventListener('resize', () => (width.value = window.innerWidth), { passive: true })
}

/** true khi man hinh nho hon breakpoint (mac dinh 640px ~ dien thoai) */
export function useIsMobile(breakpoint = 640) {
  return computed(() => width.value < breakpoint)
}

/** Thiet bi chinh la cam ung (khong co chuot de hover) */
export const isTouchDevice =
  typeof window !== 'undefined' && window.matchMedia?.('(hover: none) and (pointer: coarse)').matches
