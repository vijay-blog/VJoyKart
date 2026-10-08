SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'product_images'
    ),
    'SELECT 1',
    'CREATE TABLE product_images (
       product_id BIGINT PRIMARY KEY,
       image_data LONGBLOB NOT NULL,
       content_type VARCHAR(100) NOT NULL,
       updated_at TIMESTAMP(6) NOT NULL,
       CONSTRAINT fk_product_image_product
         FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
     )'
  )
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'product_images'
        AND column_name = 'image_data'
    ),
    'SELECT 1',
    'ALTER TABLE product_images ADD COLUMN image_data LONGBLOB NULL'
  )
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'product_images'
        AND column_name = 'image_data'
    ),
    'UPDATE product_images SET image_data = X'''' WHERE image_data IS NULL',
    'SELECT 1'
  )
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'product_images'
        AND column_name = 'image_data'
        AND is_nullable = 'YES'
    ),
    'ALTER TABLE product_images MODIFY COLUMN image_data LONGBLOB NOT NULL',
    'SELECT 1'
  )
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'product_images'
        AND column_name = 'content_type'
    ),
    'SELECT 1',
    'ALTER TABLE product_images ADD COLUMN content_type VARCHAR(100) NOT NULL DEFAULT ''image/jpeg'''
  )
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @sql = (
  SELECT IF(
    EXISTS(
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'product_images'
        AND column_name = 'updated_at'
    ),
    'SELECT 1',
    'ALTER TABLE product_images ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)'
  )
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;
