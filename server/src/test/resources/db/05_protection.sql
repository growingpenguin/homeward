-- 05_protection.sql: database step 5 (V4__consent_and_protection.sql). Two people are only connected
-- when it's allowed: children only with a lead, never past a safety flag, and a "yes" can't be moved.
-- Ported from draft/full_schema_checks.sql (T10-T13, T18, T20-T22) with new ids. The app's actions run
-- as homeward_app, like the backend. Fictional data only; every line asserts.
\set ON_ERROR_STOP on
\pset tuples_only on

-- Shelters a1 (North High) and a2 (City Community Center); Vol Ana (b1), Lead Sam (b2).
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, visibility, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-0000000000a1', 'Nora Quinn', 80, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-0000000000a1', 'Felix Varga', 40, 'findable', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000503', '00000000-0000-0000-0000-0000000000a2', 'Ada Mbeki', 9, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000504', '00000000-0000-0000-0000-0000000000a2', 'Hugo Ferrer', 45, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000505', '00000000-0000-0000-0000-0000000000a2', 'Kofi Mensah', 10, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000506', '00000000-0000-0000-0000-0000000000a1', 'Abena Mensah', 41, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000507', '00000000-0000-0000-0000-0000000000a1', 'Ines Calder', 35, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000508', '00000000-0000-0000-0000-0000000000a2', 'Paulo Reis', 60, 'ask', '00000000-0000-0000-0000-0000000000b1');
INSERT INTO searches (id, shelter_id, searcher_person_id, sought_names, entered_by) VALUES
  ('00000000-0000-0000-0000-0000000005c1', '00000000-0000-0000-0000-0000000000a2', NULL, '{Ada Mbeki}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000005c2', '00000000-0000-0000-0000-0000000000a2', NULL, '{Felix Varga}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000005c3', '00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000504', '{Nora Quinn}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000005c4', '00000000-0000-0000-0000-0000000000a2', NULL, '{Paulo Reis}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000005c5', '00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000505', '{Abena Mensah}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000005c7', '00000000-0000-0000-0000-0000000000a1', NULL, '{Abena Mensah}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000005c8', '00000000-0000-0000-0000-0000000000a2', NULL, '{Ines Calder}', '00000000-0000-0000-0000-0000000000b1');

SET ROLE homeward_app;

