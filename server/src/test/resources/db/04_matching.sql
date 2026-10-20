-- 04_matching.sql: database step 4 (V3__safe_matching.sql). Matching finds people without
-- revealing where they are, and never finds hidden or protected people.
-- Searches run as homeward_app, like the backend. Fictional data only; every line asserts.
\set ON_ERROR_STOP on
\pset tuples_only on

-- People at two fictional shelters (a1, a2), entered by Vol Ana (b1).
INSERT INTO persons (id, shelter_id, preferred_name, other_names, approx_age, visibility, status, home_area, phone, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-0000000000a1', 'Catherine Rodriguez', '{}', 80, 'ask', 'present', 'Seaview', '555-0400', '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-0000000000a2', 'William Price', '{}', 40, 'findable', 'present', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-0000000000a2', 'José Pérez', '{}', NULL, 'ask', 'present', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-0000000000a1', 'Jon Smyth', '{}', 35, 'ask', 'present', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000405', '00000000-0000-0000-0000-0000000000a1', 'Sofia Brandt', '{}', 33, 'hidden', 'present', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000406', '00000000-0000-0000-0000-0000000000a2', 'Lily Brandt', '{}', 8, 'hidden', 'present', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000407', '00000000-0000-0000-0000-0000000000a2', 'Lena Ortiz', '{}', 29, 'findable', 'present', NULL, NULL, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000408', '00000000-0000-0000-0000-0000000000a2', 'Omar Haddad', '{}', 50, 'ask', 'left', NULL, NULL, '00000000-0000-0000-0000-0000000000b1');
-- A lead protects Lena: nobody may find her.
INSERT INTO safety_flags (person_id, kind, created_by)
VALUES ('00000000-0000-0000-0000-000000000407', 'do_not_disclose', '00000000-0000-0000-0000-0000000000b2');

-- Crowded name: 120 "Maria Garcia" aged 20-59, and one aged 80 whose id sorts LAST,
-- so only the age signal (not the id tie-break) can bring her into the first 100.
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, entered_by)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'Maria Garcia', 20 + (i % 40), '00000000-0000-0000-0000-0000000000b1'
FROM generate_series(1, 120) AS i;
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, entered_by)
VALUES ('ffffffff-ffff-4fff-bfff-000000000409', '00000000-0000-0000-0000-0000000000a2', 'Maria Garcia', 80, '00000000-0000-0000-0000-0000000000b1');

SET ROLE homeward_app;

-- M1. Misspelled, accents, sound-alike, nickname
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Catrine Rodrigez', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000401'),
                   'misspelled "Catrine Rodrigez" finds Catherine Rodriguez');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Jose Perez', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000403'),
                   '"Jose Perez" without accents finds José Pérez');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('John Smith', NULL, NULL) f WHERE person_id = '00000000-0000-0000-0000-000000000404' AND f.words_sound_alike >= 1),
                   '"John Smith" finds Jon Smyth by sound');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Bill Price', NULL, NULL) f WHERE person_id = '00000000-0000-0000-0000-000000000402' AND f.nickname_match),
                   '"Bill Price" finds William Price as a nickname match');

