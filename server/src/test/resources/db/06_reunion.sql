-- Step 6: one complete fictional reunion, exercising the app role throughout.
-- Only scripts/db-test.sh's disposable database: never run the fixture files on Neon.
\set ON_ERROR_STOP on
\pset tuples_only on
BEGIN;

-- The existing fixtures have a volunteer only at North High. Give City its own operator.
INSERT INTO staff (id, shelter_id, display_name, role, pin_hash)
VALUES ('00000000-0000-0000-0000-0000000006b1', '00000000-0000-0000-0000-0000000000a2',
        'Vol June', 'volunteer', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
SET LOCAL ROLE homeward_app;

INSERT INTO persons (id, shelter_id, preferred_name, approx_age, home_area, phone, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-0000000000a1', 'Sarah Kim', 34,
   'Private test street', '555-0198', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-0000000000a2', 'Mina Kim', 31,
   NULL, NULL, '00000000-0000-0000-0000-0000000006b1');
SELECT assert_true((SELECT visibility = 'ask' AND NOT treated_as_minor(p) FROM persons p
                    WHERE id = '00000000-0000-0000-0000-000000000601'), 'Sarah starts as an adult who must be asked');
INSERT INTO searches (id, shelter_id, searcher_person_id, sought_names, sought_age_min, sought_age_max, entered_by)
VALUES ('00000000-0000-0000-0000-0000000006c1', '00000000-0000-0000-0000-0000000000a2',
        '00000000-0000-0000-0000-000000000602', '{Sara Kim}', 30, 40, '00000000-0000-0000-0000-0000000006b1');
INSERT INTO audit_log (staff_id, action, target)
VALUES ('00000000-0000-0000-0000-0000000006b1', 'SEARCH', '00000000-0000-0000-0000-0000000006c1');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Sara Kim', 30, 40)
                          WHERE person_id = '00000000-0000-0000-0000-000000000601' AND age_close),
                   'Mina finds Sarah across shelters despite the misspelling');
SELECT assert_true((SELECT array_agg(key ORDER BY key) FROM search_candidates c,
                    LATERAL jsonb_object_keys(to_jsonb(c)) key WHERE id = '00000000-0000-0000-0000-000000000601') =
                   ARRAY['age_is_estimate','approx_age','id','minor','name_sounds','name_words','other_names','preferred_name','search_names'],
                   'the actual matched row has exactly the location-free columns');
INSERT INTO match_candidates (search_id, person_id, score, reasons)
SELECT '00000000-0000-0000-0000-0000000006c1', person_id, 0.9, '["name sounds alike", "age fits"]'::jsonb
FROM find_candidates('Sara Kim', 30, 40) WHERE person_id = '00000000-0000-0000-0000-000000000601';
INSERT INTO contact_requests (id, search_id, person_id, message)
SELECT '00000000-0000-0000-0000-0000000006d1', search_id, person_id, 'Sarah, your sister Mina is looking for you.'
FROM match_candidates WHERE search_id = '00000000-0000-0000-0000-0000000006c1';
SELECT assert_true((SELECT state = 'pending' AND contact_plan IS NULL FROM contact_requests
                    WHERE id = '00000000-0000-0000-0000-0000000006d1'), 'a match and request alone disclose no contact plan');

UPDATE contact_requests SET state = 'connected', contact_plan = 'Call the North High desk and ask for Sarah', decided_at = now()
WHERE id = '00000000-0000-0000-0000-0000000006d1';
SELECT assert_true((SELECT state = 'connected' AND contact_plan = 'Call the North High desk and ask for Sarah'
                   FROM contact_requests WHERE id = '00000000-0000-0000-0000-0000000006d1'),
                   'Sarah consents and the agreed plan is stored');

-- A later protection revokes the existing connection, not just future matching.
INSERT INTO safety_flags (person_id, kind, created_by)
VALUES ('00000000-0000-0000-0000-000000000601', 'do_not_disclose', '00000000-0000-0000-0000-0000000000b2');
SELECT assert_true((SELECT state = 'blocked' FROM contact_requests WHERE id = '00000000-0000-0000-0000-0000000006d1'),
                   'protection revokes the completed reunion');
SELECT assert_true(NOT EXISTS (SELECT 1 FROM find_candidates('Sarah Kim', 30, 40)
                               WHERE person_id = '00000000-0000-0000-0000-000000000601'), 'protected Sarah disappears from matching');
DO $$ BEGIN
  UPDATE contact_requests SET state = 'connected' WHERE id = '00000000-0000-0000-0000-0000000006d1';
  RAISE EXCEPTION 'FAIL: reconnecting protected Sarah was allowed';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'ok, protected reconnection refused with SQLSTATE 23514'; END $$;
UPDATE safety_flags SET cleared_at = now(), cleared_by = '00000000-0000-0000-0000-0000000000b2'
WHERE person_id = '00000000-0000-0000-0000-000000000601';
SELECT assert_true((SELECT state = 'blocked' FROM contact_requests WHERE id = '00000000-0000-0000-0000-0000000006d1'),
                   'lifting protection never restores the old consent');

UPDATE persons SET visibility = 'hidden', updated_by = '00000000-0000-0000-0000-0000000000b1'
WHERE id = '00000000-0000-0000-0000-000000000601';
SELECT assert_true(NOT EXISTS (SELECT 1 FROM find_candidates('Sarah Kim', 30, 40)
                               WHERE person_id = '00000000-0000-0000-0000-000000000601'), 'hidden Sarah cannot be found even by exact name');

-- Correct Sarah's age, then retry the same pair: a child requires the lead path.
UPDATE persons SET visibility = 'ask', approx_age = 14, updated_by = '00000000-0000-0000-0000-0000000000b1'
WHERE id = '00000000-0000-0000-0000-000000000601';
UPDATE contact_requests SET state = 'pending', contact_plan = NULL WHERE id = '00000000-0000-0000-0000-0000000006d1';
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Sarah Kim', 10, 18)
                          WHERE person_id = '00000000-0000-0000-0000-000000000601' AND minor), 'a child may match but is marked for the lead');
DO $$ BEGIN
  UPDATE contact_requests SET state = 'connected', contact_plan = 'Call North High'
  WHERE id = '00000000-0000-0000-0000-0000000006d1';
  RAISE EXCEPTION 'FAIL: connecting a child without lead approval was allowed';
EXCEPTION WHEN check_violation THEN RAISE NOTICE 'ok, child connection refused with SQLSTATE 23514'; END $$;
SELECT assert_true((SELECT state = 'pending' AND contact_plan IS NULL FROM contact_requests
                    WHERE id = '00000000-0000-0000-0000-0000000006d1'), 'refused child release leaves no revealing state or plan');
UPDATE contact_requests SET state = 'connected', contact_plan = 'Lead Sam arranges the reunion at the North High desk',
  approved_by = '00000000-0000-0000-0000-0000000000b2', guardian_checked = true, note = 'Guardian checked against the school list'
WHERE id = '00000000-0000-0000-0000-0000000006d1';
SELECT assert_true((SELECT state = 'connected' AND guardian_checked FROM contact_requests
                    WHERE id = '00000000-0000-0000-0000-0000000006d1'), 'the child reunion succeeds only with lead approval');

-- SQL proves storage/guard behavior; the API test proves searcher replies and automatic audit writes.
ROLLBACK;
