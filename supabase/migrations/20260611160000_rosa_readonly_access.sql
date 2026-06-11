-- =====================================================================
-- Accesso in sola lettura per Rosa (r.bertuglia@yahoo.it)
-- Pagina: situazione-rosa.html
--
-- Obiettivi:
--   1. Rosa può leggere SOLO i dati mostrati da situazione-rosa.html:
--      - fnz_loans: solo il mutuo "via galli" e i prestiti "Da Rosa"
--      - fnz_other_assets: solo asset_type = 'danaro_rosa'
--      - cntrs_transactions / cntrs_categories / cntrs_saldi (Casa Rosa)
--   2. Nessuna scrittura: tutte le policy sono FOR SELECT.
--   3. Le tabelle con policy generiche "authenticated" (price cache,
--      price history, fn_logs) vengono ristrette al solo proprietario.
--   4. La RPC run_score_query (esegue SQL arbitrario) viene blindata:
--      eseguibile solo dall'utente proprietario garsal1971@gmail.com.
-- =====================================================================

-- ── Helper: email dell'utente corrente dal JWT ──────────────────────
CREATE OR REPLACE FUNCTION public.jwt_email()
RETURNS text
LANGUAGE sql STABLE
AS $$
  SELECT coalesce(auth.jwt() ->> 'email', '')
$$;

CREATE OR REPLACE FUNCTION public.is_rosa()
RETURNS boolean
LANGUAGE sql STABLE
AS $$
  SELECT lower(public.jwt_email()) = 'r.bertuglia@yahoo.it'
$$;

CREATE OR REPLACE FUNCTION public.is_garsal()
RETURNS boolean
LANGUAGE sql STABLE
AS $$
  SELECT lower(public.jwt_email()) = 'garsal1971@gmail.com'
$$;

-- ── 1. Policy in sola lettura per Rosa ──────────────────────────────
-- Le policy esistenti (user_id = auth.uid()) restano valide per il
-- proprietario; queste si aggiungono in OR e valgono solo per Rosa.

-- Mutuo "via galli" + prestiti "Da Rosa" (gli stessi filtri usati
-- client-side da situazione-rosa.html, applicati qui server-side)
DROP POLICY IF EXISTS "rosa_read_loans" ON fnz_loans;
CREATE POLICY "rosa_read_loans" ON fnz_loans
  FOR SELECT
  USING (public.is_rosa() AND (name ILIKE '%via galli%' OR name ILIKE '%da rosa%'));

-- Solo gli asset di tipo 'danaro_rosa'
DROP POLICY IF EXISTS "rosa_read_other_assets" ON fnz_other_assets;
CREATE POLICY "rosa_read_other_assets" ON fnz_other_assets
  FOR SELECT
  USING (public.is_rosa() AND asset_type = 'danaro_rosa');

-- Gestione Cassa Casa Rosa (movimenti, categorie, saldi)
DROP POLICY IF EXISTS "rosa_read_cntrs_transactions" ON cntrs_transactions;
CREATE POLICY "rosa_read_cntrs_transactions" ON cntrs_transactions
  FOR SELECT
  USING (public.is_rosa());

DROP POLICY IF EXISTS "rosa_read_cntrs_categories" ON cntrs_categories;
CREATE POLICY "rosa_read_cntrs_categories" ON cntrs_categories
  FOR SELECT
  USING (public.is_rosa());

DROP POLICY IF EXISTS "rosa_read_cntrs_saldi" ON cntrs_saldi;
CREATE POLICY "rosa_read_cntrs_saldi" ON cntrs_saldi
  FOR SELECT
  USING (public.is_rosa());

-- ── 1b. Blocco esplicito delle scritture per Rosa ───────────────────
-- Le policy esistenti "FOR ALL USING (user_id = auth.uid())" permettono
-- a qualsiasi utente (quindi anche a Rosa) di inserire righe proprie.
-- Queste policy RESTRICTIVE escludono Rosa da ogni scrittura sulle
-- tabelle a cui ha accesso in lettura.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['fnz_loans','fnz_other_assets','cntrs_transactions','cntrs_categories','cntrs_saldi'] LOOP
    EXECUTE format('DROP POLICY IF EXISTS "rosa_no_insert" ON %I', t);
    EXECUTE format('CREATE POLICY "rosa_no_insert" ON %I AS RESTRICTIVE FOR INSERT WITH CHECK (NOT public.is_rosa())', t);
    EXECUTE format('DROP POLICY IF EXISTS "rosa_no_update" ON %I', t);
    EXECUTE format('CREATE POLICY "rosa_no_update" ON %I AS RESTRICTIVE FOR UPDATE USING (NOT public.is_rosa())', t);
    EXECUTE format('DROP POLICY IF EXISTS "rosa_no_delete" ON %I', t);
    EXECUTE format('CREATE POLICY "rosa_no_delete" ON %I AS RESTRICTIVE FOR DELETE USING (NOT public.is_rosa())', t);
  END LOOP;
