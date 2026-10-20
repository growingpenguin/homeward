-- V2__login_and_schema_version.sql (backend step B0): what the running app needs before login exists.

-- The app checks the schema version at startup and refuses to run on an older schema,
-- so it may read Flyway's history table (read-only). The table only exists when Flyway runs
-- the migrations; a plain psql test run (scripts/db-test.sh) has none, so the grant is skipped there.
DO $$ BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        GRANT SELECT ON flyway_schema_history TO homeward_app;
    END IF;
END $$;

-- A volunteer logs in with shelter + name + PIN, so a name must be unique within a shelter
-- ("Ana" and "ana" are the same person to a tired volunteer).
CREATE UNIQUE INDEX staff_login_name ON staff (shelter_id, lower(display_name));
