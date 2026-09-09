CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL CHECK (role IN ('BD_EXECUTIVE','BD_UNIT_LEAD','ADMIN')),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bracit_profiles (
  id BIGSERIAL PRIMARY KEY,
  version INTEGER NOT NULL UNIQUE,
  turnover_amount NUMERIC(18,2) NOT NULL CHECK (turnover_amount >= 0),
  currency CHAR(3) NOT NULL,
  services JSONB NOT NULL DEFAULT '[]',
  past_projects JSONB NOT NULL DEFAULT '[]',
  certifications JSONB NOT NULL DEFAULT '[]',
  geographies TEXT[] NOT NULL DEFAULT '{}',
  created_by BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE eligibility_rules (
  id BIGSERIAL PRIMARY KEY,
  rule_type VARCHAR(32) NOT NULL CHECK (rule_type IN ('TURNOVER','CERTIFICATION','GEOGRAPHY')),
  operator VARCHAR(32) NOT NULL,
  value TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE grade_thresholds (
  grade CHAR(1) PRIMARY KEY CHECK (grade IN ('S','A','B')),
  minimum_score NUMERIC(4,3) NOT NULL CHECK (minimum_score BETWEEN 0 AND 1)
);

CREATE TABLE tenders (
  id BIGSERIAL PRIMARY KEY,
  source VARCHAR(32) NOT NULL,
  external_id VARCHAR(255) NOT NULL,
  title TEXT NOT NULL,
  procuring_entity TEXT,
  description TEXT NOT NULL,
  source_url TEXT,
  publish_date DATE,
  deadline_date DATE,
  geography TEXT,
  required_turnover NUMERIC(18,2),
  required_certifications TEXT[] NOT NULL DEFAULT '{}',
  similarity_score NUMERIC(5,4),
  matched_segment TEXT,
  grade CHAR(1) CHECK (grade IN ('S','A','B','C')),
  eligibility_status VARCHAR(24) CHECK (eligibility_status IN ('ELIGIBLE','INELIGIBLE','NEEDS_VERIFICATION')),
  eligibility_reason TEXT,
  summary TEXT,
  profile_version INTEGER,
  status VARCHAR(24) NOT NULL DEFAULT 'NEW',
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source, external_id)
);

CREATE TABLE tender_rule_results (
  id BIGSERIAL PRIMARY KEY,
  tender_id BIGINT NOT NULL REFERENCES tenders(id) ON DELETE CASCADE,
  rule_type VARCHAR(32) NOT NULL,
  outcome VARCHAR(24) NOT NULL CHECK (outcome IN ('PASS','FAIL','NOT_VERIFIABLE')),
  reason TEXT NOT NULL
);

CREATE TABLE bid_decisions (
  id BIGSERIAL PRIMARY KEY,
  tender_id BIGINT NOT NULL REFERENCES tenders(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  decision VARCHAR(8) NOT NULL CHECK (decision IN ('BID','HOLD','SKIP')),
  note TEXT,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE source_health (
  source VARCHAR(32) PRIMARY KEY,
  last_run_at TIMESTAMPTZ,
  last_success_at TIMESTAMPTZ,
  records_pulled INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);

INSERT INTO grade_thresholds(grade, minimum_score) VALUES ('S', .85), ('A', .70), ('B', .55);
INSERT INTO eligibility_rules(rule_type, operator, value) VALUES
  ('TURNOVER', 'PROFILE_GTE_TENDER', ''),
  ('CERTIFICATION', 'PROFILE_CONTAINS_ALL', ''),
  ('GEOGRAPHY', 'PROFILE_CONTAINS', '');
INSERT INTO source_health(source) VALUES ('WORLD_BANK'), ('ADB'), ('EGP');
