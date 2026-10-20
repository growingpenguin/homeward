-- 02_seed.sql: the fictional demo seed (db/seed/demo_seed.sql) was applied twice by db-test.sh.
\set ON_ERROR_STOP on
\pset tuples_only on
SELECT assert_true(count(*) = 3, 'demo seed: 3 shelters, stored once despite running twice') FROM shelters WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) = 7, 'demo seed: 7 staff, stored once despite running twice') FROM staff WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) FILTER (WHERE role = 'volunteer') = 3 AND count(*) FILTER (WHERE role = 'lead') = 3 AND count(*) FILTER (WHERE role = 'coordinator') = 1,
                   'demo seed: 3 volunteers, 3 leads, 1 coordinator') FROM staff WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(bool_and(pin_hash ~ '^\$2[aby]\$'), 'demo seed: every staff PIN is a BCrypt hash, never the PIN') FROM staff WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) = 0, 'demo seed: no people (people come from registration and the matching seed)') FROM persons WHERE id::text LIKE 'd0000000-%';
