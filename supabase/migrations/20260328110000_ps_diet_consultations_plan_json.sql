ALTER TABLE ps_diet_consultations
  ADD COLUMN IF NOT EXISTS plan_json jsonb;