END $$;

-- ── 2. Restringe le policy generiche "authenticated" ────────────────
-- Prima erano leggibili da QUALSIASI utente autenticato (quindi anche
-- da Rosa). Non sono dati puntati da situazione-rosa.html → solo garsal.
DROP POLICY IF EXISTS "fnz_price_cache_read" ON fnz_price_cache;
CREATE POLICY "fnz_price_cache_read" ON fnz_price_cache
  FOR SELECT
  USING (public.is_garsal());

DROP POLICY IF EXISTS "fnz_price_history_read" ON fnz_price_history;
CREATE POLICY "fnz_price_history_read" ON fnz_price_history
  FOR SELECT
  USING (public.is_garsal());

DROP POLICY IF EXISTS "fn_logs_select" ON fn_logs;
CREATE POLICY "fn_logs_select" ON fn_logs
  FOR SELECT
  USING (public.is_garsal());

-- ── 3. Blindatura run_score_query ───────────────────────────────────
-- La RPC esegue SQL arbitrario passato come parametro: senza guardia,
-- qualsiasi utente autenticato (inclusa Rosa) potrebbe leggere tutto
-- il database. La funzione originale viene rinominata in
-- run_score_query_unrestricted (EXECUTE revocato ai client) e sostituita
-- da un wrapper che la esegue solo per garsal1971@gmail.com.
DO $$
DECLARE
  v_oid    oid;
  v_ret    text;
  v_retset boolean;
  v_body   text;
BEGIN
  -- Wrapper già installato? Non fare nulla (idempotenza).
  IF EXISTS (
    SELECT 1 FROM pg_proc
    WHERE proname = 'run_score_query_unrestricted'
      AND pronamespace = 'public'::regnamespace
  ) THEN
    RAISE NOTICE 'run_score_query: wrapper già presente, nessuna modifica';
    RETURN;
  END IF;

  SELECT p.oid, pg_get_function_result(p.oid), p.proretset
    INTO v_oid, v_ret, v_retset
  FROM pg_proc p
  WHERE p.proname = 'run_score_query'
    AND p.pronamespace = 'public'::regnamespace
    AND p.pronargs = 1
    AND p.proargtypes[0] = 'text'::regtype
  LIMIT 1;

  IF v_oid IS NULL THEN
    RAISE NOTICE 'run_score_query(query text) non trovata: nessuna modifica';
    RETURN;
  END IF;

  EXECUTE 'ALTER FUNCTION public.run_score_query(text) RENAME TO run_score_query_unrestricted';
  EXECUTE 'REVOKE ALL ON FUNCTION public.run_score_query_unrestricted(text) FROM PUBLIC';
  EXECUTE 'REVOKE ALL ON FUNCTION public.run_score_query_unrestricted(text) FROM anon, authenticated';

  IF v_retset THEN
    v_body := 'RETURN QUERY SELECT * FROM public.run_score_query_unrestricted(query);';
  ELSE
    v_body := 'RETURN public.run_score_query_unrestricted(query);';
  END IF;

  EXECUTE format(
    'CREATE FUNCTION public.run_score_query(query text) RETURNS %s
     LANGUAGE plpgsql SECURITY DEFINER SET search_path = public
     AS $wrapper$
     BEGIN
       IF lower(coalesce(auth.jwt() ->> ''email'', '''')) <> ''garsal1971@gmail.com'' THEN
         RAISE EXCEPTION ''run_score_query: accesso non autorizzato'';
       END IF;
       %s
     END
     $wrapper$',
    v_ret, v_body);

  EXECUTE 'REVOKE ALL ON FUNCTION public.run_score_query(text) FROM PUBLIC, anon';
  EXECUTE 'GRANT EXECUTE ON FUNCTION public.run_score_query(text) TO authenticated';

  RAISE NOTICE 'run_score_query: wrapper di sicurezza installato';
END $$;
