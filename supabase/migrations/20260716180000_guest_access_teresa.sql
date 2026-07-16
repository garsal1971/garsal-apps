-- =====================================================================
-- Accesso in sola lettura per Teresa (teresa.leccisotti@gmail.com)
-- Pagina: situazione-teresa.html
--
-- Stesso meccanismo già usato per situazione-rosa.html (migration
-- 20260611230000_guest_access_table.sql): grant via cm_guest_access +
-- policy has_page_access(), nessuna scrittura consentita.
--
-- Dati mostrati da situazione-teresa.html:
--   - cntrs_transactions_terr / cntrs_categories_terr / cntrs_saldi_terr
--     (Conto Risparmio Teresa — Casa Terrasini)
--   - acct_transactions (Conto Spese Teresa)
-- =====================================================================

INSERT INTO cm_guest_access (email, page, note)
VALUES ('teresa.leccisotti@gmail.com', 'situazione-teresa.html', 'Teresa — accesso in sola lettura')
ON CONFLICT (email, page) DO NOTHING;

-- ── Lettura ──────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "guest_read_cntrs_transactions_terr" ON cntrs_transactions_terr;
CREATE POLICY "guest_read_cntrs_transactions_terr" ON cntrs_transactions_terr
  FOR SELECT USING (public.has_page_access('situazione-teresa.html'));

DROP POLICY IF EXISTS "guest_read_cntrs_categories_terr" ON cntrs_categories_terr;
CREATE POLICY "guest_read_cntrs_categories_terr" ON cntrs_categories_terr
  FOR SELECT USING (public.has_page_access('situazione-teresa.html'));

DROP POLICY IF EXISTS "guest_read_cntrs_saldi_terr" ON cntrs_saldi_terr;
CREATE POLICY "guest_read_cntrs_saldi_terr" ON cntrs_saldi_terr
  FOR SELECT USING (public.has_page_access('situazione-teresa.html'));

DROP POLICY IF EXISTS "guest_read_acct_transactions" ON acct_transactions;
CREATE POLICY "guest_read_acct_transactions" ON acct_transactions
  FOR SELECT USING (public.has_page_access('situazione-teresa.html'));

-- ── Blocco scritture per gli ospiti di questa pagina ────────────────
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['cntrs_transactions_terr','cntrs_categories_terr','cntrs_saldi_terr','acct_transactions'] LOOP
    EXECUTE format('DROP POLICY IF EXISTS "guest_teresa_no_insert" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_teresa_no_update" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_teresa_no_delete" ON %I', t);
    EXECUTE format('CREATE POLICY "guest_teresa_no_insert" ON %I AS RESTRICTIVE FOR INSERT WITH CHECK (public.is_garsal() OR NOT public.has_page_access(''situazione-teresa.html''))', t);
    EXECUTE format('CREATE POLICY "guest_teresa_no_update" ON %I AS RESTRICTIVE FOR UPDATE USING (public.is_garsal() OR NOT public.has_page_access(''situazione-teresa.html''))', t);
    EXECUTE format('CREATE POLICY "guest_teresa_no_delete" ON %I AS RESTRICTIVE FOR DELETE USING (public.is_garsal() OR NOT public.has_page_access(''situazione-teresa.html''))', t);
  END LOOP;
END $$;
