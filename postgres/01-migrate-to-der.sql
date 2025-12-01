-- Migration: 01-migrate-to-der.sql
-- Apply incremental changes to bring an existing database in line with the DER and the application's entities.
-- This script is idempotent (as far as possible) and contains safety checks where destructive operations could occur.
-- Run on the target DB as: psql -h <host> -U <user> -d <db> -f postgres/01-migrate-to-der.sql

-- NOTE: Some operations (creating a UNIQUE index over an existing column) will be skipped and a WARNING emitted if duplicates exist.
-- Review results and fix duplicates before re-running the script if needed.

BEGIN;

-- 1) Create tables that might be missing (rutas, ruta_opciones, tramos already present in many DBs but ensure)
CREATE TABLE IF NOT EXISTS rutas (
  id_ruta BIGSERIAL PRIMARY KEY,
  id_solicitud BIGINT,
  fecha_creacion TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
  opcion_seleccionada_id BIGINT,
  cantidad_tramos INTEGER,
  cantidad_depositos INTEGER
);

CREATE TABLE IF NOT EXISTS ruta_opciones (
  id_ruta_opcion BIGSERIAL PRIMARY KEY,
  solicitud_id BIGINT,
  ruta_id BIGINT,
  opcion_index INTEGER,
  distancia_total DOUBLE PRECISION,
  duracion_total_horas DOUBLE PRECISION,
  depositos_ids TEXT,
  depositos_nombres TEXT,
  tramos_json TEXT,
  geometry TEXT,
  fecha_creacion TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);

-- Ensure tramos table exists and has costo_real
CREATE TABLE IF NOT EXISTS tramos (
  id_tramo BIGSERIAL PRIMARY KEY
);

-- Add missing columns to tramos (if not exists)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='tramos' AND column_name='costo_real') THEN
    ALTER TABLE tramos ADD COLUMN costo_real NUMERIC(12,2);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='tramos' AND column_name='camion_dominio') THEN
    ALTER TABLE tramos ADD COLUMN camion_dominio VARCHAR(100);
  END IF;
END$$;

-- Add missing columns to solicitudes
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='solicitudes' AND column_name='costo_final') THEN
    ALTER TABLE solicitudes ADD COLUMN costo_final NUMERIC(12,2);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='solicitudes' AND column_name='tiempo_real') THEN
    ALTER TABLE solicitudes ADD COLUMN tiempo_real NUMERIC(12,2);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='solicitudes' AND column_name='ruta_id') THEN
    ALTER TABLE solicitudes ADD COLUMN ruta_id BIGINT;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='solicitudes' AND column_name='tarifa_id') THEN
    ALTER TABLE solicitudes ADD COLUMN tarifa_id BIGINT;
  END IF;
END$$;

-- 2) Create indexes used by application (non-unique ones are safe)
CREATE INDEX IF NOT EXISTS idx_rutas_solicitud ON rutas(id_solicitud);
CREATE INDEX IF NOT EXISTS idx_ruta_opciones_ruta ON ruta_opciones(ruta_id);
CREATE INDEX IF NOT EXISTS idx_tramos_ruta ON tramos(ruta_id);
CREATE INDEX IF NOT EXISTS idx_tramos_camion_dominio ON tramos(camion_dominio);
CREATE INDEX IF NOT EXISTS idx_solicitudes_ruta ON solicitudes(ruta_id);

-- 3) Unique index on camiones.dominio: check duplicates first
DO $$
DECLARE
  dup_count INT;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ux_camiones_dominio') THEN
    SELECT COUNT(*) INTO dup_count FROM (
      SELECT LOWER(dominio) AS d, COUNT(*) FROM camiones GROUP BY LOWER(dominio) HAVING COUNT(*) > 1
    ) t;
    IF dup_count > 0 THEN
      RAISE WARNING 'Cannot create unique index ux_camiones_dominio: % duplicate dominio values found. Resolve duplicates before creating the unique index.' , dup_count;
    ELSE
      EXECUTE 'CREATE UNIQUE INDEX ux_camiones_dominio ON camiones(LOWER(dominio))';
    END IF;
  END IF;
