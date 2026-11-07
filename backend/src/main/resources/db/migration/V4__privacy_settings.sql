-- =====================================================================
-- V4: Cau hinh quyen rieng tu & an/hien thong tin ca nhan
-- =====================================================================

-- Doi ten cho thong nhat quy uoc is_* (checkbox "Cong khai chi tiet voi khach (Viewer)")
ALTER TABLE family_members RENAME COLUMN public_profile TO is_public_profile;

ALTER TABLE family_members
    -- An/hien hoan toan tren cay pha he (van luu trong CSDL, Ban quan tri van thay o dang mo)
    ADD COLUMN is_visible_on_tree BOOLEAN NOT NULL DEFAULT TRUE,
    -- Cac cong tac an tung nhom truong voi khach (VIEWER)
    ADD COLUMN hide_birth_date    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hide_death_date    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hide_contact_info  BOOLEAN NOT NULL DEFAULT TRUE,   -- lien he: mac dinh AN
    ADD COLUMN hide_bio           BOOLEAN NOT NULL DEFAULT FALSE,
    -- Thong tin lien he (truoc day chua co) - doi tuong cua hide_contact_info
    ADD COLUMN phone              VARCHAR(30),
    ADD COLUMN email              VARCHAR(150),
    ADD COLUMN address            VARCHAR(255);

-- Chi muc mot phan cho truy van ve cay cua nguoi dung thong thuong (chi lay nguoi dang hien)
CREATE INDEX idx_family_members_tree_visible
    ON family_members (family_id, generation)
    WHERE is_visible_on_tree;
