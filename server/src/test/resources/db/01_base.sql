-- 01_base.sql: database step 2 (V1__base_tables.sql). Tries to break every rule of the basic tables.
-- Fictional data only. Every line must start with "ok, "; any "FAIL" fails the run.
\set ON_ERROR_STOP on
\pset tuples_only on

INSERT INTO persons (id, shelter_id, preferred_name, approx_age, is_minor, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', 'Catherine Rodriguez', 80, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000a1', 'William Price', 40, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000a2', 'Mia Chen', 9, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-0000000000a2', 'Jon Doe', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-0000000000a2', 'Rosa Diaz', NULL, false, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-0000000000a2', 'José Pérez', 60, NULL, '00000000-0000-0000-0000-0000000000b1');

-- B1. Safe defaults
SELECT CASE WHEN visibility = 'ask' THEN 'ok, new person defaults to ask' ELSE 'FAIL: default visibility ' || visibility END
FROM persons WHERE id = '00000000-0000-0000-0000-000000000001';

-- B2. A re-sent form (same browser-made id) is ignored
INSERT INTO persons (id, shelter_id, preferred_name, entered_by)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', 'Catherine Rodriguez', '00000000-0000-0000-0000-0000000000b1')
ON CONFLICT (id) DO NOTHING;
SELECT CASE WHEN count(*) = 1 THEN 'ok, same id sent twice is stored once' ELSE 'FAIL: duplicate person' END
FROM persons WHERE id = '00000000-0000-0000-0000-000000000001';

-- B3. No ID columns exist at all
SELECT CASE WHEN count(*) = 0 THEN 'ok, no ID / birth date / SSN / immigration / photo columns'
            ELSE 'FAIL: found ' || string_agg(table_name || '.' || column_name, ', ') END
FROM information_schema.columns
WHERE table_schema = 'public'
  AND column_name ~* '(ssn|social_security|passport|license|national_id|legal_name|birth|dob|immigra|citizen|photo|image)';

-- B4. Matching columns are filled by the trigger (lower case, accents removed, every word, sounds)
SELECT CASE WHEN search_names = 'jose perez' AND name_words @> ARRAY['jose','perez'] AND cardinality(name_words) = 2 AND cardinality(name_sounds) = 2
            THEN 'ok, matching columns filled: ' || search_names ELSE 'FAIL: matching columns ' || search_names END
FROM persons WHERE id = '00000000-0000-0000-0000-000000000008';

-- B5. Who counts as a child
SELECT CASE WHEN treated_as_minor(p) = expected THEN 'ok, ' ELSE 'FAIL: ' END
       || preferred_name || ' (age ' || coalesce(approx_age::text, '?') || ', is_minor ' || coalesce(is_minor::text, '?') || ') child=' || treated_as_minor(p)
FROM persons p
JOIN (VALUES ('00000000-0000-0000-0000-000000000002'::uuid, false),   -- 40 -> adult
             ('00000000-0000-0000-0000-000000000003'::uuid, true),    -- 9 -> child
             ('00000000-0000-0000-0000-000000000005'::uuid, true),    -- unknown age -> child
             ('00000000-0000-0000-0000-000000000006'::uuid, false))   -- unknown age, marked adult -> adult
     AS e(id, expected) ON e.id = p.id
ORDER BY preferred_name;

