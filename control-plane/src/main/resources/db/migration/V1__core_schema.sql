-- Core control-plane schema (MySQL 8 compatible; also runs on H2 in MySQL mode for tests)
CREATE TABLE api_definition (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(80) NOT NULL,
  name VARCHAR(160) NOT NULL,
  owner_team VARCHAR(80) NOT NULL,
  lifecycle VARCHAR(20) NOT NULL,
  description VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_api_slug UNIQUE (slug)
);
CREATE INDEX idx_api_owner_lifecycle ON api_definition (owner_team, lifecycle);

CREATE TABLE api_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  api_id BIGINT NOT NULL,
  label VARCHAR(20) NOT NULL,
  base_path VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL,
  openapi_spec LONGTEXT,
  CONSTRAINT fk_apiversion_api FOREIGN KEY (api_id) REFERENCES api_definition (id),
  CONSTRAINT uk_apiversion_label UNIQUE (api_id, label),
  CONSTRAINT uk_apiversion_path UNIQUE (base_path)
);

CREATE TABLE backend_target (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  api_version_id BIGINT NOT NULL,
  url VARCHAR(500) NOT NULL,
  weight INT NOT NULL DEFAULT 1,
  health_path VARCHAR(200) NOT NULL DEFAULT '/actuator/health',
  CONSTRAINT fk_target_version FOREIGN KEY (api_version_id) REFERENCES api_version (id),
  CONSTRAINT ck_target_weight CHECK (weight BETWEEN 1 AND 100)
);

CREATE TABLE policy_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  api_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(24) NOT NULL,
  config_json LONGTEXT NOT NULL,
  submitted_by VARCHAR(80) NOT NULL,
  approved_by VARCHAR(80),
  comment_text VARCHAR(500),
  created_at TIMESTAMP(3) NOT NULL,
  decided_at TIMESTAMP(3),
  CONSTRAINT fk_policy_api FOREIGN KEY (api_id) REFERENCES api_definition (id),
  CONSTRAINT uk_policy_version UNIQUE (api_id, version_no)
);
CREATE INDEX idx_policy_api_status ON policy_version (api_id, status);

CREATE TABLE consumer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  client_id VARCHAR(80) NOT NULL,
  owner_email VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_consumer_name UNIQUE (name),
  CONSTRAINT uk_consumer_client UNIQUE (client_id)
);

CREATE TABLE subscription (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  consumer_id BIGINT NOT NULL,
  api_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT fk_sub_consumer FOREIGN KEY (consumer_id) REFERENCES consumer (id),
  CONSTRAINT fk_sub_api FOREIGN KEY (api_id) REFERENCES api_definition (id),
  CONSTRAINT uk_sub UNIQUE (consumer_id, api_id)
);

CREATE TABLE api_key (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  consumer_id BIGINT NOT NULL,
  key_prefix VARCHAR(16) NOT NULL,
  key_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT fk_key_consumer FOREIGN KEY (consumer_id) REFERENCES consumer (id),
  CONSTRAINT uk_key_prefix UNIQUE (key_prefix)
);

CREATE TABLE audit_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ts TIMESTAMP(3) NOT NULL,
  actor VARCHAR(80) NOT NULL,
  action VARCHAR(60) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id VARCHAR(60) NOT NULL,
  detail VARCHAR(2000),
  correlation_id VARCHAR(64)
);
CREATE INDEX idx_audit_ts ON audit_event (ts);
CREATE INDEX idx_audit_entity ON audit_event (entity_type, entity_id);

CREATE TABLE idempotency_record (
  idem_key VARCHAR(100) PRIMARY KEY,
  request_hash VARCHAR(64) NOT NULL,
  response_status INT NOT NULL,
  response_body LONGTEXT,
  created_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE traffic_bucket (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL,
  route_slug VARCHAR(80) NOT NULL,
  bucket_start TIMESTAMP(3) NOT NULL,
  requests BIGINT NOT NULL,
  errors_4xx BIGINT NOT NULL,
  errors_5xx BIGINT NOT NULL,
  rate_limited BIGINT NOT NULL,
  auth_failures BIGINT NOT NULL,
  timeouts BIGINT NOT NULL,
  latency_sum_ms BIGINT NOT NULL,
  latency_max_ms BIGINT NOT NULL,
  CONSTRAINT uk_batch_route UNIQUE (batch_id, route_slug, bucket_start)
);
CREATE INDEX idx_bucket_route_time ON traffic_bucket (route_slug, bucket_start);

CREATE TABLE operational_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ts TIMESTAMP(3) NOT NULL,
  type VARCHAR(40) NOT NULL,
  severity VARCHAR(10) NOT NULL,
  route_slug VARCHAR(80),
  message VARCHAR(500) NOT NULL,
  correlation_id VARCHAR(64)
);
CREATE INDEX idx_opevent_ts ON operational_event (ts);
CREATE INDEX idx_opevent_route_type ON operational_event (route_slug, type);
