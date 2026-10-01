-- Automatic delivery-partner assignment from the fixed VJoyKart Store.
-- Additive only: no existing column/data is dropped. Every step is idempotent because the
-- shared production database may already contain some objects (see V6/V15 conventions).

-- orders.delivery_status : delivery lifecycle, independent of payment_status
SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'delivery_status'),
  'SELECT 1',
  'ALTER TABLE orders ADD COLUMN delivery_status VARCHAR(30) NOT NULL DEFAULT ''ORDER_PLACED'''));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'assigned_at'),
  'SELECT 1', 'ALTER TABLE orders ADD COLUMN assigned_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'packing_at'),
  'SELECT 1', 'ALTER TABLE orders ADD COLUMN packing_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'on_the_way_at'),
  'SELECT 1', 'ALTER TABLE orders ADD COLUMN on_the_way_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'arrived_at'),
  'SELECT 1', 'ALTER TABLE orders ADD COLUMN arrived_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'dispatch_last_attempt_at'),
  'SELECT 1', 'ALTER TABLE orders ADD COLUMN dispatch_last_attempt_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_delivery_status'),
  'SELECT 1', 'CREATE INDEX idx_orders_delivery_status ON orders(delivery_status, created_at)'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- delivery_partner_profiles : latest reported location + the single active delivery
SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'delivery_partner_profiles' AND column_name = 'current_latitude'),
  'SELECT 1', 'ALTER TABLE delivery_partner_profiles ADD COLUMN current_latitude DOUBLE NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'delivery_partner_profiles' AND column_name = 'current_longitude'),
  'SELECT 1', 'ALTER TABLE delivery_partner_profiles ADD COLUMN current_longitude DOUBLE NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'delivery_partner_profiles' AND column_name = 'location_updated_at'),
  'SELECT 1', 'ALTER TABLE delivery_partner_profiles ADD COLUMN location_updated_at TIMESTAMP(6) NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'delivery_partner_profiles' AND column_name = 'active_order_id'),
  'SELECT 1', 'ALTER TABLE delivery_partner_profiles ADD COLUMN active_order_id BIGINT NULL'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- One order can be the active delivery of at most one partner (NULLs are allowed many times).
SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'delivery_partner_profiles' AND index_name = 'uk_profiles_active_order'),
  'SELECT 1', 'CREATE UNIQUE INDEX uk_profiles_active_order ON delivery_partner_profiles(active_order_id)'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @sql = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'delivery_partner_profiles' AND index_name = 'idx_profiles_dispatch'),
  'SELECT 1', 'CREATE INDEX idx_profiles_dispatch ON delivery_partner_profiles(available, location_updated_at)'));
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

-- Backfill the delivery lifecycle for existing orders from the legacy order status.
UPDATE orders SET delivery_status = 'DELIVERED', delivered_at = COALESCE(delivered_at, updated_at)
  WHERE status = 'DELIVERED' AND delivery_status = 'ORDER_PLACED';
UPDATE orders SET delivery_status = 'CANCELLED'
  WHERE status = 'CANCELLED' AND delivery_status = 'ORDER_PLACED';
UPDATE orders SET delivery_status = 'ON_THE_WAY', on_the_way_at = COALESCE(out_for_delivery_at, picked_up_at, updated_at)
  WHERE status IN ('OUT_FOR_DELIVERY', 'PICKED_UP') AND delivery_partner_id IS NOT NULL AND delivery_status = 'ORDER_PLACED';
UPDATE orders SET delivery_status = 'DELIVERY_ASSIGNED', assigned_at = COALESCE(accepted_at, updated_at)
  WHERE status IN ('ASSIGNED', 'ACCEPTED') AND delivery_partner_id IS NOT NULL AND delivery_status = 'ORDER_PLACED';

-- Record each partner's in-flight legacy delivery (latest one) so they are not double-booked.
UPDATE delivery_partner_profiles p
  JOIN (SELECT delivery_partner_id, MAX(id) AS order_id FROM orders
        WHERE delivery_partner_id IS NOT NULL AND delivery_status IN ('DELIVERY_ASSIGNED', 'PACKING', 'ON_THE_WAY', 'ARRIVED')
        GROUP BY delivery_partner_id) active ON active.delivery_partner_id = p.user_id
  SET p.active_order_id = active.order_id
  WHERE p.active_order_id IS NULL;
