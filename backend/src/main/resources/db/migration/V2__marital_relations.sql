-- =====================================================================
-- V2: Chuan hoa nghiep vu Hon nhan (Vo/Chong) va Lien ket Con cai
--   * members   -> family_members  (them is_blood_line thay cho outsider)
--   * marriages -> marital_relations (marriage_order, status ACTIVE/DIVORCED/DECEASED, audit)
-- Dung RENAME de giu nguyen du lieu da co tu V1.
-- =====================================================================

-- ---------- 1. family_members ----------
ALTER TABLE members RENAME TO family_members;
ALTER SEQUENCE members_id_seq RENAME TO family_members_id_seq;

-- is_blood_line = TRUE : nguoi thuoc huyet thong dong ho
-- is_blood_line = FALSE: thanh vien ngoai toc chi xuat hien qua hon nhan (con dau / con re)
ALTER TABLE family_members ADD COLUMN is_blood_line BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE family_members SET is_blood_line = NOT outsider;
ALTER TABLE family_members DROP COLUMN outsider;

ALTER INDEX idx_members_family RENAME TO idx_family_members_family;
ALTER INDEX idx_members_father RENAME TO idx_family_members_father;
ALTER INDEX idx_members_mother RENAME TO idx_family_members_mother;
ALTER INDEX uq_members_family_user RENAME TO uq_family_members_family_user;
CREATE INDEX idx_family_members_blood_line ON family_members (family_id, is_blood_line);

-- Mot nguoi khong the la cha/me cua chinh minh
ALTER TABLE family_members
    ADD CONSTRAINT ck_family_members_not_own_parent
        CHECK ((father_id IS NULL OR father_id <> id) AND (mother_id IS NULL OR mother_id <> id));

-- ---------- 2. marital_relations ----------
ALTER TABLE marriages RENAME TO marital_relations;
ALTER SEQUENCE marriages_id_seq RENAME TO marital_relations_id_seq;
ALTER TABLE marital_relations RENAME COLUMN order_index TO marriage_order;
ALTER TABLE marital_relations RENAME CONSTRAINT marriages_husband_id_wife_id_key TO uq_marital_relations_couple;
ALTER INDEX idx_marriages_family RENAME TO idx_marital_relations_family;

-- Chuyen trang thai cu sang enum moi
UPDATE marital_relations
SET status = CASE status
                 WHEN 'MARRIED' THEN 'ACTIVE'
                 WHEN 'WIDOWED' THEN 'DECEASED'
                 ELSE status
    END;
ALTER TABLE marital_relations ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE marital_relations
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT ck_marital_relations_status CHECK (status IN ('ACTIVE', 'DIVORCED', 'DECEASED')),
    ADD CONSTRAINT ck_marital_relations_order CHECK (marriage_order >= 1),
    ADD CONSTRAINT ck_marital_relations_not_self CHECK (husband_id <> wife_id);

CREATE INDEX idx_marital_relations_husband ON marital_relations (husband_id, marriage_order);
CREATE INDEX idx_marital_relations_wife ON marital_relations (wife_id, marriage_order);
