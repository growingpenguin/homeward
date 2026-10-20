-- V3__safe_matching.sql (database step 4): finding people without revealing where they are.
-- Moved from db/draft/full_schema_draft.sql, plus two fixes from the reviews: the age signal counts
-- before the 100-candidate cut, and a handed-out slip can't be moved to another person.
-- Consent and protection (contact requests and their guards) follow in V4.
-- Tested on PostgreSQL 18.6 (same as Neon) with scripts/db-test.sh.

-- ---------- safety flags ----------
CREATE TABLE safety_flags (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id  uuid REFERENCES persons(id) ON DELETE CASCADE,  -- about this person
    search_id  uuid REFERENCES searches(id) ON DELETE CASCADE, -- about this search
    kind       text NOT NULL CHECK (kind IN ('do_not_disclose', 'suspected_abuser', 'custody_hold')),
    note       text,
    created_by uuid NOT NULL REFERENCES staff(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    cleared_at timestamptz,                                    -- NULL = still active
    cleared_by uuid REFERENCES staff(id),
    CHECK ((person_id IS NULL) <> (search_id IS NULL)),        -- exactly one subject
    CHECK (kind <> 'do_not_disclose' OR person_id IS NOT NULL),-- only a person can be protected
    CHECK ((cleared_at IS NULL) = (cleared_by IS NULL))        -- a lifted flag says who lifted it
);
CREATE INDEX safety_flags_person ON safety_flags (person_id) WHERE cleared_at IS NULL;
CREATE INDEX safety_flags_search ON safety_flags (search_id) WHERE cleared_at IS NULL;

-- A new flag locks the same rows as the "Connect" guard (V4), so a flag and a "Connect" can't slip past each other.
CREATE FUNCTION safety_flags_lock() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    PERFORM 1 FROM persons  WHERE id = NEW.person_id FOR UPDATE;
    PERFORM 1 FROM searches WHERE id = NEW.search_id FOR UPDATE;
    RETURN NEW;
END $$;

CREATE TRIGGER safety_flags_lock
    BEFORE INSERT ON safety_flags
    FOR EACH ROW EXECUTE FUNCTION safety_flags_lock();

-- A lifted flag stays lifted: switching it back on would skip the lock and the blocking above.
-- To protect someone again, add a new flag.
CREATE FUNCTION safety_flags_no_reactivate() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.cleared_at IS NOT NULL AND NEW.cleared_at IS NULL THEN
        RAISE EXCEPTION 'a lifted flag can''t be switched back on; add a new flag' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER safety_flags_no_reactivate
    BEFORE UPDATE ON safety_flags
    FOR EACH ROW EXECUTE FUNCTION safety_flags_no_reactivate();

-- ---------- what matching is allowed to read ----------
-- No shelter, home area, phone or description. Hidden people and protected people never show up.
-- security_barrier: the filters run first, so a clever query can't peek at the rows they remove.
CREATE VIEW search_candidates WITH (security_barrier) AS
SELECT p.id, p.preferred_name, p.other_names, p.approx_age, p.age_is_estimate,
       p.search_names, p.name_words, p.name_sounds, treated_as_minor(p) AS minor
FROM persons p
WHERE p.status <> 'left'
  AND p.visibility <> 'hidden'
  AND NOT EXISTS (SELECT 1 FROM safety_flags f
                  WHERE f.person_id = p.id AND f.cleared_at IS NULL
                    AND f.kind = 'do_not_disclose');

-- ---------- nicknames (reference data, not people) ----------
CREATE TABLE nicknames (
    nickname  text NOT NULL,
    full_name text NOT NULL,
    PRIMARY KEY (nickname, full_name)
);
INSERT INTO nicknames VALUES
    ('bill', 'william'), ('will', 'william'), ('liz', 'elizabeth'), ('beth', 'elizabeth'),
    ('bob', 'robert'), ('rob', 'robert'), ('kate', 'katherine'), ('katie', 'katherine'),
    ('jim', 'james'), ('tony', 'anthony'), ('pepe', 'jose'), ('lupe', 'guadalupe');


-- ---------- suggested matches (a person decides, never the score) ----------
CREATE TABLE match_candidates (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    search_id   uuid NOT NULL REFERENCES searches(id) ON DELETE CASCADE,
    person_id   uuid NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    score       numeric(4,3) NOT NULL CHECK (score BETWEEN 0 AND 1),
    reasons     jsonb NOT NULL,                                -- ["name sounds like 'Maria'", ...]
    decision    text NOT NULL DEFAULT 'pending' CHECK (decision IN ('pending', 'confirmed', 'rejected')),
    reviewed_by uuid REFERENCES staff(id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (search_id, person_id)                              -- never suggested twice for one search
);
CREATE INDEX match_candidates_person ON match_candidates (person_id);

-- ---------- matching ----------
-- Returns signals for up to 100 people; MatchingService ranks them and writes plain-English reasons.
CREATE FUNCTION find_candidates(q_name text, q_age_min int, q_age_max int)
RETURNS TABLE (person_id uuid, name_similarity real, words_sound_alike int,
               nickname_match boolean, age_close boolean, minor boolean)
LANGUAGE sql STABLE SET search_path = public, pg_temp AS $$
    WITH q AS (                                    -- the searched name, cleaned the same way as stored names
        SELECT lower(unaccent(btrim(q_name))) AS name
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
    -- A rough order using ALL signals before the cut, so a strong sound or nickname match
    -- isn't dropped for a weaker spelling match, and the right age counts too: with 100+ people
    -- of the same name, the one of the right age must not be cut off (unknown age = no change).
    -- MatchingService does the real ranking.
    ORDER BY s.sim + 0.3 * s.sounds + CASE WHEN s.nick THEN 0.5 ELSE 0 END
             + CASE s.age_ok WHEN true THEN 0.5 WHEN false THEN -0.5 ELSE 0 END DESC, s.pid
    LIMIT 100
$$;

-- ---------- slips stay with their person ----------
-- A pre-printed slip (no person yet) can be handed out once; after that its person, code and PIN
-- can't change, because whoever holds the slip would then read another person's answers.
-- (Failed tries, the lock and voiding still change; a lost slip gets a new code instead.)
CREATE FUNCTION reunion_codes_keep_person() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.person_id IS NOT NULL AND (NEW.person_id IS DISTINCT FROM OLD.person_id
                                      OR NEW.code <> OLD.code OR NEW.pin_hash <> OLD.pin_hash) THEN
        RAISE EXCEPTION 'a handed-out slip can''t be moved to another person; issue a new slip' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER reunion_codes_keep_person
    BEFORE UPDATE ON reunion_codes
    FOR EACH ROW EXECUTE FUNCTION reunion_codes_keep_person();

-- ---------- what the app may do ----------
GRANT SELECT, INSERT, UPDATE ON match_candidates TO homeward_app;
GRANT SELECT ON nicknames, search_candidates TO homeward_app;
GRANT EXECUTE ON FUNCTION find_candidates(text, int, int) TO homeward_app;

-- Flags: add them and lift them, but never rewrite who or what they're about.
GRANT SELECT, INSERT ON safety_flags TO homeward_app;
GRANT UPDATE (cleared_at, cleared_by) ON safety_flags TO homeward_app;
