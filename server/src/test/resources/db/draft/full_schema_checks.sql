-- full_schema_checks.sql: the break-it checks for full_schema_draft.sql (fictional data only).
-- Run with: scripts/db-test.sh --draft
\set ON_ERROR_STOP on
\set H '\'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy\''
-- Fictional seed data only
INSERT INTO shelters (id, name) VALUES
  ('00000000-0000-0000-0000-0000000000a1', 'North High School'),
  ('00000000-0000-0000-0000-0000000000a2', 'City Community Center');
INSERT INTO staff (id, shelter_id, display_name, role, pin_hash) VALUES
  ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', 'Vol Ana', 'volunteer', :H),
  ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1', 'Lead Sam', 'lead', :H);

INSERT INTO persons (id, shelter_id, preferred_name, other_names, approx_age, is_minor, home_area, visibility, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', 'Catherine Rodriguez', '{}', 80, NULL, '12 Elm St', 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-0000000000a1', 'William Price', '{}', 40, NULL, NULL, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-0000000000a2', 'Mia Chen', '{}', 9, NULL, NULL, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-0000000000a2', 'Daniel Price', '{}', 35, NULL, NULL, 'hidden', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-0000000000a2', 'Jon Doe', '{}', NULL, NULL, NULL, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-0000000000a2', 'Rosa Diaz', '{}', NULL, false, NULL, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-0000000000a2', 'Leo Park', '{}', 7, NULL, NULL, 'hidden', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-0000000000a2', 'José Pérez', '{}', 60, NULL, NULL, 'ask', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-0000000000a2', 'Tom Grey', '{}', 45, NULL, NULL, 'ask', '00000000-0000-0000-0000-0000000000b1');

INSERT INTO searches (id, shelter_id, searcher_person_id, sought_names, entered_by) VALUES
  ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000a2', NULL, '{Mia Chen}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000a2', NULL, '{William Price}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000009', '{Catherine Rodriguez}', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-0000000000c4', '00000000-0000-0000-0000-0000000000a2', NULL, '{Jose Perez}', '00000000-0000-0000-0000-0000000000b1');

CREATE FUNCTION expect_fail(sql text, label text) RETURNS text LANGUAGE plpgsql AS $$
BEGIN
  EXECUTE sql;
  RETURN 'FAIL (no error): ' || label;
EXCEPTION WHEN others THEN
  RETURN 'ok, refused: ' || label || ' -> ' || SQLERRM;
END $$;
GRANT EXECUTE ON FUNCTION expect_fail(text, text) TO homeward_app, homeward_maint;
\pset tuples_only on

\echo '=== T1 default visibility'
SELECT 'visibility = ' || visibility FROM persons WHERE id = '00000000-0000-0000-0000-000000000001';
\echo '=== T2 same UUID twice'
INSERT INTO persons (id, shelter_id, preferred_name, entered_by) VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000a1', 'Catherine Rodriguez', '00000000-0000-0000-0000-0000000000b1') ON CONFLICT (id) DO NOTHING;
SELECT 'persons with id 1: ' || count(*) FROM persons WHERE id = '00000000-0000-0000-0000-000000000001';

