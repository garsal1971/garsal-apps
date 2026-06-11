-- =====================================================================
-- Blindatura tabelle legacy: accesso riservato al proprietario
--
-- L'audit di pg_policies ha rivelato policy "USING (true)" su molte
-- tabelle storiche (ts_, hb_, ps_, cm_): erano leggibili e scrivibili
-- da CHIUNQUE avesse la anon key (pubblica nei file HTML), inclusa
-- Rosa. cm_notification_offsets era addirittura senza RLS.
--
-- Dopo questa migration:
--   - le tabelle qui sotto sono accessibili SOLO a garsal1971@gmail.com
--     (le app web inviano sempre il JWT utente ricevuto dal launcher)
--   - le edge functions non sono toccate (usano la service role)
--   - SmartBlocker non è toccato: le sue policy anon su
--     cm_notification_queue restano, e le RPC task_complete /
--     get_smart_block_token sono SECURITY DEFINER
--   - weight-quest ora richiede il login (prima ps_weight_tracking era
--     scrivibile da chiunque)
--
-- Richiede public.is_garsal() creata da 20260611160000_rosa_readonly_access.
-- =====================================================================

DO $$
DECLARE
  t text;
  p record;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'cm_categories', 'ts_tasks', 'ts_history', 'ts_settings', 'ts_notes',
    'hb_habits', 'hb_completions', 'ps_weight_tracking',
    'cm_reminder_presets', 'cm_apps', 'cm_rewards', 'cm_rewards_log',
    'cm_notification_rules'
  ] LOOP
    IF to_regclass('public.' || t) IS NULL THEN
      RAISE NOTICE 'tabella % non trovata, salto', t;
      CONTINUE;
    END IF;

    -- Rimuove TUTTE le policy esistenti (incluse eventuali policy
    -- INSERT con qual NULL non visibili nell'audit iniziale)
    FOR p IN
      SELECT policyname FROM pg_policies
      WHERE schemaname = 'public' AND tablename = t
    LOOP
      EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
    END LOOP;

    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %I ON public.%I FOR ALL USING (public.is_garsal()) WITH CHECK (public.is_garsal())',
      t || '_garsal_only', t);

    RAISE NOTICE 'tabella %: accesso ristretto al proprietario', t;
  END LOOP;
END $$;

-- cm_notification_queue: rimossa SOLO la policy generica "authenticated".
-- Le eventuali policy anon usate dall'app Android SmartBlocker restano
-- intatte (legge/aggiorna le notifiche smart_block con la anon key).
DROP POLICY IF EXISTS "utente autenticato" ON cm_notification_queue;
DROP POLICY IF EXISTS "cm_notification_queue_garsal_only" ON cm_notification_queue;
CREATE POLICY "cm_notification_queue_garsal_only" ON cm_notification_queue
  FOR ALL USING (public.is_garsal()) WITH CHECK (public.is_garsal());

-- cm_notification_offsets: era completamente SENZA RLS
ALTER TABLE IF EXISTS cm_notification_offsets ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "cm_notification_offsets_garsal_only" ON cm_notification_offsets;
CREATE POLICY "cm_notification_offsets_garsal_only" ON cm_notification_offsets
  FOR ALL USING (public.is_garsal()) WITH CHECK (public.is_garsal());
