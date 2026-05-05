-- Crea tabella storico consulenze AI
CREATE TABLE IF NOT EXISTS ps_diet_consultations (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE
                          DEFAULT auth.uid(),
  prompt      text        NOT NULL,
  response    text        NOT NULL,
  plan_json   jsonb,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ps_diet_consultations_user_date_idx
  ON ps_diet_consultations (user_id, created_at DESC);

GRANT ALL ON TABLE ps_diet_consultations TO authenticated;
GRANT ALL ON TABLE ps_diet_consultations TO service_role;

ALTER TABLE ps_diet_consultations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "dieta: select" ON ps_diet_consultations;
CREATE POLICY "dieta: select" ON ps_diet_consultations FOR SELECT TO authenticated
  USING (auth.uid() = user_id);
DROP POLICY IF EXISTS "dieta: insert" ON ps_diet_consultations;
CREATE POLICY "dieta: insert" ON ps_diet_consultations FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);
DROP POLICY IF EXISTS "dieta: delete" ON ps_diet_consultations;
CREATE POLICY "dieta: delete" ON ps_diet_consultations FOR DELETE TO authenticated
  USING (auth.uid() = user_id);
