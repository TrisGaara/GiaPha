-- Nguoi dung he thong
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(150) UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(150),
    phone           VARCHAR(30),
    system_role     VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Dong ho (tenant)
CREATE TABLE families (
    id                     BIGSERIAL PRIMARY KEY,
    name                   VARCHAR(200) NOT NULL,
    surname                VARCHAR(50)  NOT NULL,
    description            TEXT,
    origin                 VARCHAR(255),
    ancestral_hall_address VARCHAR(255),
    join_code              VARCHAR(16)  NOT NULL UNIQUE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    telegram_chat_id       VARCHAR(50),
    created_by             BIGINT REFERENCES users(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Thanh vien trong pha he
CREATE TABLE members (
    id                 BIGSERIAL PRIMARY KEY,
    family_id          BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id            BIGINT REFERENCES users(id) ON DELETE SET NULL,
    full_name          VARCHAR(150) NOT NULL,
    other_name         VARCHAR(150),
    gender             VARCHAR(10)  NOT NULL,
    generation         INT,
    birth_order        INT,
    father_id          BIGINT REFERENCES members(id) ON DELETE SET NULL,
    mother_id          BIGINT REFERENCES members(id) ON DELETE SET NULL,
    outsider           BOOLEAN NOT NULL DEFAULT FALSE,
    birth_date_solar   DATE,
    birth_lunar_day    INT,
    birth_lunar_month  INT,
    birth_lunar_year   INT,
    birth_lunar_leap   BOOLEAN NOT NULL DEFAULT FALSE,
    alive              BOOLEAN NOT NULL DEFAULT TRUE,
    death_date_solar   DATE,
    death_lunar_day    INT,
    death_lunar_month  INT,
    death_lunar_year   INT,
    death_lunar_leap   BOOLEAN NOT NULL DEFAULT FALSE,
    burial_place       VARCHAR(255),
    hometown           VARCHAR(255),
    occupation         VARCHAR(255),
    biography          TEXT,
    achievements       TEXT,
    avatar_url         VARCHAR(500),
    public_profile     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_members_family ON members(family_id);
CREATE INDEX idx_members_father ON members(father_id);
CREATE INDEX idx_members_mother ON members(mother_id);
CREATE UNIQUE INDEX uq_members_family_user ON members(family_id, user_id) WHERE user_id IS NOT NULL;

-- Hon nhan (ho tro da the / tai hon)
CREATE TABLE marriages (
    id           BIGSERIAL PRIMARY KEY,
    family_id    BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    husband_id   BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    wife_id      BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    order_index  INT NOT NULL DEFAULT 1,
    status       VARCHAR(20) NOT NULL DEFAULT 'MARRIED',
    note         VARCHAR(255),
    UNIQUE (husband_id, wife_id)
);
CREATE INDEX idx_marriages_family ON marriages(family_id);

-- Vai tro nguoi dung trong tung dong ho
CREATE TABLE family_roles (
    id                     BIGSERIAL PRIMARY KEY,
    family_id              BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id                BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                   VARCHAR(20) NOT NULL,
    branch_root_member_id  BIGINT REFERENCES members(id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (family_id, user_id)
);

-- Yeu cau tham gia dong ho
CREATE TABLE join_requests (
    id                 BIGSERIAL PRIMARY KEY,
    family_id          BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    claimed_member_id  BIGINT REFERENCES members(id) ON DELETE SET NULL,
    message            VARCHAR(500),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by        BIGINT REFERENCES users(id),
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_join_requests_family ON join_requests(family_id, status);

-- Su kien dong ho
CREATE TABLE events (
    id                  BIGSERIAL PRIMARY KEY,
    family_id           BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    member_id           BIGINT REFERENCES members(id) ON DELETE SET NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    type                VARCHAR(30) NOT NULL,
    calendar_type       VARCHAR(10) NOT NULL,
    solar_date          DATE,
    lunar_day           INT,
    lunar_month         INT,
    lunar_year          INT,
    recurring           BOOLEAN NOT NULL DEFAULT TRUE,
    location            VARCHAR(255),
    remind_days_before  VARCHAR(50),
    created_by          BIGINT REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_family ON events(family_id);

-- Thong bao trong ung dung
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id   BIGINT REFERENCES families(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    content     TEXT,
    seen        BOOLEAN NOT NULL DEFAULT FALSE,
    dedupe_key  VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, dedupe_key)
);
CREATE INDEX idx_notifications_user ON notifications(user_id, seen);
