-- cm_priorities non era stata inclusa nella blindatura di
-- 20260611190000_lock_legacy_tables.sql: aveva solo accesso in lettura,
-- quindi il CRUD "Priorità" in index.html falliva con 42501 ("new row
-- violates row-level security policy") su INSERT/UPDATE/DELETE.
--
-- Applica lo stesso pattern già usato per le altre tabelle cm_/ts_/hb_/ps_:
-- accesso completo riservato al solo proprietario (garsal1971@gmail.com).

DO $$
DECLARE
  p record;
BEGIN
  FOR p IN
    SELECT policyname FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'cm_priorities'
  LOOP
    EXECUTE format('DROP POLICY %I ON public.cm_priorities', p.policyname);
  END LOOP;
END $$;

ALTER TABLE public.cm_priorities ENABLE ROW LEVEL SECURITY;

CREATE POLICY cm_priorities_garsal_only ON public.cm_priorities
  FOR ALL USING (public.is_garsal()) WITH CHECK (public.is_garsal());
