-- 03_registration.sql: database step 3, registration as the app would do it.
-- Runs as homeward_app (the least-privilege user the backend will use). Fictional data only.
-- Every line asserts: "ok, ..." or "FAIL: ...".
\set ON_ERROR_STOP on
\pset tuples_only on

-- Owner (= a lead printing a sheet): one pre-printed slip that isn't attached to anyone yet.
INSERT INTO reunion_codes (code, pin_hash) VALUES ('HW-4T6W', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');

SET ROLE homeward_app;

-- R1. A volunteer registers Sarah: only a name; everything else defaults safely
INSERT INTO persons (id, shelter_id, preferred_name, entered_by)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-0000000000a1', 'Sarah Kim', '00000000-0000-0000-0000-0000000000b1');
SELECT assert_true(visibility = 'ask' AND source = 'app' AND status = 'present' AND approx_age IS NULL AND treated_as_minor(p),
                   'Sarah registered with just a name: ask, app, present, unknown age (so treated as a child)')
FROM persons p WHERE id = '00000000-0000-0000-0000-000000000101';

-- R2. Slow Wi-Fi: the browser re-sends the same form (same id) -> nothing new is stored
WITH ins AS (
  INSERT INTO persons (id, shelter_id, preferred_name, entered_by)
  VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-0000000000a1', 'Sarah Kim', '00000000-0000-0000-0000-0000000000b1')
  ON CONFLICT (id) DO NOTHING RETURNING 1)
SELECT assert_true(count(*) = 0, 're-sent registration (same id) inserts nothing') FROM ins;
SELECT assert_true(count(*) = 1, 'Sarah is stored once') FROM persons WHERE preferred_name = 'Sarah Kim';

-- R3. The person's privacy choice is stored as chosen (hidden), not overwritten by the default
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, visibility, entered_by)
VALUES ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-0000000000a1', 'Dana Ruiz', 34, 'hidden', '00000000-0000-0000-0000-0000000000b1');
SELECT assert_true(visibility = 'hidden', 'a chosen "Keep me out of searches" (hidden) is stored') FROM persons WHERE id = '00000000-0000-0000-0000-000000000102';

-- R4. Copying from paper: sheet/line and the date written on the paper are kept
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, source, paper_ref, recorded_at, entered_by)
VALUES ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-0000000000a2', 'Tom Baker', 70, 'paper', 'Sheet 4, line 12', '2026-10-17', '00000000-0000-0000-0000-0000000000b1');
SELECT assert_true(source = 'paper' AND paper_ref = 'Sheet 4, line 12' AND recorded_at::date = DATE '2026-10-17',
                   'paper registration keeps sheet/line and the date on the paper (not today)')
FROM persons WHERE id = '00000000-0000-0000-0000-000000000103';

-- R5. An unaccompanied child is stored as such (for the lead's list)
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, is_minor, unaccompanied, entered_by)
VALUES ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-0000000000a2', 'Leo Park', 9, true, true, '00000000-0000-0000-0000-0000000000b1');
SELECT assert_true(unaccompanied AND treated_as_minor(p), 'unaccompanied 9-year-old stored and treated as a child')
FROM persons p WHERE id = '00000000-0000-0000-0000-000000000104';

-- R6. Hand Sarah the pre-printed slip: attach it only if it's still free and working
WITH att AS (
  UPDATE reunion_codes SET person_id = '00000000-0000-0000-0000-000000000101'
  WHERE code = 'HW-4T6W' AND person_id IS NULL AND voided_at IS NULL RETURNING 1)
SELECT assert_true(count(*) = 1, 'pre-printed slip HW-4T6W attached to Sarah') FROM att;
INSERT INTO audit_log (staff_id, action, target) VALUES ('00000000-0000-0000-0000-0000000000b1', 'CODE_ISSUED', '00000000-0000-0000-0000-000000000101');
SELECT assert_true(count(*) = 1, 'the slip hand-out is in the audit log')
FROM audit_log WHERE action = 'CODE_ISSUED' AND target = '00000000-0000-0000-0000-000000000101';

-- R7. The same slip can't be attached to a second person (it's no longer free)
WITH att AS (
  UPDATE reunion_codes SET person_id = '00000000-0000-0000-0000-000000000103'
  WHERE code = 'HW-4T6W' AND person_id IS NULL AND voided_at IS NULL RETURNING 1)
SELECT assert_true(count(*) = 0, 'an already-attached slip is not attached to Tom') FROM att;
SELECT assert_true(person_id = '00000000-0000-0000-0000-000000000101', 'slip HW-4T6W still belongs to Sarah') FROM reunion_codes WHERE code = 'HW-4T6W';

-- R8. Sarah can't get a second working slip
SELECT expect_fail($$INSERT INTO reunion_codes (code, person_id, pin_hash) VALUES ('HW-5X7Y', '00000000-0000-0000-0000-000000000101', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')$$, 'a second working slip for Sarah');

-- R9. A typo fixed with Edit keeps a snapshot of the row before, with who changed it
UPDATE persons SET preferred_name = 'Sara Kim', approx_age = 29, updated_by = '00000000-0000-0000-0000-0000000000b1'
WHERE id = '00000000-0000-0000-0000-000000000101';
SELECT assert_true(before->>'preferred_name' = 'Sarah Kim' AND before->>'approx_age' IS NULL AND changed_by = '00000000-0000-0000-0000-0000000000b1',
                   'the edit kept a snapshot (name before = Sarah Kim) and who changed it')
FROM person_history WHERE person_id = '00000000-0000-0000-0000-000000000101';
SELECT assert_true(search_names = 'sara kim' AND NOT treated_as_minor(p), 'after the edit: matching names updated, age 29 is an adult')
FROM persons p WHERE id = '00000000-0000-0000-0000-000000000101';

-- R10. Registration can't skip the rules, even from the app
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', '  ', '00000000-0000-0000-0000-0000000000b1')$$, 'register with a blank name');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, source, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', 'paper', '00000000-0000-0000-0000-0000000000b1')$$, 'paper registration without sheet/line');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', 'X', NULL)$$, 'register without saying which volunteer entered it');
SELECT expect_fail($$INSERT INTO persons (id, shelter_id, preferred_name, entered_by) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000ff', 'X', '00000000-0000-0000-0000-0000000000b1')$$, 'register at a shelter that does not exist');
SELECT expect_fail($$DELETE FROM persons WHERE id = '00000000-0000-0000-0000-000000000102'$$, 'the app deletes a registration');

RESET ROLE;
