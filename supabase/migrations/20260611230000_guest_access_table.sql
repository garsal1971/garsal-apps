-- =====================================================================
-- Accessi ospite configurabili da tabella (cm_guest_access)
--
-- Sostituisce l'email cablata nelle policy (is_rosa) con una tabella di
-- configurazione: email → pagina autorizzata. Per dare accesso a una
-- nuova persona a situazione-rosa.html basta inserire una riga e
-- invitare l'utente dal dashboard; nessuna migration necessaria.
--
--   INSERT INTO cm_guest_access (email, page)
--   VALUES ('nuova@mail.it', 'situazione-rosa.html');
--
-- Il proprietario (is_garsal) resta fuori dal meccanismo e mantiene
-- accesso totale. Richiede is_garsal()/jwt_email() della migration
-- 20260611160000_rosa_readonly_access.
-- =====================================================================

-- ── Tabella di configurazione ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS cm_guest_access (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  email      text        NOT NULL,
  page       text        NOT NULL,
  note       text        NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(email, page)
);

ALTER TABLE cm_guest_access ENABLE ROW LEVEL SECURITY;

-- Il proprietario gestisce tutto
DROP POLICY IF EXISTS "cm_guest_access_garsal_all" ON cm_guest_access;
CREATE POLICY "cm_guest_access_garsal_all" ON cm_guest_access
  FOR ALL USING (public.is_garsal()) WITH CHECK (public.is_garsal());

-- Ogni ospite legge solo le proprie righe (serve alla pagina HTML per
-- verificare il permesso dopo il login)
DROP POLICY IF EXISTS "cm_guest_access_own_select" ON cm_guest_access;
CREATE POLICY "cm_guest_access_own_select" ON cm_guest_access
  FOR SELECT USING (lower(email) = lower(public.jwt_email()));

-- ── Helper per le policy RLS delle tabelle dati ─────────────────────
-- SECURITY DEFINER: legge cm_guest_access senza dipendere dalle sue
-- policy quando viene valutata dentro le policy di altre tabelle.
CREATE OR REPLACE FUNCTION public.has_page_access(p_page text)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM cm_guest_access
    WHERE lower(email) = lower(public.jwt_email())
      AND page = p_page
  )
$$;

-- ── Prima riga: Rosa (per lei non cambia nulla) ─────────────────────
INSERT INTO cm_guest_access (email, page, note)
VALUES ('r.bertuglia@yahoo.it', 'situazione-rosa.html', 'Rosa — accesso in sola lettura')
ON CONFLICT (email, page) DO NOTHING;

-- ── Migrazione delle policy da is_rosa() a has_page_access() ────────
-- Lettura: stessi filtri di prima, ma validi per chiunque sia in tabella
DROP POLICY IF EXISTS "rosa_read_loans" ON fnz_loans;
DROP POLICY IF EXISTS "guest_read_loans" ON fnz_loans;
CREATE POLICY "guest_read_loans" ON fnz_loans
  FOR SELECT
  USING (public.has_page_access('situazione-rosa.html')
         AND (name ILIKE '%via galli%' OR name ILIKE '%da rosa%'));

DROP POLICY IF EXISTS "rosa_read_other_assets" ON fnz_other_assets;
DROP POLICY IF EXISTS "guest_read_other_assets" ON fnz_other_assets;
CREATE POLICY "guest_read_other_assets" ON fnz_other_assets
  FOR SELECT
  USING (public.has_page_access('situazione-rosa.html')
         AND asset_type = 'danaro_rosa');

DROP POLICY IF EXISTS "rosa_read_cntrs_transactions" ON cntrs_transactions;
DROP POLICY IF EXISTS "guest_read_cntrs_transactions" ON cntrs_transactions;
CREATE POLICY "guest_read_cntrs_transactions" ON cntrs_transactions
  FOR SELECT USING (public.has_page_access('situazione-rosa.html'));

DROP POLICY IF EXISTS "rosa_read_cntrs_categories" ON cntrs_categories;
DROP POLICY IF EXISTS "guest_read_cntrs_categories" ON cntrs_categories;
CREATE POLICY "guest_read_cntrs_categories" ON cntrs_categories
  FOR SELECT USING (public.has_page_access('situazione-rosa.html'));

DROP POLICY IF EXISTS "rosa_read_cntrs_saldi" ON cntrs_saldi;
DROP POLICY IF EXISTS "guest_read_cntrs_saldi" ON cntrs_saldi;
CREATE POLICY "guest_read_cntrs_saldi" ON cntrs_saldi
  FOR SELECT USING (public.has_page_access('situazione-rosa.html'));

-- Blocco scritture: gli ospiti restano in sola lettura.
-- (is_garsal() OR ... per non bloccare mai il proprietario)
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['fnz_loans','fnz_other_assets','cntrs_transactions','cntrs_categories','cntrs_saldi'] LOOP
    EXECUTE format('DROP POLICY IF EXISTS "rosa_no_insert" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "rosa_no_update" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "rosa_no_delete" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_no_insert" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_no_update" ON %I', t);
    EXECUTE format('DROP POLICY IF EXISTS "guest_no_delete" ON %I', t);
    EXECUTE format('CREATE POLICY "guest_no_insert" ON %I AS RESTRICTIVE FOR INSERT WITH CHECK (public.is_garsal() OR NOT public.has_page_access(''situazione-rosa.html''))', t);
    EXECUTE format('CREATE POLICY "guest_no_update" ON %I AS RESTRICTIVE FOR UPDATE USING (public.is_garsal() OR NOT public.has_page_access(''situazione-rosa.html''))', t);
    EXECUTE format('CREATE POLICY "guest_no_delete" ON %I AS RESTRICTIVE FOR DELETE USING (public.is_garsal() OR NOT public.has_page_access(''situazione-rosa.html''))', t);
  END LOOP;
END $$;

-- is_rosa() non è più usata da nessuna policy
DROP FUNCTION IF EXISTS public.is_rosa();
