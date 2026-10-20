-- V1__base_tables.sql (database step 2): the basic tables.
-- Moved unchanged from db/draft/full_schema_draft.sql; safe matching (V2) and consent & protection (V3) follow.
-- Tested on PostgreSQL 18.6 (same as Neon) with scripts/db-test.sh.
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

-- ---------- searches ----------
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
GRANT SELECT, INSERT, UPDATE ON shelters, staff, persons, reunion_codes, searches TO homeward_app;
GRANT SELECT ON person_history TO homeward_app;     -- history is written by the trigger only

-- The audit log is append-only for the app: no UPDATE, no DELETE, and it can't set the time.
GRANT SELECT ON audit_log TO homeward_app;
GRANT INSERT (staff_id, action, target) ON audit_log TO homeward_app;

-- Only the retention job can delete, and it still can't touch the audit log.
GRANT SELECT, DELETE ON persons, searches, reunion_codes TO homeward_maint;
