-- V5__request_message.sql (backend step B4 review): the searcher's message belongs to ONE contact request,
-- not to the whole search, so a second request (or a re-send) can't change what the first person is read.
-- The app may write it once, with the request; V4's column grant gives no UPDATE on it.
ALTER TABLE contact_requests
    ADD COLUMN message text CHECK (message IS NULL OR char_length(message) <= 500);
