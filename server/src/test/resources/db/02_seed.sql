-- 02_seed.sql: the fictional demo seed (db/seed/demo_seed.sql) was applied twice by db-test.sh.
\set ON_ERROR_STOP on
\pset tuples_only on
SELECT assert_true(count(*) = 3, 'demo seed: 3 shelters, stored once despite running twice') FROM shelters WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) = 6, 'demo seed: 6 staff, stored once despite running twice') FROM staff WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) FILTER (WHERE role = 'volunteer') = 3 AND count(*) FILTER (WHERE role = 'lead') = 3 AND count(*) FILTER (WHERE role = 'coordinator') = 0,
                   'demo seed: 3 volunteers, 3 leads and NO coordinator (the public PIN must never open a coordinator account)') FROM staff WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(DISTINCT pin_hash) = 1, 'demo seed: all demo accounts share the one demo PIN hash') FROM staff WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) = 0, 'demo seed: no people with staff/shelter ids') FROM persons WHERE id::text LIKE 'd0000000-%';
SELECT assert_true(count(*) = 200, 'demo seed: 200 fictional people, stored once despite running twice') FROM persons WHERE id::text LIKE 'd1000000-%';
SELECT assert_true(count(DISTINCT shelter_id) = 3 AND count(*) FILTER (WHERE approx_age IS NULL) > 0
                   AND count(DISTINCT visibility) = 3 AND count(*) FILTER (WHERE visibility = 'ask') > 100,
                   'demo seed: people at all 3 shelters, some ages unknown, all 3 privacy choices, mostly "ask"')
FROM persons WHERE id::text LIKE 'd1000000-%';
