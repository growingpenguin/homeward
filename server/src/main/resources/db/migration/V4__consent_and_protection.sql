-- V4__consent_and_protection.sql (database step 5): two people are only connected when the found
-- person says yes; children only with a lead's sign-off; safety flags beat everything.
-- Moved unchanged from db/draft/full_schema_draft.sql. These triggers are the last line of defense:
-- even if the backend had a bug, the database refuses the reveal.
-- Tested on PostgreSQL 18.6 (same as Neon) with scripts/db-test.sh.

-- ---------- contact requests ----------
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

-- ---------- a new flag also blocks what was already revealed ----------
CREATE FUNCTION safety_flags_block_reveals() RETURNS trigger
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    IF NEW.cleared_at IS NOT NULL THEN
        RETURN NEW;                                   -- entered already lifted: not an active flag (same as the guard)
    END IF;
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

-- ---------- what the app may do ----------
-- The child check trusts staff.role, so the app must not be able to make anyone a lead:
-- staff accounts are created and changed by the owner only (seed / admin), never by the app.
REVOKE INSERT, UPDATE ON staff FROM homeward_app;
-- Requests: make them and record decisions; never rewrite their id or when they were made.
GRANT SELECT, INSERT ON contact_requests TO homeward_app;
GRANT UPDATE (state, contact_plan, approved_by, guardian_checked, note, decided_at) ON contact_requests TO homeward_app;

-- Trigger functions run without this right; revoking it stops the app attaching them to a table of its own.
REVOKE EXECUTE ON FUNCTION contact_requests_guard(), searches_keep_searcher(), persons_recheck_minor(),
                           safety_flags_block_reveals() FROM PUBLIC;
