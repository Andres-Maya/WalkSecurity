-- Crea el usuario y la base de datos de WalkSecurity en el PostgreSQL instalado en tu PC.
-- Se ejecuta UNA sola vez con el usuario administrador (el script scripts/dev-up.ps1 lo hace por ti):
--   psql -h localhost -U postgres -f backend/db/crear-bd-local.sql
-- Es idempotente: si ya existen, no hace nada.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'walksecurity') THEN
        CREATE ROLE walksecurity WITH LOGIN PASSWORD 'walksecurity';
    END IF;
END
$$;

SELECT 'CREATE DATABASE walksecurity OWNER walksecurity'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'walksecurity')\gexec
