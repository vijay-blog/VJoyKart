-- Retains only the minimum contact data necessary to verify a public deletion request.
CREATE TABLE IF NOT EXISTS account_deletion_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) NULL,
  phone VARCHAR(40) NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL
);

SET @sql = (SELECT IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'deleted_at'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;
