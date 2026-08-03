-- Widen the role CHECK to allow ADMIN (drop and re-add, as in V10).
-- No admin is seeded: registration rejects ADMIN, so the first admin must be
-- inserted by hand (see README) and can then create others through the API.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('DESIGNER', 'PILOT', 'ADMIN'));
