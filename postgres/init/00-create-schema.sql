-- Schema creation script for TPI-Backend (PostgreSQL)
-- Creates tables, foreign keys and indexes following the DER and current entity models

BEGIN;

-- Clients
CREATE TABLE IF NOT EXISTS clientes (
	id_cliente BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(255) NOT NULL,
	email VARCHAR(255),
	telefono VARCHAR(50)
);

-- Estados (solicitud, contenedor, camion, tramo)
CREATE TABLE IF NOT EXISTS estado_solicitud (
	id_estado BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS estado_contenedor (
	id_estado BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS estado_camion (
	id_estado BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS estado_tramo (
	id_estado BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(100) NOT NULL
);

-- Tipo de tramo
CREATE TABLE IF NOT EXISTS tipo_tramo (
	id_tipo BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(100) NOT NULL
);

-- Tarifas y rangos
CREATE TABLE IF NOT EXISTS tarifas (
	id_tarifa BIGSERIAL PRIMARY KEY,
	costo_base_gestion_fijo NUMERIC(12,2) DEFAULT 0,
	valor_litro_combustible NUMERIC(12,4) DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tarifa_volumen_peso (
	id BIGSERIAL PRIMARY KEY,
	id_tarifa BIGINT NOT NULL REFERENCES tarifas(id_tarifa) ON DELETE CASCADE,
	volumen_min DOUBLE PRECISION,
	volumen_max DOUBLE PRECISION,
	peso_min DOUBLE PRECISION,
	peso_max DOUBLE PRECISION,
	costo_por_km_base DOUBLE PRECISION
);
CREATE INDEX IF NOT EXISTS idx_tarifa_volumen_tarifa ON tarifa_volumen_peso(id_tarifa);

-- Ciudades y depósitos
CREATE TABLE IF NOT EXISTS ciudad (
	id_ciudad BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS depositos (
	id_deposito BIGSERIAL PRIMARY KEY,
	nombre VARCHAR(255) NOT NULL,
	direccion VARCHAR(1024),
	latitud NUMERIC(11,8),
	longitud NUMERIC(11,8),
	id_ciudad BIGINT REFERENCES ciudad(id_ciudad),
	costo_estadia_diario NUMERIC(12,2) DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_depositos_ciudad ON depositos(id_ciudad);

-- Contenedores
CREATE TABLE IF NOT EXISTS contenedores (
	id_contenedor BIGSERIAL PRIMARY KEY,
	peso NUMERIC(12,3),
	volumen NUMERIC(12,3),
	estado_id BIGINT REFERENCES estado_contenedor(id_estado),
	cliente_id BIGINT REFERENCES clientes(id_cliente)
);
CREATE INDEX IF NOT EXISTS idx_contenedores_cliente ON contenedores(cliente_id);
CREATE INDEX IF NOT EXISTS idx_contenedores_estado ON contenedores(estado_id);

-- Camiones
CREATE TABLE IF NOT EXISTS camiones (
	id_camion BIGSERIAL PRIMARY KEY,
	dominio VARCHAR(100) NOT NULL,
	nombre_transportista VARCHAR(255),
	telefono_transportista VARCHAR(50),
	capacidad_peso_max NUMERIC(12,3),
	capacidad_volumen_max NUMERIC(12,3),
	consumo_combustible_promedio NUMERIC(12,4),
	costo_por_km NUMERIC(12,4),
	disponible BOOLEAN DEFAULT TRUE,
	activo BOOLEAN DEFAULT TRUE,
	estado_id BIGINT REFERENCES estado_camion(id_estado)
);
CREATE INDEX IF NOT EXISTS idx_camiones_estado ON camiones(estado_id);

-- Solicitudes (primary application entity)
CREATE TABLE IF NOT EXISTS solicitudes (
	id_solicitud BIGSERIAL PRIMARY KEY,
	contenedor_id BIGINT REFERENCES contenedores(id_contenedor),
	deposito_origen_id BIGINT REFERENCES depositos(id_deposito),
	deposito_destino_id BIGINT REFERENCES depositos(id_deposito),
	latitud_origen NUMERIC(11,8),
	longitud_origen NUMERIC(11,8),
	latitud_destino NUMERIC(11,8),
	longitud_destino NUMERIC(11,8),
	distancia_total DOUBLE PRECISION,
	duracion_horas DOUBLE PRECISION,
	fecha_ingreso TIMESTAMP,
	fecha_retiro TIMESTAMP,
	id_estado BIGINT REFERENCES estado_solicitud(id_estado),
	costo_calculado NUMERIC(12,2),
	costo_final NUMERIC(12,2),
	tiempo_real NUMERIC(12,2),
	ruta_id BIGINT,
	tarifa_id BIGINT
);
CREATE INDEX IF NOT EXISTS idx_solicitudes_contenedor ON solicitudes(contenedor_id);
CREATE INDEX IF NOT EXISTS idx_solicitudes_deposito_origen ON solicitudes(deposito_origen_id);
CREATE INDEX IF NOT EXISTS idx_solicitudes_deposito_destino ON solicitudes(deposito_destino_id);
CREATE INDEX IF NOT EXISTS idx_solicitudes_estado ON solicitudes(id_estado);
CREATE INDEX IF NOT EXISTS idx_solicitudes_ruta ON solicitudes(ruta_id);

-- Rutas
CREATE TABLE IF NOT EXISTS rutas (
	id_ruta BIGSERIAL PRIMARY KEY,
	id_solicitud BIGINT,
	fecha_creacion TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
	opcion_seleccionada_id BIGINT,
	cantidad_tramos INTEGER,
	cantidad_depositos INTEGER
);
CREATE INDEX IF NOT EXISTS idx_rutas_solicitud ON rutas(id_solicitud);

-- Ruta opciones
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
CREATE INDEX IF NOT EXISTS idx_ruta_opciones_ruta ON ruta_opciones(ruta_id);

-- Tramos (segments of a route)
CREATE TABLE IF NOT EXISTS tramos (
	id_tramo BIGSERIAL PRIMARY KEY,
	ruta_id BIGINT,
	deposito_origen_id BIGINT REFERENCES depositos(id_deposito),
	deposito_destino_id BIGINT REFERENCES depositos(id_deposito),
	orden INTEGER,
	distancia DOUBLE PRECISION,
	duracion DOUBLE PRECISION,
	costo NUMERIC(12,2),
	costo_real NUMERIC(12,2),
	camion_id BIGINT REFERENCES camiones(id_camion),
	camion_dominio VARCHAR(100),
	estado_id BIGINT REFERENCES estado_tramo(id_estado),
	geometry TEXT
);
CREATE INDEX IF NOT EXISTS idx_tramos_ruta ON tramos(ruta_id);
CREATE INDEX IF NOT EXISTS idx_tramos_camion_dominio ON tramos(camion_dominio);
CREATE INDEX IF NOT EXISTS idx_tramos_deposito_origen ON tramos(deposito_origen_id);
CREATE INDEX IF NOT EXISTS idx_tramos_deposito_destino ON tramos(deposito_destino_id);
CREATE INDEX IF NOT EXISTS idx_tramos_estado ON tramos(estado_id);

-- Add missing FK constraints
DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rutas_opcion_seleccionada') THEN
		IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rutas' AND column_name='opcion_seleccionada_id')
			 AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='ruta_opciones') THEN
			EXECUTE 'ALTER TABLE rutas ADD CONSTRAINT fk_rutas_opcion_seleccionada FOREIGN KEY (opcion_seleccionada_id) REFERENCES ruta_opciones(id_ruta_opcion)';
		END IF;
	END IF;
END$$;

DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_solicitudes_ruta') THEN
		IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='solicitudes' AND column_name='ruta_id')
			 AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='rutas') THEN
			EXECUTE 'ALTER TABLE solicitudes ADD CONSTRAINT fk_solicitudes_ruta FOREIGN KEY (ruta_id) REFERENCES rutas(id_ruta)';
		END IF;
	END IF;
END$$;

DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ruta_opciones_ruta') THEN
		IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ruta_opciones' AND column_name='ruta_id')
			 AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='rutas') THEN
			EXECUTE 'ALTER TABLE ruta_opciones ADD CONSTRAINT fk_ruta_opciones_ruta FOREIGN KEY (ruta_id) REFERENCES rutas(id_ruta) ON DELETE CASCADE';
		END IF;
	END IF;
END$$;

DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ruta_opciones_solicitud') THEN
		IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ruta_opciones' AND column_name='solicitud_id')
			 AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='solicitudes') THEN
			EXECUTE 'ALTER TABLE ruta_opciones ADD CONSTRAINT fk_ruta_opciones_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes(id_solicitud) ON DELETE CASCADE';
		END IF;
	END IF;
END$$;

-- Additional indexes
CREATE INDEX IF NOT EXISTS idx_depositos_latlong ON depositos(latitud, longitud);

-- Ensure uniqueness in estado names for seed INSERTs with ON CONFLICT
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_solicitud_nombre ON estado_solicitud(LOWER(nombre));
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_contenedor_nombre ON estado_contenedor(LOWER(nombre));
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_camion_nombre ON estado_camion(LOWER(nombre));
CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_tramo_nombre ON estado_tramo(LOWER(nombre));

-- Unique index on camiones.dominio
DO $$
DECLARE
	dup_count INT;
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ux_camiones_dominio') THEN
		SELECT COUNT(*) INTO dup_count FROM (
			SELECT LOWER(dominio) AS d, COUNT(*) FROM camiones GROUP BY LOWER(dominio) HAVING COUNT(*) > 1
		) t;
		IF dup_count > 0 THEN
			RAISE WARNING 'Cannot create unique index ux_camiones_dominio: % duplicate dominio values found.', dup_count;
		ELSE
			EXECUTE 'CREATE UNIQUE INDEX ux_camiones_dominio ON camiones(LOWER(dominio))';
		END IF;
	END IF;
END$$;

-- Seed estado_* tables
INSERT INTO estado_solicitud (nombre) VALUES ('PENDIENTE'), ('PROGRAMADO'), ('EN_RUTA'), ('ENTREGADO'), ('FINALIZADO') ON CONFLICT DO NOTHING;
INSERT INTO estado_contenedor (nombre) VALUES ('LIBRE'), ('ASIGNADO'), ('EN_DEPOSITO'), ('EN_TRANSITO') ON CONFLICT DO NOTHING;
INSERT INTO estado_camion (nombre) VALUES ('DISPONIBLE'), ('ASIGNADO'), ('MANTENIMIENTO') ON CONFLICT DO NOTHING;
INSERT INTO estado_tramo (nombre) VALUES ('PENDIENTE'), ('EN_PROCESO'), ('FINALIZADO') ON CONFLICT DO NOTHING;

COMMIT;
