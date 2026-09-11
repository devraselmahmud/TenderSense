ALTER TABLE bracit_profiles
  ADD COLUMN minimum_tender_budget NUMERIC(18,2) NOT NULL DEFAULT 100000.00
    CHECK (minimum_tender_budget >= 0);
