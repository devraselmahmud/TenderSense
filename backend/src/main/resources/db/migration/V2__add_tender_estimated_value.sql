ALTER TABLE tenders
  ADD COLUMN estimated_value NUMERIC(18,2) CHECK (estimated_value >= 0),
  ADD COLUMN estimated_value_currency CHAR(3) CHECK (estimated_value_currency ~ '^[A-Z]{3}$');
