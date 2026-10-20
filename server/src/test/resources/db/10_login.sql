-- 10_login.sql: backend step B0 (V2__login_and_schema_version.sql). Every line asserts.
-- (Inline CASE instead of assert_true: that helper arrives with the step 3 branch.)
\set ON_ERROR_STOP on
\pset tuples_only on

SELECT expect_fail($$INSERT INTO staff (shelter_id, display_name, role, pin_hash) VALUES ('00000000-0000-0000-0000-0000000000a1', 'vol ana', 'volunteer', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$,
                   'a second "Vol Ana" at the same shelter (different case)');
INSERT INTO staff (shelter_id, display_name, role, pin_hash)
VALUES ('00000000-0000-0000-0000-0000000000a2', 'Vol Ana', 'volunteer', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
SELECT CASE WHEN count(*) = 2 THEN 'ok, ' ELSE 'FAIL: ' END || 'the same name at another shelter is allowed' FROM staff WHERE lower(display_name) = 'vol ana';
SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'ok, ' ELSE 'FAIL: ' END || 'no Flyway table in a plain psql run, so V2 skipped the grant without failing';
