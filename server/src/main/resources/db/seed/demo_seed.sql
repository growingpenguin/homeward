-- demo_seed.sql: FICTIONAL demo data only (never real people). Not a migration: Flyway never reads this folder.
-- Three shelters from the demo script in PROPOSAL.md, and staff to log in with.
-- Demo PIN for every staff account: 2468 (public on purpose, listed in the README). Stored only as a BCrypt hash.
-- Only volunteers and leads: a public PIN must never open a coordinator account on the live site.
-- Re-runnable: a second run updates the rows to match this file (e.g. a rotated PIN hash).

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
