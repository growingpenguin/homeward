-- V6__edge_cases.sql (edge-case campaign): two rules found while testing.
--
-- 1. An ESTIMATED age under 21 counts as a child. "About 18" from a tired volunteer can easily be 16,
--    and a child must never be released on a guess. An exact age (age_is_estimate = false) of 18 or more
--    is an adult. The persons CHECK constraints are unchanged: they only compare is_minor with the
--    number, and "adult + estimated 19" stays storable. For that case the ESTIMATE WINS (a known age
--    decides, as before; an estimate under 21 is "not known well enough to call an adult"). A volunteer
--    who is sure can enter the exact age, or a lead can correct the record. With no age at all, nothing
--    changed: only an explicit is_minor = false makes someone an adult.
-- 2. Hyphens and apostrophes in names: "Mary-Ann O'Brien" is stored and searched as "mary ann obrien"
--    (hyphen = word break, apostrophe = removed), so "Mary Ann", "Mary-Ann" and "O'Brien"/"OBrien" all line up.
--
-- Neither change needs a new column. treated_as_minor is replaced in place (the search_candidates view,
-- ConsentRepo and the backend call it by name). Tested with scripts/db-test.sh (07_edge_cases.sql).

CREATE OR REPLACE FUNCTION treated_as_minor(p persons) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN p.approx_age IS NOT NULL THEN p.approx_age < 18 OR (p.age_is_estimate AND p.approx_age < 21)
                ELSE coalesce(p.is_minor, true) END
$$;

-- One cleaning rule for stored names and searched names: no accents, lower case, apostrophes
-- (straight, curly, modifier, backtick) removed, hyphens and dashes become spaces.
CREATE FUNCTION normalize_name(n text) RETURNS text
LANGUAGE sql STABLE SET search_path = public, pg_temp AS $$
    SELECT lower(unaccent(btrim(
        regexp_replace(regexp_replace(n, '[''’ʼ`]', '', 'g'), '[-‐‑‒–—]', ' ', 'g'))))
$$;
GRANT EXECUTE ON FUNCTION normalize_name(text) TO homeward_app;

CREATE OR REPLACE FUNCTION persons_fill_names() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    NEW.search_names := normalize_name(NEW.preferred_name || ' ' || array_to_string(NEW.other_names, ' '));
    NEW.name_words   := ARRAY(
        SELECT DISTINCT w FROM unnest(regexp_split_to_array(NEW.search_names, '\s+')) AS w WHERE w <> '');
    NEW.name_sounds  := ARRAY(
        SELECT DISTINCT dmetaphone(w) FROM unnest(NEW.name_words) AS w);
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION find_candidates(q_name text, q_age_min int, q_age_max int)
RETURNS TABLE (person_id uuid, name_similarity real, words_sound_alike int,
               nickname_match boolean, age_close boolean, minor boolean)
LANGUAGE sql STABLE SET search_path = public, pg_temp AS $$
    WITH q AS (                                    -- the searched name, cleaned the same way as stored names
        SELECT normalize_name(q_name) AS name
    ), words AS (                                  -- split it into words
        SELECT w FROM q, unnest(regexp_split_to_array(q.name, '\s+')) AS w WHERE w <> ''
    ), expanded AS (                               -- "Bill" also means "William", and back
        SELECT w FROM words
        UNION SELECT n.full_name FROM nicknames n JOIN words ON n.nickname = words.w
        UNION SELECT n.nickname  FROM nicknames n JOIN words ON n.full_name = words.w
    )
    SELECT * FROM (
        SELECT c.id AS pid,
               similarity(c.search_names, q.name) AS sim,
               (SELECT count(*)::int FROM words WHERE dmetaphone(words.w) = ANY (c.name_sounds)) AS sounds,
               EXISTS (SELECT 1 FROM expanded e
                       WHERE e.w NOT IN (SELECT w FROM words) AND e.w = ANY (c.name_words)) AS nick,
               CASE WHEN c.approx_age IS NULL OR (q_age_min IS NULL AND q_age_max IS NULL) THEN NULL  -- no signal
                    ELSE c.approx_age BETWEEN coalesce(q_age_min, 0) - 5 AND coalesce(q_age_max, 120) + 5 END AS age_ok,
               c.minor
        FROM search_candidates c, q
        WHERE c.search_names % q.name                                   -- similar spelling
           OR c.name_sounds && ARRAY(SELECT dmetaphone(w) FROM words)   -- or a word sounds alike
           OR c.name_words  && ARRAY(SELECT w FROM expanded)            -- or a nickname / exact word
    ) s
    -- Same ordering and cut as V3 (see the comment there).
    ORDER BY s.sim + 0.3 * s.sounds + CASE WHEN s.nick THEN 0.5 ELSE 0 END
             + CASE WHEN s.age_ok THEN 0.3 ELSE 0 END DESC, s.pid
    LIMIT 100
$$;

-- Names already stored get the new cleaning too. The fill-names trigger recomputes them; the history
-- trigger is switched off for this one statement so no "edit" snapshot is written for every person.
ALTER TABLE persons DISABLE TRIGGER persons_keep_history;
UPDATE persons SET preferred_name = preferred_name;
ALTER TABLE persons ENABLE TRIGGER persons_keep_history;
