-- demo_seed.sql: FICTIONAL demo data only (never real people). Not a migration: Flyway never reads this folder.
-- Three shelters from the demo script in PROPOSAL.md, and staff to log in with.
-- Demo PIN for every staff account: 2468 (public on purpose, listed in the README). Stored only as a BCrypt hash.
-- Re-runnable: ON CONFLICT DO NOTHING.

INSERT INTO shelters (id, name, area, desk_phone) VALUES
  ('d0000000-0000-0000-0000-000000000001', 'Lincoln High Shelter',      'North district', '555-0101'),
  ('d0000000-0000-0000-0000-000000000002', 'Eastside Community Center', 'East district',  '555-0102'),
  ('d0000000-0000-0000-0000-000000000003', 'Westfield High Gym',        'West district',  '555-0103')
ON CONFLICT (id) DO NOTHING;

INSERT INTO staff (id, shelter_id, display_name, role, pin_hash) VALUES
  ('d0000000-0000-0000-0000-0000000000a1', 'd0000000-0000-0000-0000-000000000001', 'Volunteer Alex', 'volunteer',   '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'),
  ('d0000000-0000-0000-0000-0000000000a2', 'd0000000-0000-0000-0000-000000000001', 'Lead Jordan',    'lead',        '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'),
  ('d0000000-0000-0000-0000-0000000000a3', 'd0000000-0000-0000-0000-000000000001', 'Coordinator Sam','coordinator', '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'),
  ('d0000000-0000-0000-0000-0000000000b1', 'd0000000-0000-0000-0000-000000000002', 'Volunteer Riley','volunteer',   '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'),
  ('d0000000-0000-0000-0000-0000000000b2', 'd0000000-0000-0000-0000-000000000002', 'Lead Morgan',    'lead',        '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'),
  ('d0000000-0000-0000-0000-0000000000c1', 'd0000000-0000-0000-0000-000000000003', 'Volunteer Casey','volunteer',   '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce'),
  ('d0000000-0000-0000-0000-0000000000c2', 'd0000000-0000-0000-0000-000000000003', 'Lead Taylor',    'lead',        '$2y$10$sJTbEHKYZuVUzQvN6nEXuOzc/WYJ79nkkqzdk3D//4ZWFqf3RCYce')
ON CONFLICT (id) DO NOTHING;
