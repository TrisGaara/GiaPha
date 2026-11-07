-- =====================================================================
-- V3: Thu tu con cai (birth_order)
--
-- Nhom anh chi em ("sibling group") = toan bo con cua NGUOI CHA THUOC HUYET THONG
-- (ke ca con voi cac doi vo khac nhau). Neu cha la con re / khong ro cha thi nhom theo NGUOI ME.
-- Day cung la cach cay pha he ve cac nhanh con duoi mot nguoi.
-- =====================================================================

-- 1. Chuan hoa du lieu cu: danh so lai 1..n trong tung nhom
--    (giu thu tu da nhap, nguoi chua co so xep sau theo ngay sinh, roi theo thu tu tao)
WITH grp AS (
    SELECT c.id,
           CASE
               WHEN f.id IS NOT NULL AND f.is_blood_line THEN f.id
               ELSE COALESCE(c.mother_id, c.father_id)
           END AS group_parent_id,
           c.birth_order,
           c.birth_date_solar
    FROM family_members c
             LEFT JOIN family_members f ON f.id = c.father_id
    WHERE c.father_id IS NOT NULL OR c.mother_id IS NOT NULL
),
ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY group_parent_id
                              ORDER BY birth_order NULLS LAST, birth_date_solar NULLS LAST, id) AS rn
    FROM grp
)
UPDATE family_members m
SET birth_order = r.rn
FROM ranked r
WHERE m.id = r.id;

-- Thuy to / nguoi khong co cha me trong pha he
UPDATE family_members SET birth_order = 1 WHERE birth_order IS NULL;

-- 2. Rang buoc cot
ALTER TABLE family_members
    ALTER COLUMN birth_order SET DEFAULT 1,
    ALTER COLUMN birth_order SET NOT NULL,
    ADD CONSTRAINT ck_family_members_birth_order CHECK (birth_order BETWEEN 1 AND 99);

-- 3. Index phuc vu truy van "danh sach con cua mot nguoi, sap theo thu tu sinh"
CREATE INDEX idx_family_members_father_birth_order ON family_members (father_id, birth_order);
CREATE INDEX idx_family_members_mother_birth_order ON family_members (mother_id, birth_order);

-- Ghi chu: khong dung UNIQUE (nhom, birth_order) o tang CSDL vi
--   (a) nhom anh chi em phu thuoc vao is_blood_line cua nguoi cha (khong bieu dien duoc bang partial index),
--   (b) khi dao thu tu phai hoan doi so tam thoi (unique index khong DEFERRABLE duoc).
-- Tinh duy nhat & lien tuc 1..n duoc dam bao boi SiblingOrderService (luon danh so lai ca nhom).

-- 4. Quy uoc hien thi cua tung dong ho
ALTER TABLE families
    ADD COLUMN child_sort_mode VARCHAR(20) NOT NULL DEFAULT 'BIRTH_ORDER',
    ADD CONSTRAINT ck_families_child_sort_mode CHECK (child_sort_mode IN ('BIRTH_ORDER', 'SONS_FIRST'));
