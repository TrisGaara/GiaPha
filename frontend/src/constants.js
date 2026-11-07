import dayjs from 'dayjs'

export const ROLE_LABELS = {
  OWNER: 'Tộc trưởng',
  ADMIN: 'Ban quản trị',
  BRANCH_MANAGER: 'Trưởng chi',
  MEMBER: 'Thành viên',
  VIEWER: 'Khách (chỉ xem)'
}

const ROLE_LEVEL = { OWNER: 100, ADMIN: 80, BRANCH_MANAGER: 60, MEMBER: 40, VIEWER: 20 }

export const atLeast = (role, min) => (ROLE_LEVEL[role] || 0) >= ROLE_LEVEL[min]

export const FAMILY_STATUS = {
  PENDING: { label: 'Chờ duyệt', type: 'warning' },
  ACTIVE: { label: 'Hoạt động', type: 'success' },
  LOCKED: { label: 'Bị khóa', type: 'danger' }
}

export const EVENT_TYPES = {
  DEATH_ANNIVERSARY: 'Ngày giỗ',
  ANCESTOR_DAY: 'Giỗ tổ',
  BRANCH_ANNIVERSARY: 'Giỗ chi / phòng',
  MEETING: 'Họp mặt',
  LONGEVITY: 'Mừng thọ',
  SPRING_FESTIVAL: 'Khai xuân',
  SCHOLARSHIP: 'Khuyến học',
  OTHER: 'Khác'
}

export const MARRIAGE_STATUS = { ACTIVE: 'Vợ chồng', DIVORCED: 'Đã ly hôn', DECEASED: 'Góa (một bên đã mất)' }

export const fmtDate = (d) => (d ? dayjs(d).format('DD/MM/YYYY') : '')
export const fmtDateTime = (d) => (d ? dayjs(d).format('HH:mm DD/MM/YYYY') : '')

/** "1920 – 1995", "s. 1980", ... */
export function lifeSpan(m) {
  const by = m.birthDateSolar ? dayjs(m.birthDateSolar).year() : m.birthLunar?.year
  const dy = m.deathDateSolar ? dayjs(m.deathDateSolar).year() : m.deathLunar?.year
  if (!m.alive) return `${by || '?'} – ${dy || '?'}`
  return by ? `s. ${by}` : ''
}