-- P1. A child is only connected with a lead, a guardian check and a note
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000005c1', '00000000-0000-0000-0000-000000000503', 'connected', 'Call City desk')$$, 'child, no lead');
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note) VALUES ('00000000-0000-0000-0000-0000000005c1', '00000000-0000-0000-0000-000000000503', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b1', true, 'checked')$$, 'child, approver is a volunteer');
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note) VALUES ('00000000-0000-0000-0000-0000000005c1', '00000000-0000-0000-0000-000000000503', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b2', false, 'checked')$$, 'child, guardian not checked');
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note) VALUES ('00000000-0000-0000-0000-0000000005c1', '00000000-0000-0000-0000-000000000503', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b2', true, E' \n')$$, 'child, whitespace note');
INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note)
VALUES ('00000000-0000-0000-0000-0000000005c1', '00000000-0000-0000-0000-000000000503', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b2', true, 'Ada recognised her dad; school list matches');
SELECT 'ok, allowed: child with lead + guardian check + note';

SELECT expect_fail($$UPDATE staff SET role = 'lead' WHERE id = '00000000-0000-0000-0000-0000000000b1'$$, 'app promotes a volunteer to lead (to fake a sign-off)');
SELECT expect_fail($$INSERT INTO staff (shelter_id, display_name, role, pin_hash) VALUES ('00000000-0000-0000-0000-0000000000a1', 'Fake Lead', 'lead', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$, 'app creates a lead account');

-- P2. "Connected" always says how to meet; a request can't be moved
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000005c2', '00000000-0000-0000-0000-000000000502', 'connected', '   ')$$, 'connected with a blank contact plan');
SELECT expect_fail($$UPDATE contact_requests SET person_id = '00000000-0000-0000-0000-000000000507' WHERE person_id = '00000000-0000-0000-0000-000000000503'$$, 'move a connected request to another person');
SELECT expect_fail($$UPDATE contact_requests SET search_id = '00000000-0000-0000-0000-0000000005c2' WHERE person_id = '00000000-0000-0000-0000-000000000503'$$, 'move a connected request to another search');
SELECT expect_fail($$DELETE FROM contact_requests$$, 'app deletes contact requests');

-- P3. Safety flags beat everything, including "findable" and an earlier "Connect"
INSERT INTO contact_requests (search_id, person_id) VALUES ('00000000-0000-0000-0000-0000000005c2', '00000000-0000-0000-0000-000000000502');
SELECT assert_true(state = 'pending', 'a new request starts as pending') FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000005c2';
INSERT INTO safety_flags (person_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000502', 'do_not_disclose', '00000000-0000-0000-0000-0000000000b2');
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'x' WHERE search_id = '00000000-0000-0000-0000-0000000005c2'$$, 'connect a protected person who chose "findable"');
SELECT expect_fail($$UPDATE contact_requests SET state = 'safe_only' WHERE search_id = '00000000-0000-0000-0000-0000000005c2'$$, '"safe only" for a protected person');
-- flag added AFTER "Connect": Ada's lead-approved request is blocked at once
INSERT INTO safety_flags (person_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000503', 'custody_hold', '00000000-0000-0000-0000-0000000000b2');
SELECT assert_true(state = 'blocked', 'custody_hold added after Connect switches the request to blocked (got ' || state || ')')
FROM contact_requests WHERE person_id = '00000000-0000-0000-0000-000000000503';
-- flag on the SEARCHER (Hugo is suspected): his search for Nora can't connect
INSERT INTO contact_requests (search_id, person_id) VALUES ('00000000-0000-0000-0000-0000000005c3', '00000000-0000-0000-0000-000000000501');
INSERT INTO safety_flags (person_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000504', 'suspected_abuser', '00000000-0000-0000-0000-0000000000b2');
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'x' WHERE search_id = '00000000-0000-0000-0000-0000000005c3'$$, 'connect when the searcher is flagged');
-- flag on the SEARCH itself, and a temp table named safety_flags can't hide it
INSERT INTO contact_requests (search_id, person_id) VALUES ('00000000-0000-0000-0000-0000000005c4', '00000000-0000-0000-0000-000000000508');
INSERT INTO safety_flags (search_id, kind, created_by) VALUES ('00000000-0000-0000-0000-0000000005c4', 'suspected_abuser', '00000000-0000-0000-0000-0000000000b2');
CREATE TEMP TABLE safety_flags (LIKE public.safety_flags);
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'x' WHERE search_id = '00000000-0000-0000-0000-0000000005c4'$$, 'connect while a temp table "safety_flags" hides the real one');
DROP TABLE pg_temp.safety_flags;
SELECT expect_fail($$INSERT INTO safety_flags (search_id, kind, created_by) VALUES ('00000000-0000-0000-0000-0000000005c1', 'do_not_disclose', '00000000-0000-0000-0000-0000000000b2')$$, 'do_not_disclose on a search (only a person can be protected)');

-- P4. A child who is SEARCHING also needs a lead ("Connect" reveals both sides)
INSERT INTO contact_requests (search_id, person_id) VALUES ('00000000-0000-0000-0000-0000000005c5', '00000000-0000-0000-0000-000000000506');
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'Call the North High desk' WHERE search_id = '00000000-0000-0000-0000-0000000005c5'$$, 'connect a child searcher (Kofi, 10) with an adult, no lead');
UPDATE contact_requests SET state = 'connected', contact_plan = 'Call the North High desk', approved_by = '00000000-0000-0000-0000-0000000000b2',
       guardian_checked = true, note = 'Abena is his mother; school list matches'
WHERE search_id = '00000000-0000-0000-0000-0000000005c5';
SELECT 'ok, allowed: child searcher with lead + guardian check + note';

-- P5. The person searching can't be swapped once it matters
SELECT expect_fail($$UPDATE searches SET searcher_person_id = '00000000-0000-0000-0000-000000000507' WHERE id = '00000000-0000-0000-0000-0000000005c5'$$, 'swap the searcher on a search that already has one');
INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000005c7', '00000000-0000-0000-0000-000000000506', 'connected', 'Call the North High desk');
SELECT expect_fail($$UPDATE searches SET searcher_person_id = '00000000-0000-0000-0000-000000000505' WHERE id = '00000000-0000-0000-0000-0000000005c7'$$, 'attach a child searcher to a search that already got a "Connect"');
UPDATE searches SET searcher_person_id = '00000000-0000-0000-0000-000000000507' WHERE id = '00000000-0000-0000-0000-0000000005c8';
SELECT 'ok, allowed: link the searcher to a new search with no answer yet';

-- P6. Correcting an age to a child re-checks earlier "Connect"s
INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES ('00000000-0000-0000-0000-0000000005c9', '00000000-0000-0000-0000-0000000000a2', '{Ines Calder}', '00000000-0000-0000-0000-0000000000b1');
INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000005c9', '00000000-0000-0000-0000-000000000507', 'connected', 'Call the North High desk');
UPDATE persons SET approx_age = 14, updated_by = '00000000-0000-0000-0000-0000000000b1' WHERE id = '00000000-0000-0000-0000-000000000507';
SELECT assert_true(state = 'pending', 'correcting Ines to 14 sends the unapproved Connect back to pending (got ' || state || ')')
FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000005c9';
UPDATE persons SET approx_age = 15, updated_by = '00000000-0000-0000-0000-0000000000b1' WHERE id = '00000000-0000-0000-0000-000000000506';
SELECT assert_true(state = 'connected', 'a lead-approved Connect stays connected after correcting Abena to 15 (got ' || state || ')')
FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000005c5';
SELECT assert_true(state = 'pending', 'an unapproved Connect involving Abena (now 15) went back to pending (got ' || state || ')')
FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000005c7';
RESET ROLE;
