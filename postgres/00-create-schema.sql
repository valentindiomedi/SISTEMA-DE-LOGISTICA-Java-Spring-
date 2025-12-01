

-- Schema creation script for TPI-Backend (PostgreSQL)
-- Creates tables, foreign keys and indexes following the DER and current entity models
-- Run on a fresh database as: psql -h <host> -U <user> -d <db> -f 00-create-schema.sql

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
	nombre VARCHAR(255) NOT NULL
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
	-- Consolidated initialization script for TPI-Backend
	-- This single file replaces init/01-init.sql and 01-migrate-to-der.sql to avoid runtime modifications

	-- If run on a machine that doesn't have the target database yet, create it (uses dblink extension)
	CREATE EXTENSION IF NOT EXISTS dblink;

	DO
	$$
	BEGIN
		 IF NOT EXISTS (
				SELECT FROM pg_database WHERE datname = 'tpi_backend_db'
		 ) THEN
				PERFORM dblink_exec('dbname=postgres', 'CREATE DATABASE tpi_backend_db');
		 END IF;
	END
	$$;

	-- Schema creation script for TPI-Backend (PostgreSQL)
	-- Creates tables, foreign keys and indexes following the DER and current entity models
	-- Run on a fresh database as: psql -h <host> -U <user> -d <db> -f 00-create-schema.sql

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
			nombre VARCHAR(255) NOT NULL
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
	-- Unicidad en dominio (requerida por la lógica)
	DO $$
	BEGIN
			IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ux_camiones_dominio') THEN
					EXECUTE 'CREATE UNIQUE INDEX ux_camiones_dominio ON camiones(LOWER(dominio))';
			END IF;
	END$$;
	CREATE INDEX IF NOT EXISTS idx_camiones_estado ON camiones(estado_id);

	-- Solicitudes
	CREATE TABLE IF NOT EXISTS solicitudes (
			id_solicitud BIGSERIAL PRIMARY KEY,
			-- FK to contenedores expressed by relationship (ManyToOne)
			contenedor_id BIGINT REFERENCES contenedores(id_contenedor),
			cliente_id BIGINT REFERENCES clientes(id_cliente),
			origen_lat NUMERIC(11,8),
			origen_long NUMERIC(11,8),
			destino_lat NUMERIC(11,8),
			destino_long NUMERIC(11,8),
			direccion_origen VARCHAR(1024),
			direccion_destino VARCHAR(1024),
			estado_solicitud_id BIGINT REFERENCES estado_solicitud(id_estado),
			costo_estimado NUMERIC(12,2),
			costo_final NUMERIC(12,2),
			tiempo_estimado NUMERIC(12,2),
			tiempo_real NUMERIC(12,2),
			ruta_id BIGINT,
			tarifa_id BIGINT REFERENCES tarifas(id_tarifa),
			fecha_creacion TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
			fecha_modificacion TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
	);
	CREATE INDEX IF NOT EXISTS idx_solicitudes_contenedor ON solicitudes(contenedor_id);
	CREATE INDEX IF NOT EXISTS idx_solicitudes_cliente ON solicitudes(cliente_id);
	CREATE INDEX IF NOT EXISTS idx_solicitudes_estado ON solicitudes(estado_solicitud_id);

	-- Rutas y opciones de ruta
	CREATE TABLE IF NOT EXISTS rutas (
			id_ruta BIGSERIAL PRIMARY KEY,
			id_solicitud BIGINT REFERENCES solicitudes(id_solicitud),
			fecha_creacion TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
			opcion_seleccionada_id BIGINT,
			cantidad_tramos INTEGER,
			cantidad_depositos INTEGER
	);
	CREATE INDEX IF NOT EXISTS idx_rutas_solicitud ON rutas(id_solicitud);

	CREATE TABLE IF NOT EXISTS ruta_opciones (
			id_ruta_opcion BIGSERIAL PRIMARY KEY,
			ruta_id BIGINT REFERENCES rutas(id_ruta) ON DELETE CASCADE,
			solicitud_id BIGINT,
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
	CREATE INDEX IF NOT EXISTS idx_ruta_opciones_solicitud ON ruta_opciones(solicitud_id);

	-- Tramos
	CREATE TABLE IF NOT EXISTS tramos (
			id_tramo BIGSERIAL PRIMARY KEY,
			ruta_id BIGINT REFERENCES rutas(id_ruta) ON DELETE CASCADE,
			camion_dominio VARCHAR(100),
			origen_deposito_id BIGINT REFERENCES depositos(id_deposito),
			destino_deposito_id BIGINT REFERENCES depositos(id_deposito),
			origen_lat NUMERIC(11,8),
			origen_long NUMERIC(11,8),
			destino_lat NUMERIC(11,8),
			destino_long NUMERIC(11,8),
			tipo_id BIGINT REFERENCES tipo_tramo(id_tipo),
			estado_id BIGINT REFERENCES estado_tramo(id_estado),
			costo_aproximado NUMERIC(12,2),
			costo_real NUMERIC(12,2),
			fecha_hora_inicio_estimada TIMESTAMP WITHOUT TIME ZONE,
			fecha_hora_fin_estimada TIMESTAMP WITHOUT TIME ZONE,
			fecha_hora_inicio_real TIMESTAMP WITHOUT TIME ZONE,
			fecha_hora_fin_real TIMESTAMP WITHOUT TIME ZONE,
			orden INTEGER,
			distancia DOUBLE PRECISION,
			duracion_horas DOUBLE PRECISION
	);
	CREATE INDEX IF NOT EXISTS idx_tramos_ruta ON tramos(ruta_id);
	CREATE INDEX IF NOT EXISTS idx_tramos_origen_deposito ON tramos(origen_deposito_id);
	CREATE INDEX IF NOT EXISTS idx_tramos_destino_deposito ON tramos(destino_deposito_id);
	CREATE INDEX IF NOT EXISTS idx_tramos_camion_dominio ON tramos(camion_dominio);

	-- Añadir FK entre tablas que se crearon en orden diferente
	-- (rutas fue creada antes de ruta_opciones y solicitudes fue creada antes de rutas)
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

	-- (Opcional) índices para búsquedas frecuentes
	CREATE INDEX IF NOT EXISTS idx_depositos_latlong ON depositos(latitud, longitud);
	CREATE INDEX IF NOT EXISTS idx_solicitudes_ruta ON solicitudes(ruta_id);

	-- Asegurar unicidad en nombres de estados para que los seed INSERT ... ON CONFLICT funcionen
	CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_solicitud_nombre ON estado_solicitud(LOWER(nombre));
	CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_contenedor_nombre ON estado_contenedor(LOWER(nombre));
	CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_camion_nombre ON estado_camion(LOWER(nombre));
	CREATE UNIQUE INDEX IF NOT EXISTS ux_estado_tramo_nombre ON estado_tramo(LOWER(nombre));

	-- Data seed for common estados (optional, helpful for tests)
	INSERT INTO estado_solicitud (nombre) VALUES ('PENDIENTE'), ('PROGRAMADO'), ('EN_RUTA'), ('ENTREGADO'), ('FINALIZADO') ON CONFLICT DO NOTHING;
	INSERT INTO estado_contenedor (nombre) VALUES ('LIBRE'), ('ASIGNADO'), ('EN_DEPOSITO'), ('EN_TRANSITO') ON CONFLICT DO NOTHING;
	INSERT INTO estado_camion (nombre) VALUES ('DISPONIBLE'), ('ASIGNADO'), ('MANTENIMIENTO') ON CONFLICT DO NOTHING;
	INSERT INTO estado_tramo (nombre) VALUES ('PENDIENTE'), ('EN_PROCESO'), ('FINALIZADO') ON CONFLICT DO NOTHING;

	COMMIT;

	-- Additional idempotent migration blocks (from 01-migrate-to-der) to ensure older DBs get missing columns/indexes
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

	-- Ensure indexes used by application exist
	CREATE INDEX IF NOT EXISTS idx_rutas_solicitud ON rutas(id_solicitud);
	CREATE INDEX IF NOT EXISTS idx_ruta_opciones_ruta ON ruta_opciones(ruta_id);
	CREATE INDEX IF NOT EXISTS idx_tramos_ruta ON tramos(ruta_id);
	CREATE INDEX IF NOT EXISTS idx_tramos_camion_dominio ON tramos(camion_dominio);
	CREATE INDEX IF NOT EXISTS idx_solicitudes_ruta ON solicitudes(ruta_id);

	-- Unique index on camiones.dominio: check duplicates first
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

	-- Ensure fk constraints exist (safe idempotent blocks)
	DO $$
	BEGIN
		IF NOT EXISTS (
			SELECT 1 FROM pg_constraint WHERE conname = 'fk_rutas_opcion_seleccionada'
		) THEN
			IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rutas' AND column_name='opcion_seleccionada_id')
				 AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='ruta_opciones') THEN
				EXECUTE 'ALTER TABLE rutas ADD CONSTRAINT fk_rutas_opcion_seleccionada FOREIGN KEY (opcion_seleccionada_id) REFERENCES ruta_opciones(id_ruta_opcion)';
			END IF;
		END IF;
	END$$;

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

	-- Seed estado_* tables (safe because unique indexes exist)
	INSERT INTO estado_solicitud (nombre) VALUES ('PENDIENTE'), ('PROGRAMADO'), ('EN_RUTA'), ('ENTREGADO'), ('FINALIZADO') ON CONFLICT DO NOTHING;
	INSERT INTO estado_contenedor (nombre) VALUES ('LIBRE'), ('ASIGNADO'), ('EN_DEPOSITO'), ('EN_TRANSITO') ON CONFLICT DO NOTHING;
	INSERT INTO estado_camion (nombre) VALUES ('DISPONIBLE'), ('ASIGNADO'), ('MANTENIMIENTO') ON CONFLICT DO NOTHING;
	INSERT INTO estado_tramo (nombre) VALUES ('PENDIENTE'), ('EN_PROCESO'), ('FINALIZADO') ON CONFLICT DO NOTHING;

	-- Useful run commands:
	-- psql -h localhost -U postgres -d tpi -f 00-create-schema.sql
	-- After running, verify tables: \dt