\echo '=== T3-T5, T12-13 matching'
SELECT 'Katherine Rodrigez -> ' || p.preferred_name || ' sim=' || round(f.name_similarity::numeric,2) || ' sound=' || f.words_sound_alike || ' age_close=' || f.age_close FROM find_candidates('Katherine Rodrigez', 75, 85) f JOIN persons p ON p.id = f.person_id;
SELECT 'Bill Price -> ' || p.preferred_name || ' nickname=' || f.nickname_match FROM find_candidates('Bill Price', NULL, NULL) f JOIN persons p ON p.id = f.person_id;
SELECT 'John Do -> ' || p.preferred_name || ' sound=' || f.words_sound_alike FROM find_candidates('John Do', NULL, NULL) f JOIN persons p ON p.id = f.person_id;
SELECT 'jose perez -> ' || p.preferred_name FROM find_candidates('jose perez', NULL, NULL) f JOIN persons p ON p.id = f.person_id;
SELECT 'Pepe Perez -> ' || p.preferred_name || ' nickname=' || f.nickname_match FROM find_candidates('Pepe Perez', NULL, NULL) f JOIN persons p ON p.id = f.person_id;
SELECT 'one-sided age (min 55 only) for Jose 60 -> age_close=' || f.age_close FROM find_candidates('Jose Perez', 55, NULL) f;
\echo '=== T6 hidden adult and hidden child: 0 results each'
SELECT 'Daniel Price (hidden adult): ' || count(*) FROM find_candidates('Daniel Price', NULL, NULL) f WHERE f.person_id = '00000000-0000-0000-0000-000000000004';
SELECT 'Leo Park (hidden child): ' || count(*) FROM find_candidates('Leo Park', NULL, NULL) f WHERE f.person_id = '00000000-0000-0000-0000-000000000007';
\echo '=== T7 view columns (no shelter_id, home_area, phone, description)'
SELECT string_agg(column_name, ', ' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = 'search_candidates';
SELECT 'security_barrier: ' || coalesce(array_to_string(reloptions, ','), 'none') FROM pg_class WHERE relname = 'search_candidates';

\echo '=== T8 who counts as a child'
SELECT preferred_name || ' (age ' || coalesce(approx_age::text, '?') || ', is_minor ' || coalesce(is_minor::text, '?') || ') -> child=' || treated_as_minor(p) FROM persons p WHERE id IN ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000006') ORDER BY 1;
SELECT expect_fail($$UPDATE persons SET is_minor = false WHERE id = '00000000-0000-0000-0000-000000000003'$$, 'mark a 9-year-old as adult');
SELECT expect_fail($$UPDATE persons SET is_minor = true WHERE id = '00000000-0000-0000-0000-000000000002'$$, 'mark a 40-year-old as minor');

\echo '=== T9 input checks'
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', E'\t\n ', '00000000-0000-0000-0000-0000000000b1')$$, 'name of only tab/newline/space');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, visibility, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', 'public', '00000000-0000-0000-0000-0000000000b1')$$, 'unknown visibility');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, source, paper_ref, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', 'paper', '  ', '00000000-0000-0000-0000-0000000000b1')$$, 'paper with blank paper_ref');
SELECT expect_fail($$INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', '{""}', '00000000-0000-0000-0000-0000000000b1')$$, 'sought_names {""}');
SELECT expect_fail($$INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', ARRAY[NULL::text], '00000000-0000-0000-0000-0000000000b1')$$, 'sought_names {NULL}');
SELECT expect_fail($$INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-0O1I', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$, 'code with 0/O/1/I');
SELECT expect_fail($$INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-7K3P', '4821')$$, 'plain-text PIN');

\echo '=== T10 child release'
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-000000000003', 'connected', 'Call City desk')$$, 'child, no lead');
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note) VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-000000000003', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b1', true, 'checked')$$, 'child, approver is a volunteer');
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note) VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-000000000003', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b2', true, E' \n')$$, 'child, whitespace note');
INSERT INTO contact_requests (search_id, person_id, state, contact_plan, approved_by, guardian_checked, note) VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-000000000003', 'connected', 'Call City desk', '00000000-0000-0000-0000-0000000000b2', true, 'Mia recognised her dad; school list matches');
SELECT 'ok, allowed: child with lead + guardian check + note';