-- B6. Contradictions and bad input are refused
SELECT expect_fail($$UPDATE persons SET is_minor = false WHERE id = '00000000-0000-0000-0000-000000000003'$$, 'mark a 9-year-old as adult');
SELECT expect_fail($$UPDATE persons SET is_minor = true WHERE id = '00000000-0000-0000-0000-000000000002'$$, 'mark a 40-year-old as minor');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', E'\t\n ', '00000000-0000-0000-0000-0000000000b1')$$, 'name of only tab/newline/space');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, visibility, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', 'public', '00000000-0000-0000-0000-0000000000b1')$$, 'unknown visibility');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, approx_age, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', 130, '00000000-0000-0000-0000-0000000000b1')$$, 'age 130');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, source, paper_ref, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', 'paper', '  ', '00000000-0000-0000-0000-0000000000b1')$$, 'paper with blank paper_ref');
SELECT expect_fail($$INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', '{""}', '00000000-0000-0000-0000-0000000000b1')$$, 'sought_names {""}');
SELECT expect_fail($$INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', ARRAY[NULL::text], '00000000-0000-0000-0000-0000000000b1')$$, 'sought_names {NULL}');
SELECT expect_fail($$INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-0O1I', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$, 'code with 0/O/1/I');
SELECT expect_fail($$INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-7K3P', '4821')$$, 'plain-text PIN');
SELECT expect_fail($$INSERT INTO staff (shelter_id, display_name, role, pin_hash) VALUES ('00000000-0000-0000-0000-0000000000a1', 'X', 'volunteer', '1234')$$, 'plain-text staff PIN');

-- B7. One working code per person; pre-printed slips (no person yet) are allowed
INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES ('HW-7K3P', '00000000-0000-0000-0000-000000000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-9Q2R', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
SELECT 'ok, allowed: a working code and a pre-printed slip';
SELECT expect_fail($$INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES ('HW-8M4Q', '00000000-0000-0000-0000-000000000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$, 'second working code');

-- B8. Every edit keeps a snapshot of the row before it
UPDATE persons SET approx_age = 81, updated_by = '00000000-0000-0000-0000-0000000000b2' WHERE id = '00000000-0000-0000-0000-000000000001';
SELECT CASE WHEN (before->>'approx_age') = '80' AND changed_by IS NOT NULL
            THEN 'ok, history kept the age before (80) and who changed it' ELSE 'FAIL: history ' || before::text END
FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000001';

-- B9. The app's database user
SET ROLE homeward_app;
INSERT INTO audit_log (staff_id, action, target) VALUES ('00000000-0000-0000-0000-0000000000b1', 'SEARCH', NULL);
SELECT 'ok, allowed: app adds an audit entry';
SELECT expect_fail($$UPDATE audit_log SET action = 'SEARCH'$$, 'app updates audit_log');
SELECT expect_fail($$DELETE FROM audit_log$$, 'app deletes audit_log');
SELECT expect_fail($$INSERT INTO audit_log (staff_id, action, at) VALUES ('00000000-0000-0000-0000-0000000000b1', 'SEARCH', '2020-01-01')$$, 'app sets the audit time');
SELECT expect_fail($$INSERT INTO audit_log (staff_id, action) VALUES ('00000000-0000-0000-0000-0000000000b1', 'PIN 4821 at Eastside')$$, 'free-text audit action');
SELECT expect_fail($$DELETE FROM persons$$, 'app deletes persons');
SELECT expect_fail($$INSERT INTO person_history (person_id, before) VALUES ('00000000-0000-0000-0000-000000000001', '{}')$$, 'app writes fake history');
CREATE TEMP TABLE fake (id uuid, updated_by uuid);
SELECT expect_fail($$CREATE TRIGGER t BEFORE UPDATE ON fake FOR EACH ROW EXECUTE FUNCTION public.persons_keep_history()$$, 'app attaches the history function to a temp table');
UPDATE persons SET search_names = 'fake name', updated_by = '00000000-0000-0000-0000-0000000000b1' WHERE id = '00000000-0000-0000-0000-000000000005';
SELECT CASE WHEN search_names = 'jon doe' THEN 'ok, search_names set by hand was overwritten by the trigger' ELSE 'FAIL: search_names ' || search_names END
FROM persons WHERE id = '00000000-0000-0000-0000-000000000005';
SELECT CASE WHEN count(*) = 1 THEN 'ok, the app''s edit still wrote history' ELSE 'FAIL: history rows ' || count(*) END
FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000005';
RESET ROLE;

-- B10. The retention user can delete people (history follows) but not the audit log
SET ROLE homeward_maint;
DELETE FROM persons WHERE id = '00000000-0000-0000-0000-000000000001';
SELECT 'ok, allowed: retention user deletes a person';
SELECT expect_fail($$DELETE FROM audit_log$$, 'retention user deletes audit_log');
RESET ROLE;
SELECT CASE WHEN count(*) = 0 THEN 'ok, history of the deleted person is gone too' ELSE 'FAIL: history left' END
FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000001';
SELECT CASE WHEN count(*) = 1 THEN 'ok, audit entries are kept' ELSE 'FAIL: audit rows ' || count(*) END FROM audit_log;