END$$;

-- 4) Ensure estado_* unique indexes exist to allow ON CONFLICT DO NOTHING when seeding
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_solicitud_nombre ON estado_solicitud (LOWER(nombre));
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_contenedor_nombre ON estado_contenedor (LOWER(nombre));
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_camion_nombre ON estado_camion (LOWER(nombre));
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_tramo_nombre ON estado_tramo (LOWER(nombre));

-- 5) Add FK constraints (safely via existence checks)
-- rutas.opcion_seleccionada_id -> ruta_opciones(id_ruta_opcion)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_rutas_opcion_seleccionada'
  ) THEN
    -- Only add constraint if referenced column exists
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rutas' AND column_name='opcion_seleccionada_id')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='ruta_opciones') THEN
      EXECUTE 'ALTER TABLE rutas ADD CONSTRAINT fk_rutas_opcion_seleccionada FOREIGN KEY (opcion_seleccionada_id) REFERENCES ruta_opciones(id_ruta_opcion)';
    END IF;
  END IF;
END$$;

-- solicitudes.ruta_id -> rutas(id_ruta)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_solicitudes_ruta'
  ) THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='solicitudes' AND column_name='ruta_id')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='rutas') THEN
      EXECUTE 'ALTER TABLE solicitudes ADD CONSTRAINT fk_solicitudes_ruta FOREIGN KEY (ruta_id) REFERENCES rutas(id_ruta)';
    END IF;
  END IF;
END$$;

-- ruta_opciones.ruta_id -> rutas.id_ruta
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_ruta_opciones_ruta'
  ) THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ruta_opciones' AND column_name='ruta_id')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='rutas') THEN
      EXECUTE 'ALTER TABLE ruta_opciones ADD CONSTRAINT fk_ruta_opciones_ruta FOREIGN KEY (ruta_id) REFERENCES rutas(id_ruta) ON DELETE CASCADE';
    END IF;
  END IF;
END$$;

-- ruta_opciones.solicitud_id -> solicitudes(id_solicitud)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_ruta_opciones_solicitud'
  ) THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ruta_opciones' AND column_name='solicitud_id')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='solicitudes') THEN
      EXECUTE 'ALTER TABLE ruta_opciones ADD CONSTRAINT fk_ruta_opciones_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes(id_solicitud) ON DELETE CASCADE';
    END IF;
  END IF;
END$$;

-- 6) Seed estado_* tables (safe because we created unique indexes above)
INSERT INTO estado_solicitud (nombre) VALUES ('PENDIENTE'), ('PROGRAMADO'), ('EN_RUTA'), ('ENTREGADO'), ('FINALIZADO') ON CONFLICT DO NOTHING;
INSERT INTO estado_contenedor (nombre) VALUES ('LIBRE'), ('ASIGNADO'), ('EN_DEPOSITO'), ('EN_TRANSITO') ON CONFLICT DO NOTHING;
INSERT INTO estado_camion (nombre) VALUES ('DISPONIBLE'), ('ASIGNADO'), ('MANTENIMIENTO') ON CONFLICT DO NOTHING;
INSERT INTO estado_tramo (nombre) VALUES ('PENDIENTE'), ('EN_PROCESO'), ('FINALIZADO') ON CONFLICT DO NOTHING;

COMMIT;

-- Post-migration checks (non-destructive suggestions):
-- 1) If the unique index on camiones could not be created due to duplicates, run:
--    SELECT LOWER(dominio) AS d, COUNT(*) FROM camiones GROUP BY LOWER(dominio) HAVING COUNT(*)>1;
--    Then decide how to deduplicate (merge/rename/remove) and re-run this migration.
-- 2) Consider adding `camion_id` FK to `tramos` (to store the chosen truck by id). That migration is optional and may require cleaning/choosing mapping between dominio and id.