\echo '=== T11 contact plan required'
SELECT expect_fail($$INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-000000000002', 'connected', '   ')$$, 'connected with blank plan');

\echo '=== T12 a request cannot be moved'
SELECT expect_fail($$UPDATE contact_requests SET person_id = '00000000-0000-0000-0000-000000000005' WHERE person_id = '00000000-0000-0000-0000-000000000003'$$, 'move connected request to another person');
SELECT expect_fail($$UPDATE contact_requests SET search_id = '00000000-0000-0000-0000-0000000000c2' WHERE person_id = '00000000-0000-0000-0000-000000000003'$$, 'move connected request to another search');

\echo '=== T13 flags'
INSERT INTO contact_requests (search_id, person_id, state) VALUES ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-000000000002', 'pending');
INSERT INTO safety_flags (person_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000002', 'do_not_disclose', '00000000-0000-0000-0000-0000000000b2');
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'x' WHERE search_id = '00000000-0000-0000-0000-0000000000c2'$$, 'connect a protected person');
SELECT expect_fail($$UPDATE contact_requests SET state = 'safe_only' WHERE search_id = '00000000-0000-0000-0000-0000000000c2'$$, 'safe_only for a protected person');
SELECT 'protected person in view: ' || count(*) FROM search_candidates WHERE id = '00000000-0000-0000-0000-000000000002';
-- flag added AFTER connect: Mia's connected request
INSERT INTO safety_flags (person_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000003', 'custody_hold', '00000000-0000-0000-0000-0000000000b2');
SELECT 'Mia request after custody_hold: ' || state FROM contact_requests WHERE person_id = '00000000-0000-0000-0000-000000000003';
SELECT 'custody_hold child still matchable for the lead: ' || count(*) FROM search_candidates WHERE id = '00000000-0000-0000-0000-000000000003';
-- flag on the SEARCHER (Tom is suspected): his search for Catherine can't connect
INSERT INTO contact_requests (search_id, person_id, state) VALUES ('00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-000000000001', 'pending');
INSERT INTO safety_flags (person_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000009', 'suspected_abuser', '00000000-0000-0000-0000-0000000000b2');
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'x' WHERE search_id = '00000000-0000-0000-0000-0000000000c3'$$, 'connect when the searcher is flagged');
SELECT expect_fail($$INSERT INTO safety_flags (person_id, search_id, kind, created_by) VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000c1', 'custody_hold', '00000000-0000-0000-0000-0000000000b2')$$, 'flag with two subjects');
SELECT expect_fail($$INSERT INTO safety_flags (search_id, kind, created_by) VALUES ('00000000-0000-0000-0000-0000000000c1', 'do_not_disclose', '00000000-0000-0000-0000-0000000000b2')$$, 'do_not_disclose on a search');

\echo '=== T14 history'
UPDATE persons SET approx_age = 81, updated_by = '00000000-0000-0000-0000-0000000000b2' WHERE id = '00000000-0000-0000-0000-000000000001';
SELECT 'history: age before=' || (before->>'approx_age') || ', changed_by set=' || (changed_by IS NOT NULL) FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000001';

\echo '=== T15 codes'
INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES ('HW-7K3P', '00000000-0000-0000-0000-000000000001', :H);
SELECT expect_fail($$INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES ('HW-8M4Q', '00000000-0000-0000-0000-000000000001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$, 'second working code');

\echo '=== T16 app user'
SET ROLE homeward_app;
INSERT INTO audit_log (staff_id, action, target) VALUES ('00000000-0000-0000-0000-0000000000b1', 'SEARCH', NULL);
SELECT 'ok, allowed: app adds an audit entry';
SELECT expect_fail($$UPDATE audit_log SET action = 'SEARCH'$$, 'app updates audit_log');
SELECT expect_fail($$DELETE FROM audit_log$$, 'app deletes audit_log');
SELECT expect_fail($$INSERT INTO audit_log (staff_id, action, at) VALUES ('00000000-0000-0000-0000-0000000000b1', 'SEARCH', '2020-01-01')$$, 'app sets the audit time');
SELECT expect_fail($$INSERT INTO audit_log (staff_id, action) VALUES ('00000000-0000-0000-0000-0000000000b1', 'PIN 4821 at Eastside')$$, 'free-text audit action');
SELECT expect_fail($$DELETE FROM persons$$, 'app deletes persons');
SELECT expect_fail($$INSERT INTO person_history (person_id, before) VALUES ('00000000-0000-0000-0000-000000000001', '{}')$$, 'app writes fake history');
UPDATE persons SET search_names = 'fake name' WHERE id = '00000000-0000-0000-0000-000000000005';
SELECT 'app sets search_names by hand -> stored: ' || search_names FROM persons WHERE id = '00000000-0000-0000-0000-000000000005';
SELECT 'app edit still wrote history: ' || count(*) FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000005';
SELECT expect_fail($$UPDATE safety_flags SET kind = 'custody_hold' WHERE person_id = '00000000-0000-0000-0000-000000000002'$$, 'app rewrites a flag kind');
SELECT expect_fail($$UPDATE safety_flags SET cleared_at = now() WHERE person_id = '00000000-0000-0000-0000-000000000002'$$, 'app lifts a flag without saying who');
UPDATE safety_flags SET cleared_at = now(), cleared_by = '00000000-0000-0000-0000-0000000000b2' WHERE person_id = '00000000-0000-0000-0000-000000000002';
SELECT 'ok, allowed: app lifts a flag with cleared_by';
SELECT 'app can match: ' || count(*) FROM find_candidates('Mia', NULL, NULL);
RESET ROLE;

\echo '=== T17 retention user'
SET ROLE homeward_maint;
DELETE FROM persons WHERE id = '00000000-0000-0000-0000-000000000001';
SELECT 'ok, allowed: maint deletes a person';
SELECT expect_fail($$DELETE FROM audit_log$$, 'maint deletes audit_log');
RESET ROLE;
SELECT 'history left for deleted person: ' || count(*) FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000001';
SELECT 'audit rows kept: ' || count(*) FROM audit_log;

\echo '=== T18 final attacks'
INSERT INTO contact_requests (search_id, person_id, state) VALUES ('00000000-0000-0000-0000-0000000000c4', '00000000-0000-0000-0000-000000000008', 'pending');
INSERT INTO safety_flags (search_id, kind, created_by) VALUES ('00000000-0000-0000-0000-0000000000c4', 'suspected_abuser', '00000000-0000-0000-0000-0000000000b2');
SET ROLE homeward_app;
CREATE TEMP TABLE safety_flags (LIKE public.safety_flags);
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'x' WHERE search_id = '00000000-0000-0000-0000-0000000000c4'$$, 'connect while a temp table "safety_flags" hides the real one');
DROP TABLE pg_temp.safety_flags;
CREATE TEMP TABLE fake (id uuid, updated_by uuid);
SELECT expect_fail($$CREATE TRIGGER t BEFORE UPDATE ON fake FOR EACH ROW EXECUTE FUNCTION public.persons_keep_history()$$, 'attach the history function to a temp table');
SELECT expect_fail($$UPDATE safety_flags SET cleared_at = NULL, cleared_by = NULL WHERE person_id = '00000000-0000-0000-0000-000000000002'$$, 'switch a lifted flag back on');
UPDATE persons SET preferred_name = 'Jon Doe', updated_by = '00000000-0000-0000-0000-0000000000b1' WHERE id = '00000000-0000-0000-0000-000000000005';
SELECT 'ok, app edit still writes history (trigger works without EXECUTE grant): ' || count(*) FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000005';
RESET ROLE;
\echo '=== T19 ranking uses all signals: Pepe Perez -> top result'
SELECT 'top for "Pepe Perez": ' || p.preferred_name FROM find_candidates('Pepe Perez', NULL, NULL) f JOIN persons p ON p.id = f.person_id LIMIT 1;

\echo '=== T20 a child who is SEARCHING also needs a lead'
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-0000000000a2', 'Sam Lee', 10, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-0000000000a1', 'Grace Lee', 41, '00000000-0000-0000-0000-0000000000b1');
INSERT INTO searches (id, shelter_id, searcher_person_id, sought_names, entered_by) VALUES
  ('00000000-0000-0000-0000-0000000000c5', '00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000010', '{Grace Lee}', '00000000-0000-0000-0000-0000000000b1');
INSERT INTO contact_requests (search_id, person_id, state) VALUES ('00000000-0000-0000-0000-0000000000c5', '00000000-0000-0000-0000-000000000011', 'pending');
SELECT expect_fail($$UPDATE contact_requests SET state = 'connected', contact_plan = 'Call the North High desk' WHERE search_id = '00000000-0000-0000-0000-0000000000c5'$$, 'connect a child searcher (Sam, 10) with an adult, no lead');
UPDATE contact_requests SET state = 'connected', contact_plan = 'Call the North High desk', approved_by = '00000000-0000-0000-0000-0000000000b2', guardian_checked = true, note = 'Grace is his mother; school list matches' WHERE search_id = '00000000-0000-0000-0000-0000000000c5';
SELECT 'ok, allowed: child searcher with lead + guardian check + note';

\echo '=== T21 the person searching cannot be swapped'
SELECT expect_fail($$UPDATE searches SET searcher_person_id = '00000000-0000-0000-0000-000000000005' WHERE id = '00000000-0000-0000-0000-0000000000c5'$$, 'swap the searcher on a search that already has one');
-- a search with no searcher yet that already got a "Connect" from an adult (Grace)
INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES ('00000000-0000-0000-0000-0000000000c7', '00000000-0000-0000-0000-0000000000a1', '{Grace Lee}', '00000000-0000-0000-0000-0000000000b1');
INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000000c7', '00000000-0000-0000-0000-000000000011', 'connected', 'Call the North High desk');
SELECT 'state before: ' || state FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000000c7';
SELECT expect_fail($$UPDATE searches SET searcher_person_id = '00000000-0000-0000-0000-000000000010' WHERE id = '00000000-0000-0000-0000-0000000000c7'$$, 'attach a child searcher to a search that already got a "Connect"');
INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES ('00000000-0000-0000-0000-0000000000c6', '00000000-0000-0000-0000-0000000000a2', '{Rosa Diaz}', '00000000-0000-0000-0000-0000000000b1');
UPDATE searches SET searcher_person_id = '00000000-0000-0000-0000-000000000009' WHERE id = '00000000-0000-0000-0000-0000000000c6';
SELECT 'ok, allowed: link the searcher to a new search with no answer yet';

\echo '=== T22 correcting an age to a child re-checks earlier "Connect"s'
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-0000000000a1', 'Ben Ortiz', 35, '00000000-0000-0000-0000-0000000000b1');
INSERT INTO searches (id, shelter_id, sought_names, entered_by) VALUES ('00000000-0000-0000-0000-0000000000c8', '00000000-0000-0000-0000-0000000000a2', '{Ben Ortiz}', '00000000-0000-0000-0000-0000000000b1');
INSERT INTO contact_requests (search_id, person_id, state, contact_plan) VALUES ('00000000-0000-0000-0000-0000000000c8', '00000000-0000-0000-0000-000000000012', 'connected', 'Call the North High desk');
SELECT 'Ben (35) connected, no lead: ' || state FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000000c8';
UPDATE persons SET approx_age = 14, updated_by = '00000000-0000-0000-0000-0000000000b1' WHERE id = '00000000-0000-0000-0000-000000000012';
SELECT 'after correcting Ben to 14: ' || state FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000000c8';
-- Sam (10) -> Grace already has a lead sign-off; correcting Grace to 15 keeps it connected
UPDATE persons SET approx_age = 15, updated_by = '00000000-0000-0000-0000-0000000000b1' WHERE id = '00000000-0000-0000-0000-000000000011';
SELECT 'Sam -> Grace (lead-approved) after correcting Grace to 15: ' || state FROM contact_requests WHERE search_id = '00000000-0000-0000-0000-0000000000c5';
