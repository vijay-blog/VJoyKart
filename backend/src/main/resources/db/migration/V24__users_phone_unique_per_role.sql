-- Customer OTP accounts are separate from delivery-partner/admin accounts, so the same mobile
-- number may legitimately exist once per role. A global unique index on users.phone
-- (uk_users_phone, created outside Flyway in the shared database) blocked customer sign-in
-- for numbers already registered as a partner/admin. Replace it with uniqueness per role.

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_phone'),
  'ALTER TABLE users DROP INDEX uk_users_phone', 'SELECT 1'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- Only add the per-role unique index when existing data already satisfies it, so startup never fails.
SET @sql = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_phone_role')
  OR EXISTS(SELECT 1 FROM users WHERE phone IS NOT NULL GROUP BY phone, role HAVING COUNT(*) > 1),
  'SELECT 1', 'CREATE UNIQUE INDEX uk_users_phone_role ON users(phone, role)'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
