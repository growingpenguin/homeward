-- 07_edge_cases.sql: edge-case campaign (V6__edge_cases.sql): estimated ages near 18, names with
-- hyphens and apostrophes, non-Latin names, and a child whose age and "minor" answer are both unknown.
-- Only scripts/db-test.sh's disposable database. Fictional data only; every line asserts.
\set ON_ERROR_STOP on
\pset tuples_only on

INSERT INTO persons (id, shelter_id, preferred_name, approx_age, age_is_estimate, is_minor, entered_by) VALUES
  ('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-0000000000a1', 'Edge Eighteen',    18, true,  NULL,  '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000702', '00000000-0000-0000-0000-0000000000a1', 'Edge Nineteen',    19, false, NULL,  '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000703', '00000000-0000-0000-0000-0000000000a1', 'Edge Twenty',      20, true,  NULL,  '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000704', '00000000-0000-0000-0000-0000000000a1', 'Edge Twentyone',   21, true,  NULL,  '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000705', '00000000-0000-0000-0000-0000000000a1', 'Edge Seventeen',   17, false, NULL,  '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000706', '00000000-0000-0000-0000-0000000000a1', 'Edge Marked Adult', 19, true,  false, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000707', '00000000-0000-0000-0000-0000000000a1', 'Edge Unknown',     NULL, true, NULL,  '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000708', '00000000-0000-0000-0000-0000000000a1', 'Edge Unknown Adult', NULL, true, false, '00000000-0000-0000-0000-0000000000b1'),
  ('00000000-0000-0000-0000-000000000709', '00000000-0000-0000-0000-0000000000a1', 'Mary-Ann O''Brien', 40, false, NULL, '00000000-0000-0000-0000-0000000000b1');

-- E1. An estimated age under 21 is a child; an exact age of 18 or more is an adult.
SELECT assert_true((SELECT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000701'),
                   'an estimated "about 18" is treated as a child');
SELECT assert_true((SELECT NOT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000702'),
                   'an exact age of 19 (not an estimate) is an adult');
SELECT assert_true((SELECT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000703'),
                   'an estimated 20 is treated as a child');
SELECT assert_true((SELECT NOT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000704'),
                   'an estimated 21 is an adult');
SELECT assert_true((SELECT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000705'),
                   'an exact 17 is a child');
SELECT assert_true((SELECT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000706'),
                   'marked "adult" but with an estimated age of 19: the estimate wins, still a child (a lead can fix it)');
SELECT assert_true((SELECT minor FROM search_candidates WHERE id = '00000000-0000-0000-0000-000000000701'),
                   'the matching view shows an estimated 18 as a child');
SELECT assert_true((SELECT minor FROM find_candidates('Edge Eighteen', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000701'),
                   'find_candidates reports an estimated 18 as a child');

-- E2 (CH5). No age and no answer: a child. Only an explicit "adult" makes an unknown age an adult.
SELECT assert_true((SELECT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000707'),
                   'unknown age and no answer for "minor": treated as a child');
SELECT assert_true((SELECT NOT treated_as_minor(p) FROM persons p WHERE id = '00000000-0000-0000-0000-000000000708'),
                   'unknown age marked adult by a volunteer: an adult');

-- E3. Hyphens and apostrophes are word breaks.
SELECT assert_true((SELECT name_words @> ARRAY['mary','ann','obrien'] FROM persons WHERE id = '00000000-0000-0000-0000-000000000709'),
                   '"Mary-Ann O''Brien" is stored as the words mary, ann, obrien');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Mary Ann OBrien', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000709'),
                   '"Mary Ann OBrien" finds Mary-Ann O''Brien');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Maryann Obrien', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000709'),
                   '"Maryann Obrien" finds Mary-Ann O''Brien');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('Mary-Ann', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000709'),
                   '"Mary-Ann" finds Mary-Ann O''Brien');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('O’Brien', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-000000000709'),
                   'a curly apostrophe "O’Brien" finds Mary-Ann O''Brien');

-- E4. Non-Latin names: stored and searched without an error (matching is best-effort).
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, entered_by)
VALUES ('00000000-0000-0000-0000-00000000070a', '00000000-0000-0000-0000-0000000000a1', 'محمد علي', 45, '00000000-0000-0000-0000-0000000000b1');
SELECT assert_true((SELECT count(*) >= 0 FROM find_candidates('李小龍', NULL, NULL)), 'a Chinese name search returns without an error');
SELECT assert_true((SELECT count(*) >= 0 FROM find_candidates('محمد علي', NULL, NULL)), 'an Arabic name search returns without an error');
SELECT assert_true(EXISTS (SELECT 1 FROM find_candidates('محمد علي', NULL, NULL) WHERE person_id = '00000000-0000-0000-0000-00000000070a'),
                   'an Arabic name finds the same Arabic name');
