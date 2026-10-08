CREATE TABLE IF NOT EXISTS product_images (
  product_id BIGINT PRIMARY KEY,
  image_data LONGBLOB NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_product_image_product
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
