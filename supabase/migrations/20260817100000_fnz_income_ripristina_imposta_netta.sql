-- Reddito — l'imposta netta torna in tabella, con i suoi dati storici.
--
-- liq_imposta_netta era una delle cinque colonne tolte dalla liquidazione della dichiarazione, e
-- 20260816110000_fnz_income_cancella_kind_ritirati.sql ne aveva cancellato le righe. Ora la voce
-- è di nuovo in INCOME_SECTIONS (finanza.html): senza questa migration la colonna si vedrebbe
-- vuota per tutti gli anni, e i sette importi andrebbero ribattuti a mano dai 730.
--
-- Gli importi sono ricopiati da 20260815160000_fnz_income_dati_storici.sql, che è la via di
-- recupero prevista proprio per questo caso. Le tre migration girano in ordine anche su un
-- database nuovo — insert, cancellazione, ripristino — e il risultato coincide con la produzione.
--
-- Solo liq_imposta_netta: le altre quattro voci ritirate (liq_detrazioni, liq_acconti,
-- liq_esito, liq_reddito_rif) restano fuori dalla pagina e restano cancellate.

DO $$
DECLARE
  v_user      uuid;
  v_ripristinate integer;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user IS NULL THEN
    -- Progetto dev o database appena creato: non c'è a chi intestare le righe.
    RAISE NOTICE 'fnz_income: utente garsal1971@gmail.com non trovato, imposta netta non ripristinata.';
    RETURN;
  END IF;

  INSERT INTO fnz_income (user_id, year, kind, amount)
  SELECT v_user, y, 'liq_imposta_netta', a::numeric
  FROM (VALUES
    (2025, 15638),
    (2024, 12188),
    (2022, 11498),
    (2021, 10850),
    (2020, 10039),
    (2019,  6070),
    (2017,  8169)
    -- 2018 e 2023 non hanno documenti: nessuna riga, che è diverso da zero.
  ) AS t(y, a)
  -- Idempotente come l'insert dei dati storici: se la voce è già stata ricompilata a mano con un
  -- valore diverso, questa migration la riallinea al 730 invece di duplicarla.
  ON CONFLICT (user_id, year, kind) DO UPDATE
    SET amount = EXCLUDED.amount, updated_at = now();

  GET DIAGNOSTICS v_ripristinate = ROW_COUNT;
  RAISE NOTICE 'fnz_income: % righe di imposta netta ripristinate.', v_ripristinate;
END $$;
