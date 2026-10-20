-- demo_seed.sql: FICTIONAL demo data only (never real people). Not a migration: Flyway never reads this folder.
-- Three shelters from the demo script in PROPOSAL.md, and staff to log in with.
-- Demo PIN for every staff account: 2468 (public on purpose, listed in the README). Stored only as a BCrypt hash.
-- Only volunteers and leads: a public PIN must never open a coordinator account on the live site.
-- Re-runnable: a second run updates shelters and staff to match this file (e.g. a rotated PIN hash);
-- the 200 people at the end are only added once and never overwritten (see there).

INSERT INTO shelters (id, name, area, desk_phone) VALUES
  ('d0000000-0000-0000-0000-000000000001', 'Lincoln High Shelter',      'North district', '555-0101'),
  ('d0000000-0000-0000-0000-000000000002', 'Eastside Community Center', 'East district',  '555-0102'),
  ('d0000000-0000-0000-0000-000000000003', 'Westfield High Gym',        'West district',  '555-0103')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, area = EXCLUDED.area, desk_phone = EXCLUDED.desk_phone;

WITH demo_pin AS (SELECT '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'::text AS pin_hash)    -- BCrypt of 2468, defined once
INSERT INTO staff (id, shelter_id, display_name, role, pin_hash)
SELECT v.id::uuid, v.shelter_id::uuid, v.display_name, v.role, demo_pin.pin_hash
FROM (VALUES
  ('d0000000-0000-0000-0000-0000000000a1', 'd0000000-0000-0000-0000-000000000001', 'Volunteer Alex',  'volunteer'),
  ('d0000000-0000-0000-0000-0000000000a2', 'd0000000-0000-0000-0000-000000000001', 'Lead Jordan',     'lead'),
  ('d0000000-0000-0000-0000-0000000000b1', 'd0000000-0000-0000-0000-000000000002', 'Volunteer Riley', 'volunteer'),
  ('d0000000-0000-0000-0000-0000000000b2', 'd0000000-0000-0000-0000-000000000002', 'Lead Morgan',     'lead'),
  ('d0000000-0000-0000-0000-0000000000c1', 'd0000000-0000-0000-0000-000000000003', 'Volunteer Casey', 'volunteer'),
  ('d0000000-0000-0000-0000-0000000000c2', 'd0000000-0000-0000-0000-000000000003', 'Lead Taylor',     'lead')
) AS v(id, shelter_id, display_name, role), demo_pin
ON CONFLICT (id) DO UPDATE SET shelter_id = EXCLUDED.shelter_id, display_name = EXCLUDED.display_name,
                               role = EXCLUDED.role, pin_hash = EXCLUDED.pin_hash;

-- A crowd of 200 FICTIONAL people (made-up first + last name combinations), so matching has
-- something realistic to sort through: accents, lower case, nicknames, unknown ages, all three
-- privacy choices, spread over the three shelters. Fixed ids, so a second run adds nothing.
-- (People registered during the demo are left alone: DO NOTHING, not DO UPDATE.)
WITH firsts AS (SELECT ARRAY['Maria', 'José', 'ana', 'Luis', 'Grace', 'Tom', 'Aisha', 'Dmitri', 'Lupe', 'Bill',
                             'Katie', 'Nguyen', 'Fatima', 'Pedro', 'Mei', 'Samuel', 'Rosa', 'Ivan', 'Leila', 'Joe'] AS a),
     lasts  AS (SELECT ARRAY['Garcia', 'Pérez', 'Johnson', 'Okafor', 'Nguyen', 'Smith', 'Rodriguez', 'Haddad',
                             'Kowalski', 'brown', 'Martinez', 'Lee', 'Ali', 'Silva', 'Chen'] AS a)
INSERT INTO persons (id, shelter_id, preferred_name, approx_age, visibility, entered_by)
SELECT ('d1000000-0000-0000-0000-' || lpad(i::text, 12, '0'))::uuid,
       ('d0000000-0000-0000-0000-00000000000' || (1 + i % 3))::uuid,
       firsts.a[1 + i % 20] || ' ' || lasts.a[1 + (i * 7) % 15],
       CASE WHEN i % 9 = 0 THEN NULL ELSE 18 + (i * 13) % 70 END,                 -- every 9th age unknown
       CASE WHEN i % 10 = 0 THEN 'hidden' WHEN i % 4 = 0 THEN 'findable' ELSE 'ask' END,
       ('d0000000-0000-0000-0000-0000000000' || (ARRAY['a1', 'b1', 'c1'])[1 + i % 3])::uuid
FROM generate_series(1, 200) AS i, firsts, lasts
ON CONFLICT (id) DO NOTHING;