-- M2. Ages: a signal only when both sides know an age
SELECT assert_true(age_close, 'age 80 fits a search for 75-85')
FROM find_candidates('Catherine Rodriguez', 75, 85) WHERE person_id = '00000000-0000-0000-0000-000000000401';
SELECT assert_true(age_close IS NULL, 'no age in the search: no age signal (not "too old")')
FROM find_candidates('Catherine Rodriguez', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000401';
SELECT assert_true(age_close IS NULL, 'person''s age unknown: no age signal')
FROM find_candidates('Jose Perez', 30, 40) WHERE person_id = '00000000-0000-0000-0000-000000000403';
SELECT assert_true(NOT age_close, 'age 40 doesn''t fit a search for 5-10')
FROM find_candidates('William Price', 5, 10) WHERE person_id = '00000000-0000-0000-0000-000000000402';

-- M3. Hidden, protected and departed people are never found, adult or child
SELECT assert_true(NOT EXISTS (SELECT 1 FROM find_candidates('Sofia Brandt', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000405'),
                   'hidden adult (Sofia) is never found, even by exact name');
SELECT assert_true(NOT EXISTS (SELECT 1 FROM find_candidates('Lily Brandt', 5, 10) WHERE person_id = '00000000-0000-0000-0000-000000000406'),
                   'hidden child (Lily) is never found');
SELECT assert_true(NOT EXISTS (SELECT 1 FROM find_candidates('Lena Ortiz', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000407'),
                   'protected person (do_not_disclose) is never found, even though "findable"');
SELECT assert_true(NOT EXISTS (SELECT 1 FROM find_candidates('Omar Haddad', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000408'),
                   'someone who left the shelter system is not suggested');

-- M4. What matching can read has no location at all
SELECT assert_true(count(*) = 0, 'search_candidates has no shelter / home area / phone / description / paper columns')
FROM information_schema.columns
WHERE table_name = 'search_candidates'
  AND column_name IN ('shelter_id', 'home_area', 'phone', 'description', 'paper_ref', 'entered_by', 'status', 'visibility');
SELECT assert_true(count(*) = 6, 'find_candidates returns only ids and signals (6 columns)')
FROM pg_proc p, unnest(p.proargmodes) AS mode WHERE p.proname = 'find_candidates' AND mode = 't';

-- M5. Crowded name: the one of the right age survives the 100-candidate cut and comes first
SELECT assert_true(count(*) = 100, 'crowded name: the cut still keeps 100 candidates')
FROM find_candidates('Maria Garcia', 75, 85);
SELECT assert_true(person_id = 'ffffffff-ffff-4fff-bfff-000000000409', 'crowded name: Maria Garcia aged 80 comes back first for 75-85')
FROM (SELECT person_id FROM find_candidates('Maria Garcia', 75, 85) LIMIT 1) first_row;

-- M6. Flags: the app can lift a flag, but not rewrite it or switch it back on
UPDATE safety_flags SET cleared_at = now(), cleared_by = '00000000-0000-0000-0000-0000000000b2'
WHERE person_id = '00000000-0000-0000-0000-000000000407';
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Lena Ortiz', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000407'),
                   'once the lead lifts the flag, Lena (findable) can be found');
SELECT expect_fail($$UPDATE safety_flags SET cleared_at = NULL, cleared_by = NULL WHERE person_id = '00000000-0000-0000-0000-000000000407'$$, 'switch a lifted flag back on');
SELECT expect_fail($$UPDATE safety_flags SET kind = 'custody_hold' WHERE person_id = '00000000-0000-0000-0000-000000000407'$$, 'app rewrites what a flag is');
SELECT expect_fail($$DELETE FROM safety_flags$$, 'app deletes flags');
SELECT expect_fail($$INSERT INTO safety_flags (kind, created_by) VALUES ('do_not_disclose', '00000000-0000-0000-0000-0000000000b2')$$, 'flag about nobody');

-- M7. Suggested matches: once per search and person
INSERT INTO searches (id, shelter_id, sought_names, entered_by)
VALUES ('00000000-0000-0000-0000-000000000491', '00000000-0000-0000-0000-0000000000a2', '{Catherine Rodriguez}', '00000000-0000-0000-0000-0000000000b1');
INSERT INTO match_candidates (search_id, person_id, score, reasons)
VALUES ('00000000-0000-0000-0000-000000000491', '00000000-0000-0000-0000-000000000401', 0.9, '["name spelled like Catherine Rodriguez"]');
SELECT 'ok, allowed: app stores a suggested match';
SELECT expect_fail($$INSERT INTO match_candidates (search_id, person_id, score, reasons) VALUES ('00000000-0000-0000-0000-000000000491', '00000000-0000-0000-0000-000000000401', 0.5, '[]')$$, 'same person suggested twice for one search');
SELECT expect_fail($$INSERT INTO match_candidates (search_id, person_id, score, reasons) VALUES ('00000000-0000-0000-0000-000000000491', '00000000-0000-0000-0000-000000000402', 1.5, '[]')$$, 'score above 1');
SELECT expect_fail($$INSERT INTO match_candidates (search_id, person_id, score, reasons) VALUES ('00000000-0000-0000-0000-000000000491', '00000000-0000-0000-0000-000000000405', 0.9, '[]')$$, 'suggest a hidden person directly');
SELECT expect_fail($$UPDATE match_candidates SET person_id = '00000000-0000-0000-0000-000000000405' WHERE search_id = '00000000-0000-0000-0000-000000000491'$$, 'rewrite who was suggested');
UPDATE match_candidates SET decision = 'confirmed', reviewed_by = '00000000-0000-0000-0000-0000000000b1' WHERE search_id = '00000000-0000-0000-0000-000000000491';
SELECT 'ok, allowed: a volunteer confirms a suggested match';

-- M8. A handed-out slip stays with its person; a pre-printed one can be handed out once
RESET ROLE;
INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES ('HW-K7M2', '00000000-0000-0000-0000-000000000401', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-P3Q8', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
SET ROLE homeward_app;
SELECT expect_fail($$UPDATE reunion_codes SET person_id = '00000000-0000-0000-0000-000000000402' WHERE code = 'HW-K7M2'$$, 'move a handed-out slip to someone else');
SELECT expect_fail($$UPDATE reunion_codes SET person_id = NULL WHERE code = 'HW-K7M2'$$, 'take a handed-out slip back to blank');
SELECT expect_fail($$UPDATE reunion_codes SET code = 'HW-Z9Z9' WHERE code = 'HW-K7M2'$$, 'change the code of a handed-out slip');
SELECT expect_fail($$UPDATE reunion_codes SET pin_hash = '$2a$10$abcdefghijklmnopqrstuuN9qo8uLOickgx2ZMRZoMyeIjZAgcfl7' WHERE code = 'HW-K7M2'$$, 'change the PIN of a handed-out slip');
UPDATE reunion_codes SET failed_attempts = 1 WHERE code = 'HW-K7M2';
SELECT 'ok, allowed: counting a wrong PIN on a handed-out slip';
UPDATE reunion_codes SET person_id = '00000000-0000-0000-0000-000000000402' WHERE code = 'HW-P3Q8';
SELECT assert_true(person_id = '00000000-0000-0000-0000-000000000402', 'a pre-printed slip is handed out to William')
FROM reunion_codes WHERE code = 'HW-P3Q8';
RESET ROLE;
