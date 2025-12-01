-- Script para insertar 25 depósitos distribuidos por toda Argentina
-- Incluye ciudades principales de todas las regiones del país

-- Primero insertamos las ciudades (con nombre estandarizado)
INSERT INTO ciudad (nombre) VALUES 
('Buenos Aires'),
('Córdoba'),
('Rosario'),
('Mendoza'),
('San Miguel De Tucumán'),
('La Plata'),
('Mar Del Plata'),
('Salta'),
('Santa Fe'),
('San Juan'),
('Resistencia'),
('Neuquén'),
('Posadas'),
('Bahía Blanca'),
('Paraná'),
('Santiago Del Estero'),
('Corrientes'),
('San Salvador De Jujuy'),
('Río Cuarto'),
('Comodoro Rivadavia'),
('San Luis'),
('Formosa'),
('La Rioja'),
('Catamarca'),
('Río Gallegos')
ON CONFLICT (nombre) DO NOTHING;

-- Insertamos los 25 depósitos distribuidos por Argentina
INSERT INTO depositos (nombre, direccion, latitud, longitud, id_ciudad, costo_estadia_diario) VALUES
-- Región Metropolitana de Buenos Aires (3 depósitos)
('Depósito Central CABA', 'Av. Córdoba 1500, CABA', -34.5989, -58.3897, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Buenos Aires'), 800.00),
('Depósito Zona Norte', 'Panamericana Km 25, Buenos Aires', -34.4925, -58.5519, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Buenos Aires'), 750.00),
('Depósito La Plata', 'Calle 44 y 17, La Plata', -34.9205, -57.9536, (SELECT id_ciudad FROM ciudad WHERE nombre = 'La Plata'), 650.00),

-- Región Pampeana (4 depósitos)
('Depósito Rosario Centro', 'Av. Belgrano 2500, Rosario', -32.9442, -60.6505, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Rosario'), 700.00),
('Depósito Mar del Plata', 'Ruta 2 Km 398, Mar del Plata', -38.0055, -57.5426, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Mar Del Plata'), 650.00),
('Depósito Santa Fe', 'Bv. Gálvez 1800, Santa Fe', -31.6333, -60.7000, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Santa Fe'), 600.00),
('Depósito Bahía Blanca', 'Av. Alem 3000, Bahía Blanca', -38.7183, -62.2663, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Bahía Blanca'), 550.00),

-- Región Centro (3 depósitos)
('Depósito Córdoba Capital', 'Av. Circunvalación Km 8, Córdoba', -31.4201, -64.1888, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Córdoba'), 720.00),
('Depósito Río Cuarto', 'Ruta 8 Km 601, Río Cuarto', -33.1231, -64.3499, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Río Cuarto'), 580.00),
('Depósito Paraná', 'Av. Ramírez 2200, Paraná', -31.7413, -60.5115, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Paraná'), 600.00),

-- Región Cuyo (3 depósitos)
('Depósito Mendoza', 'Acceso Este Km 5, Mendoza', -32.8895, -68.8458, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Mendoza'), 680.00),
('Depósito San Juan', 'Av. Libertador 1500, San Juan', -31.5375, -68.5364, (SELECT id_ciudad FROM ciudad WHERE nombre = 'San Juan'), 620.00),
('Depósito San Luis', 'Av. Lafinur 2000, San Luis', -33.3017, -66.3378, (SELECT id_ciudad FROM ciudad WHERE nombre = 'San Luis'), 550.00),

-- Región NOA - Noroeste (4 depósitos)
('Depósito Tucumán', 'Av. Aconquija 1800, Tucumán', -26.8083, -65.2176, (SELECT id_ciudad FROM ciudad WHERE nombre = 'San Miguel De Tucumán'), 640.00),
('Depósito Salta', 'Ruta 51 Km 6, Salta', -24.7859, -65.4117, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Salta'), 660.00),
('Depósito Jujuy', 'Av. Bolivia 1200, Jujuy', -24.1858, -65.2995, (SELECT id_ciudad FROM ciudad WHERE nombre = 'San Salvador De Jujuy'), 600.00),
('Depósito Santiago del Estero', 'Av. Belgrano Sur 2500, Santiago', -27.7951, -64.2615, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Santiago Del Estero'), 580.00),

-- Región NEA - Noreste (3 depósitos)
('Depósito Resistencia', 'Av. Sarmiento 1500, Resistencia', -27.4514, -58.9867, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Resistencia'), 590.00),
('Depósito Corrientes', 'Ruta 12 Km 1028, Corrientes', -27.4692, -58.8306, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Corrientes'), 580.00),
('Depósito Posadas', 'Av. Quaranta 3000, Posadas', -27.3671, -55.8961, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Posadas'), 610.00),

-- Región Patagonia (5 depósitos)
('Depósito Neuquén', 'Ruta 22 Km 1230, Neuquén', -38.9516, -68.0591, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Neuquén'), 680.00),
('Depósito Comodoro Rivadavia', 'Ruta 3 Km 1390, Comodoro', -45.8653, -67.4810, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Comodoro Rivadavia'), 720.00),
('Depósito Río Gallegos', 'Av. San Martín 2500, Río Gallegos', -51.6226, -69.2181, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Río Gallegos'), 800.00),
('Depósito La Rioja', 'Av. Circunvalación Este 1500, La Rioja', -29.4131, -66.8558, (SELECT id_ciudad FROM ciudad WHERE nombre = 'La Rioja'), 570.00),
('Depósito Catamarca', 'Av. Belgrano 800, Catamarca', -28.4696, -65.7795, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Catamarca'), 560.00),

-- Depósito adicional (Formosa)
('Depósito Formosa', 'Av. 25 de Mayo 1200, Formosa', -26.1775, -58.1781, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Formosa'), 590.00);

-- Verificación: mostrar cantidad de depósitos insertados
SELECT COUNT(*) as total_depositos FROM depositos;
SELECT COUNT(*) as total_ciudades FROM ciudad;
