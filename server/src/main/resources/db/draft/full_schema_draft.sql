-- full_schema_draft.sql: the complete tested schema (was V1__init.sql). NOT a migration: Flyway never reads this folder.
-- It is split into db/migration/V1..V3 step by step (DATABASE_PLAN.md) and used to prove the split changes nothing.
CREATE EXTENSION IF NOT EXISTS pg_trgm;        -- similar spelling
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;  -- sounds alike (dmetaphone)
CREATE EXTENSION IF NOT EXISTS unaccent;       -- "José" matches "Jose"

-- ---------- helpers ----------
-- TRUE only if the text has at least one visible character (spaces, tabs and newlines don't count).
CREATE FUNCTION has_text(t text) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT coalesce(t ~ '[^[:space:]]', false)
$$;

CREATE FUNCTION all_have_text(a text[]) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT cardinality(a) > 0 AND coalesce((SELECT bool_and(has_text(x)) FROM unnest(a) AS x), false)
$$;

-- ---------- shelters & staff ----------
CREATE TABLE shelters (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text NOT NULL CHECK (has_text(name)),
    area        text,
    desk_phone  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE staff (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    shelter_id   uuid NOT NULL REFERENCES shelters(id),
    display_name text NOT NULL CHECK (has_text(display_name)),
    role         text NOT NULL CHECK (role IN ('volunteer', 'lead', 'coordinator')),
    pin_hash     text NOT NULL CHECK (pin_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$')  -- BCrypt only
);

-- ---------- persons ----------
CREATE TABLE persons (
    id                 uuid PRIMARY KEY,                       -- made by the browser: a re-send is ignored
    shelter_id         uuid NOT NULL REFERENCES shelters(id),  -- the desk that registered them
    preferred_name     text NOT NULL
                       CHECK (has_text(preferred_name) AND length(preferred_name) <= 100),
    other_names        text[] NOT NULL DEFAULT '{}',           -- nicknames, maiden names, spellings
    approx_age         int CHECK (approx_age BETWEEN 0 AND 120),   -- NULL = unknown
    age_is_estimate    boolean NOT NULL DEFAULT true,
    is_minor           boolean,                                -- NULL = not sure
    unaccompanied      boolean NOT NULL DEFAULT false,         -- a child with no adult: top of the lead's list
    guardian_person_id uuid REFERENCES persons(id) ON DELETE SET NULL,
    home_area          text,
    description        text,
    phone              text,
    visibility         text NOT NULL DEFAULT 'ask'
                       CHECK (visibility IN ('findable', 'ask', 'hidden')),
    source             text NOT NULL DEFAULT 'app'
                       CHECK (source IN ('app', 'paper', 'import')),
    paper_ref          text,
    recorded_at        timestamptz NOT NULL DEFAULT now(),
    entered_by         uuid NOT NULL REFERENCES staff(id),
    updated_by         uuid REFERENCES staff(id),
    status             text NOT NULL DEFAULT 'present'
                       CHECK (status IN ('present', 'moved', 'left')),
    created_at         timestamptz NOT NULL DEFAULT now(),
    -- filled by a trigger, used only for matching
    search_names       text NOT NULL DEFAULT '',
    name_words         text[] NOT NULL DEFAULT '{}',
    name_sounds        text[] NOT NULL DEFAULT '{}',
    CHECK (source <> 'paper' OR has_text(paper_ref)),          -- a paper record must point back to its sheet
    CHECK (NOT (is_minor = false AND approx_age < 18)),        -- "adult" can't have a child's age
    CHECK (NOT (is_minor = true  AND approx_age >= 18))        -- "minor" can't have an adult's age
    -- Deliberately NO columns for: legal ID, date of birth, SSN, immigration status, photo.
);

-- Treated as a child unless we KNOW they're an adult.
-- A known age decides; with no age, only a volunteer marking "adult" (is_minor = false) makes them an adult.
CREATE FUNCTION treated_as_minor(p persons) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN p.approx_age IS NOT NULL THEN p.approx_age < 18
                ELSE coalesce(p.is_minor, true) END
$$;

-- Keep the matching columns up to date on EVERY insert and update, so they can't be edited by hand:
-- all names in lower case without accents, each word, and how each word sounds.
CREATE FUNCTION persons_fill_names() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    NEW.search_names := lower(unaccent(btrim(NEW.preferred_name || ' ' || array_to_string(NEW.other_names, ' '))));
    NEW.name_words   := ARRAY(
        SELECT DISTINCT w FROM unnest(regexp_split_to_array(NEW.search_names, '\s+')) AS w WHERE w <> '');
    NEW.name_sounds  := ARRAY(
        SELECT DISTINCT dmetaphone(w) FROM unnest(NEW.name_words) AS w);
    RETURN NEW;
END $$;

CREATE TRIGGER persons_fill_names
    BEFORE INSERT OR UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION persons_fill_names();

CREATE INDEX persons_names_trgm  ON persons USING gin (search_names gin_trgm_ops);
CREATE INDEX persons_name_words  ON persons USING gin (name_words);
CREATE INDEX persons_name_sounds ON persons USING gin (name_sounds);
CREATE INDEX persons_shelter     ON persons (shelter_id);

-- ---------- edit snapshots: every edit keeps the "before" version ----------
CREATE TABLE person_history (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_id  uuid NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    before     jsonb NOT NULL,
    changed_by uuid REFERENCES staff(id),
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX person_history_person ON person_history (person_id);

-- SECURITY DEFINER: the trigger writes history with the owner's rights,
-- so the app itself has no INSERT on person_history and can't fake it.
CREATE FUNCTION persons_keep_history() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
BEGIN
    INSERT INTO person_history (person_id, before, changed_by)
    VALUES (OLD.id, to_jsonb(OLD) - 'search_names' - 'name_words' - 'name_sounds', NEW.updated_by);
    RETURN NEW;
END $$;

CREATE TRIGGER persons_keep_history
    BEFORE UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION persons_keep_history();

-- Nobody may call it directly or attach it to another table (e.g. a temp table) to fake history.
REVOKE EXECUTE ON FUNCTION persons_keep_history() FROM PUBLIC;

-- ---------- reunion codes (for people with no phone) ----------
CREATE TABLE reunion_codes (
    code            text PRIMARY KEY
                    CHECK (code ~ '^HW-[2-9A-HJ-NP-Z]{4}$'),         -- no 0/O or 1/I
    person_id       uuid REFERENCES persons(id) ON DELETE CASCADE,  -- NULL = pre-printed, not handed out yet
    pin_hash        text NOT NULL
                    CHECK (pin_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),  -- BCrypt only, never the PIN
    failed_attempts int NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until    timestamptz,
    voided_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

-- A person has at most one working code; a reissued slip voids the old one.
CREATE UNIQUE INDEX one_active_code_per_person
    ON reunion_codes (person_id) WHERE voided_at IS NULL AND person_id IS NOT NULL;

-- ---------- searches, matches, contact requests ----------
CREATE TABLE searches (
    id                 uuid PRIMARY KEY,                       -- made by the browser
    shelter_id         uuid NOT NULL REFERENCES shelters(id),  -- volunteers only see their own shelter's searches
    searcher_person_id uuid REFERENCES persons(id) ON DELETE CASCADE,
    sought_names       text[] NOT NULL CHECK (all_have_text(sought_names)),
    sought_age_min     int CHECK (sought_age_min BETWEEN 0 AND 120),
    sought_age_max     int CHECK (sought_age_max BETWEEN 0 AND 120),
    relationship       text,
    last_known_place   text,
    message            text,
    status             text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'matched', 'closed')),
    entered_by         uuid NOT NULL REFERENCES staff(id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    CHECK (sought_age_min <= sought_age_max)
);
CREATE INDEX searches_shelter  ON searches (shelter_id);
CREATE INDEX searches_searcher ON searches (searcher_person_id);

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

CREATE TABLE contact_requests (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    search_id        uuid NOT NULL REFERENCES searches(id) ON DELETE CASCADE,
    person_id        uuid NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    state            text NOT NULL DEFAULT 'pending'
                     CHECK (state IN ('pending', 'connected', 'safe_only', 'declined', 'blocked')),
    contact_plan     text,                                     -- how to meet; only after "Connect"
    approved_by      uuid REFERENCES staff(id),                -- the lead, for a child
    guardian_checked boolean NOT NULL DEFAULT false,
    note             text,
    decided_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (search_id, person_id),
    CHECK (state <> 'connected' OR has_text(contact_plan))     -- "connected" always says how to meet
);
CREATE INDEX contact_requests_person ON contact_requests (person_id);

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

-- ---------- the last line of defense for connecting people ----------
-- search_path is pinned (pg_temp last) in every function that reads tables,
-- so a temporary table named e.g. safety_flags can't stand in for the real one.
CREATE FUNCTION contact_requests_guard() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
DECLARE
    p persons;              -- the person being looked for
    sp persons;             -- the person searching (if registered)
    searcher uuid;
    approver_role text;
BEGIN
    -- 0. A request belongs to one search and one person forever; another pair needs a new request.
    IF TG_OP = 'UPDATE' AND (NEW.person_id <> OLD.person_id OR NEW.search_id <> OLD.search_id) THEN
        RAISE EXCEPTION 'a request can''t be moved to another person or search' USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.state NOT IN ('connected', 'safe_only') THEN
        RETURN NEW;                                  -- only revealing states are checked
    END IF;

    -- Lock the rows a new flag would lock too, so a flag and a "Connect" can't slip past each other.
    SELECT * INTO p FROM persons WHERE id = NEW.person_id FOR UPDATE;
    SELECT searcher_person_id INTO searcher FROM searches WHERE id = NEW.search_id FOR UPDATE;
    IF searcher IS NOT NULL THEN
        SELECT * INTO sp FROM persons WHERE id = searcher FOR UPDATE;
    END IF;

    -- 1. Safety flags beat everything, including "findable" and an earlier "Connect":
    --    a flag on the person, on this search, or on the searcher.
    IF EXISTS (SELECT 1 FROM safety_flags f
               WHERE f.cleared_at IS NULL
                 AND (f.person_id = NEW.person_id
                      OR f.search_id = NEW.search_id
                      OR f.person_id = searcher)) THEN
        RAISE EXCEPTION 'blocked by a safety flag' USING ERRCODE = 'check_violation';
    END IF;

    -- 2. A child is only connected with a lead's sign-off, a guardian check and a note,
    --    whether the child is the one being looked for or the one searching ("Connect" reveals both sides).
    IF NEW.state = 'connected' AND (treated_as_minor(p) OR (searcher IS NOT NULL AND treated_as_minor(sp))) THEN
        SELECT role INTO approver_role FROM staff WHERE id = NEW.approved_by;
        IF approver_role IS NULL OR approver_role NOT IN ('lead', 'coordinator')
           OR NOT NEW.guardian_checked
           OR NOT has_text(NEW.note) THEN
            RAISE EXCEPTION 'a child needs a lead sign-off, a guardian check and a note'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER contact_requests_guard
    BEFORE INSERT OR UPDATE ON contact_requests
    FOR EACH ROW EXECUTE FUNCTION contact_requests_guard();

-- The person searching can't be swapped once it matters: a "yes" given to one searcher
-- must never be handed to another (possibly a child, who would then skip the lead check).
CREATE FUNCTION searches_keep_searcher() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    IF NEW.searcher_person_id IS DISTINCT FROM OLD.searcher_person_id
       AND (OLD.searcher_person_id IS NOT NULL
            OR EXISTS (SELECT 1 FROM contact_requests cr
                       WHERE cr.search_id = OLD.id AND cr.state IN ('connected', 'safe_only'))) THEN
        RAISE EXCEPTION 'the person searching can''t be changed; start a new search' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER searches_keep_searcher
    BEFORE UPDATE OF searcher_person_id ON searches
    FOR EACH ROW EXECUTE FUNCTION searches_keep_searcher();

-- A correction that makes someone count as a child (e.g. age 40 corrected to 14) re-checks every
-- "Connect" involving them, found or searching: any without a lead's sign-off goes back to "pending".
CREATE FUNCTION persons_recheck_minor() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    IF treated_as_minor(NEW) AND NOT treated_as_minor(OLD) THEN
        UPDATE contact_requests cr
        SET state = 'pending', decided_at = now()
        WHERE cr.state = 'connected'
          AND (cr.person_id = NEW.id
               OR cr.search_id IN (SELECT s.id FROM searches s WHERE s.searcher_person_id = NEW.id))
          AND NOT (cr.guardian_checked AND has_text(cr.note)
                   AND EXISTS (SELECT 1 FROM staff st
                               WHERE st.id = cr.approved_by AND st.role IN ('lead', 'coordinator')));
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER persons_recheck_minor
    AFTER UPDATE ON persons
    FOR EACH ROW EXECUTE FUNCTION persons_recheck_minor();

-- A new flag locks the same rows as the guard, then blocks anything it already revealed.
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

CREATE FUNCTION safety_flags_block_reveals() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    UPDATE contact_requests cr
    SET state = 'blocked', decided_at = now()
    WHERE cr.state IN ('connected', 'safe_only')
      AND (cr.person_id = NEW.person_id                         -- the flagged person was revealed
           OR cr.search_id = NEW.search_id                      -- the flagged search got an answer
           OR cr.search_id IN (SELECT s.id FROM searches s      -- the flagged person was the searcher
                               WHERE s.searcher_person_id = NEW.person_id));
    RETURN NEW;
END $$;

CREATE TRIGGER safety_flags_block_reveals
    AFTER INSERT ON safety_flags
    FOR EACH ROW EXECUTE FUNCTION safety_flags_block_reveals();

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
    -- isn't dropped for a weaker spelling match. MatchingService does the real ranking.
    ORDER BY s.sim + 0.3 * s.sounds + CASE WHEN s.nick THEN 0.5 ELSE 0 END DESC, s.pid
    LIMIT 100
$$;

-- ---------- audit log ----------
CREATE TABLE audit_log (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_id uuid NOT NULL REFERENCES staff(id),   -- who
    action   text NOT NULL CHECK (action IN (
                 'SEARCH', 'CONTACT_REQUEST', 'DECISION', 'CODE_LOOKUP', 'CODE_LOOKUP_FAILED',
                 'CODE_ISSUED', 'CODE_REISSUED', 'REVEALED_CONTACT', 'REVEALED_SAFE',
                 'MINOR_RELEASED', 'FLAG_ADDED', 'FLAG_CLEARED')),   -- a fixed list: no free text, so no PINs or places
    target   uuid,                                 -- no FK: the entry outlives the data it points to
    at       timestamptz NOT NULL DEFAULT now()    -- set by the database, not the app
);

-- ---------- database users ----------
-- Flyway runs as the owner. The app connects as homeward_app; the retention job as homeward_maint.
-- Created here with SQL on purpose: on Neon, users made in the console get extra rights (neon_superuser).
-- Passwords are set outside the migration.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'homeward_app')   THEN CREATE ROLE homeward_app LOGIN;   END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'homeward_maint') THEN CREATE ROLE homeward_maint LOGIN; END IF;
END $$;

GRANT USAGE ON SCHEMA public TO homeward_app, homeward_maint;

-- The app can read and write, but never delete people.
GRANT SELECT, INSERT, UPDATE ON shelters, staff, persons, reunion_codes, searches,
      match_candidates, contact_requests TO homeward_app;
GRANT SELECT ON person_history, nicknames, search_candidates TO homeward_app;     -- history is written by the trigger only
GRANT EXECUTE ON FUNCTION find_candidates(text, int, int) TO homeward_app;

-- Flags: add them and lift them, but never rewrite who or what they're about.
GRANT SELECT, INSERT ON safety_flags TO homeward_app;
GRANT UPDATE (cleared_at, cleared_by) ON safety_flags TO homeward_app;

-- The audit log is append-only for the app: no UPDATE, no DELETE, and it can't set the time.
GRANT SELECT ON audit_log TO homeward_app;
GRANT INSERT (staff_id, action, target) ON audit_log TO homeward_app;

-- Only the retention job can delete, and it still can't touch the audit log.
GRANT SELECT, DELETE ON persons, searches, reunion_codes TO homeward_maint;
