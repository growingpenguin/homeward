-- 00_helpers.sql: shared test helpers and fictional seed data. Runs before every step test.
\set ON_ERROR_STOP on

-- expect_fail(sql, label): passes ("ok, refused") only if the statement raises an error.
CREATE FUNCTION expect_fail(sql text, label text) RETURNS text LANGUAGE plpgsql AS $$
BEGIN
  EXECUTE sql;
  RETURN 'FAIL (no error): ' || label;
EXCEPTION WHEN others THEN
  RETURN 'ok, refused: ' || label || ' -> ' || SQLERRM;
END $$;
GRANT EXECUTE ON FUNCTION expect_fail(text, text) TO homeward_app, homeward_maint;

-- assert_true(condition, label): 'ok, <label>' when true, 'FAIL: <label>' when false or NULL.
-- Every step test asserts its results; nothing is print-only.
CREATE FUNCTION assert_true(ok boolean, label text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN ok THEN 'ok, ' || label ELSE 'FAIL: ' || label END
$$;
GRANT EXECUTE ON FUNCTION assert_true(boolean, text) TO homeward_app, homeward_maint;

-- Fictional shelters and staff (BCrypt hash of a throwaway PIN).
INSERT INTO shelters (id, name) VALUES
  ('00000000-0000-0000-0000-0000000000a1', 'North High School'),
  ('00000000-0000-0000-0000-0000000000a2', 'City Community Center');
INSERT INTO staff (id, shelter_id, display_name, role, pin_hash) VALUES
  ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', 'Vol Ana', 'volunteer', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
  ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1', 'Lead Sam', 'lead', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
SELECT 'ok, helpers and fictional shelters/staff loaded';
