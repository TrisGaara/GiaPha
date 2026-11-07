/**
 * Dung "cay cap vo chong" tu du lieu phang cua API GET /api/families/{id}/tree
 * ({ members: MemberDto[], maritalRelations: MaritalRelationDto[] }).
 *
 * Moi nut (CoupleNode) = MOT thanh vien huyet thong (primary) + cac doi vo/chong cua ho (partners) + cac con.
 * Component chi viec render nut, khong phai tu tinh quan he nua.
 *
 * @typedef {Object} Partner
 * @property {Object} person    MemberDto cua vo/chong
 * @property {Object} marriage  MaritalRelationDto (marriageOrder, status ACTIVE/DIVORCED/DECEASED)
 *
 * @typedef {Object} CoupleNode
 * @property {number} id               id cua primary (dung lam key va de thu gon / mo rong)
 * @property {Object} primary          thanh vien huyet thong cua nut
 * @property {'MALE'|'FEMALE'} side    gioi tinh primary -> quyet dinh primary nam o nua trai (chong) hay phai (vo)
 * @property {Partner[]} partners      cac doi vo/chong hien trong the, sap theo marriageOrder
 * @property {number} defaultPartnerIndex  doi vo/chong hien mac dinh (cuoc hon nhan ACTIVE dau tien)
 * @property {CoupleNode[]} children   giu nguyen thu tu backend tra ve (thu tu sinh / con trai truoc)
 * @property {number} descendantCount  tong so con chau (hien tren nut "+N" khi thu gon)
 * @property {?number} parentOrder     voi nut con: thu tu cua nguoi me/cha ngoai toc trong the cha me
 *                                     (chi dat khi cha/me co tu 2 doi vo/chong tro len) -> hien huy hieu ①②
 */

/**
 * @param {Object[]} members
 * @param {Object[]} relations
 * @param {{ rootId?: number|null }} [options] rootId: chi dung cay tu mot nguoi
 * @returns {CoupleNode[]}
 */
export function buildForest(members, relations, { rootId = null } = {}) {
  const byId = new Map(members.map((m) => [m.id, m]))
  const partnersOf = indexPartners(byId, relations)
  const childrenOf = indexChildren(members, byId)

  /** Vo/chong duoc ve TRONG the cua nguoi kia (thay vi o vi tri rieng cua ho tren cay) */
  const shownInCard = (p) => !p.bloodLine || (!p.fatherId && !p.motherId)

  const visited = new Set() // chong vong lap / ve trung khi du lieu loi

  function makeNode(person) {
    if (!person || visited.has(person.id)) return null
    visited.add(person.id)

    const partners = (partnersOf.get(person.id) || []).filter((p) => shownInCard(p.person))
    partners.forEach((p) => visited.add(p.person.id))

    const children = (childrenOf.get(person.id) || []).map(makeNode).filter(Boolean)
    if (partners.length > 1) {
      // Danh dau moi con thuoc doi vo/chong nao, de hien huy hieu ①② tuong ung voi chip trong the cha me
      for (const child of children) {
        const otherParentId = person.gender === 'MALE' ? child.primary.motherId : child.primary.fatherId
        const p = partners.find((x) => x.person.id === otherParentId)
        child.parentOrder = p ? p.marriage.marriageOrder : null
        child.parentName = p ? p.person.fullName : null
      }
    }

    const activeIndex = partners.findIndex((p) => p.marriage.status === 'ACTIVE')
    return {
      id: person.id,
      primary: person,
      side: person.gender,
      partners,
      defaultPartnerIndex: activeIndex >= 0 ? activeIndex : 0,
      children,
      descendantCount: children.reduce((n, c) => n + 1 + c.descendantCount, 0),
      parentOrder: null,
      parentName: null
    }
  }

  if (rootId != null) {
    const root = makeNode(byId.get(rootId))
    return root ? [root] : []
  }

  // Goc: nguoi huyet thong khong co cha me trong pha he (backend da sap theo doi)
  const forest = []
  for (const m of members) {
    if (m.bloodLine && !m.fatherId && !m.motherId && !visited.has(m.id)) {
      const node = makeNode(m)
      if (node) forest.push(node)
    }
  }
  return forest
}

function indexPartners(byId, relations) {
  const map = new Map()
  const add = (id, otherId, marriage) => {
    const person = byId.get(otherId)
    if (!person || !byId.has(id)) return
    if (!map.has(id)) map.set(id, [])
    map.get(id).push({ person, marriage })
  }
  for (const r of relations) {
    add(r.husbandId, r.wifeId, r)
    add(r.wifeId, r.husbandId, r)
  }
  for (const list of map.values()) list.sort((a, b) => a.marriage.marriageOrder - b.marriage.marriageOrder)
  return map
}

/** Nhom con theo "nguoi cha/me nhom" - giong SiblingOrderService o backend, giu nguyen thu tu backend */
function indexChildren(members, byId) {
  const map = new Map()
  for (const c of members) {
    let parentId = c.fatherId
    const father = byId.get(parentId)
    // Con cua con gai dong ho (cha la con re / khong ro cha) -> nam duoi nguoi me
    if (!parentId || (father && !father.bloodLine)) parentId = c.motherId || parentId
    if (!parentId || !byId.has(parentId)) continue
    if (!map.has(parentId)) map.set(parentId, [])
    map.get(parentId).push(c)
  }
  return map
}

/** ① ② ③ ... cho thu tu vo/chong */
export function circled(n) {
  return n >= 1 && n <= 20 ? String.fromCharCode(0x2460 + n - 1) : `(${n})`
}
