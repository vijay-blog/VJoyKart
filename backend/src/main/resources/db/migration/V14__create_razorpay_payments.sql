CREATE TABLE IF NOT EXISTS payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  gateway VARCHAR(30) NOT NULL,
  gateway_order_id VARCHAR(120) NOT NULL,
  gateway_payment_id VARCHAR(120),
  gateway_signature VARCHAR(300),
  amount DECIMAL(19,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL,
  failure_reason VARCHAR(500),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT uk_payment_gateway_order UNIQUE (gateway_order_id)
);

CREATE INDEX idx_payments_order ON payments(order_id);
