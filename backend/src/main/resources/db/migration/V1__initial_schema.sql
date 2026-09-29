-- Esquema inicial de WalkSecurity.
-- Las coordenadas se guardan como lat/lng (WGS84). Si más adelante se necesitan consultas
-- espaciales complejas se puede migrar a PostGIS sin cambiar el API.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE trusted_contacts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    phone        VARCHAR(20)  NOT NULL,
    relationship VARCHAR(60),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_trusted_contact_phone UNIQUE (user_id, phone)
);

CREATE TABLE location_records (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    accuracy_meters REAL,
    recorded_at     TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_location_records_user_time ON location_records (user_id, recorded_at DESC);

-- Fase 2: zonas circulares (coinciden con las geocercas de Android).
-- risk_score es una ESTIMACIÓN en [0, 1], no una garantía de peligro.
CREATE TABLE risk_zones (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120)     NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    radius_meters REAL             NOT NULL CHECK (radius_meters > 0),
    risk_score    REAL             NOT NULL CHECK (risk_score BETWEEN 0 AND 1),
    source        VARCHAR(20)      NOT NULL DEFAULT 'MANUAL', -- MANUAL | MODEL
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Fase 4: histórico de incidentes para entrenar el modelo (ubicación, hora, día).
CREATE TABLE incidents (
    id          BIGSERIAL PRIMARY KEY,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    occurred_at TIMESTAMPTZ      NOT NULL,
    category    VARCHAR(60)      NOT NULL,
    severity    SMALLINT         NOT NULL DEFAULT 1 CHECK (severity BETWEEN 1 AND 5),
    source      VARCHAR(60)
);
CREATE INDEX idx_incidents_time ON incidents (occurred_at);

CREATE TABLE alerts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL, -- SOS | RISK_ZONE
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | RESOLVED
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    accuracy_meters REAL,
    message         VARCHAR(500),
    risk_zone_id    BIGINT REFERENCES risk_zones (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_user_time ON alerts (user_id, created_at DESC);
