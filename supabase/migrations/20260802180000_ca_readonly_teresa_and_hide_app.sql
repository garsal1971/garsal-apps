-- =====================================================================
-- Analisi Costi: fuori dalla home, dentro Finanza e Situazione Teresa
--
--   1. L'app sparisce dal launcher AppSphere (cm_apps.active = false).
--      Il file cost-analysis.html resta raggiungibile: è ancora l'unica
--      app dove si importa, si categorizza e si configura — la si apre
--      dal collegamento in finanza.html o dalla notifica Smart Block.
--   2. Teresa legge i dati di Analisi Costi dalla vista in sola lettura
--      dentro situazione-teresa.html: stesso meccanismo già usato per
--      il resto della sua pagina (cm_guest_access + has_page_access()).
--
-- ⚠️ Le tabelle ca_* NON sono monoutente (Ada ha i propri dati): ogni
--    policy di lettura per Teresa è ristretta alle righe di Salvatore
--    via garsal_user_id(), altrimenti vedrebbe anche quelle di Ada.
-- =====================================================================

-- ── L'app non compare più tra le bolle della home ───────────────────
UPDATE cm_apps SET active = false WHERE title = 'Analisi Costi';

-- ── Helper: user_id del proprietario ────────────────────────────────
-- SECURITY DEFINER perché auth.users non è leggibile dal ruolo
-- authenticated: senza questo la policy vedrebbe sempre NULL.
CREATE OR REPLACE FUNCTION public.garsal_user_id()
RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public, auth
AS $$
  SELECT id FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com' LIMIT 1
$$;

-- ── Lettura per gli ospiti di situazione-teresa.html ────────────────
DROP POLICY IF EXISTS "guest_teresa_read_ca_categories" ON ca_categories;
CREATE POLICY "guest_teresa_read_ca_categories" ON ca_categories
  FOR SELECT USING (
    public.has_page_access('situazione-teresa.html') AND user_id = public.garsal_user_id()
  );

DROP POLICY IF EXISTS "guest_teresa_read_ca_people" ON ca_people;
CREATE POLICY "guest_teresa_read_ca_people" ON ca_people
  FOR SELECT USING (
    public.has_page_access('situazione-teresa.html') AND user_id = public.garsal_user_id()
  );

DROP POLICY IF EXISTS "guest_teresa_read_ca_transactions" ON ca_transactions;
CREATE POLICY "guest_teresa_read_ca_transactions" ON ca_transactions
  FOR SELECT USING (
    public.has_page_access('situazione-teresa.html') AND user_id = public.garsal_user_id()
  );

-- ca_transaction_categories non ha user_id: si risale alla transazione.
DROP POLICY IF EXISTS "guest_teresa_read_ca_transaction_categories" ON ca_transaction_categories;
CREATE POLICY "guest_teresa_read_ca_transaction_categories" ON ca_transaction_categories
  FOR SELECT USING (
    public.has_page_access('situazione-teresa.html')
    AND EXISTS (
      SELECT 1 FROM ca_transactions t
      WHERE t.id = transaction_id AND t.user_id = public.garsal_user_id()
    )
  );

-- ── Nessuna scrittura per gli ospiti di quella pagina ───────────────
-- Policy RESTRICTIVE: valgono in AND con le permissive. Salvatore passa
-- sempre (is_garsal), chi non è ospite di questa pagina non è toccato,
-- Teresa resta in sola lettura. Le Edge Function usano la service role
-- e non passano per la RLS.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['ca_categories','ca_people','ca_transactions','ca_transaction_categories'] LOOP
    EXECUTE format('DROP POLICY IF EXISTS "guest_teresa_no_insert" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_teresa_no_update" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_teresa_no_delete" ON %I', t);
    EXECUTE format('CREATE POLICY "guest_teresa_no_insert" ON %I AS RESTRICTIVE FOR INSERT WITH CHECK (public.is_garsal() OR NOT public.has_page_access(''situazione-teresa.html''))', t);
    EXECUTE format('CREATE POLICY "guest_teresa_no_update" ON %I AS RESTRICTIVE FOR UPDATE USING (public.is_garsal() OR NOT public.has_page_access(''situazione-teresa.html''))', t);
    EXECUTE format('CREATE POLICY "guest_teresa_no_delete" ON %I AS RESTRICTIVE FOR DELETE USING (public.is_garsal() OR NOT public.has_page_access(''situazione-teresa.html''))', t);
  END LOOP;
END $$;
