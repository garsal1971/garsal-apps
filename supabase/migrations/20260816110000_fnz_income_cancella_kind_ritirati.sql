-- Reddito — cancellazione dei kind che la pagina non mostra più.
--
-- Nelle revisioni di agosto 2026 la pagina 💶 Reddito è stata ridotta a due tabelle: redditi
-- (lavoro, pensione, altri_lordi, altri_netti) e liquidazione (liq_imponibile,
-- liq_imposta_lorda, liq_reddito_netto). Le voci uscite dalle tabelle erano rimaste archiviate
-- senza una colonna che le mostrasse; qui vengono cancellate su richiesta esplicita.
--
-- 86 righe su 112, in 19 kind. Restano i 4 kind con dati fra quelli mostrati (lavoro, pensione,
-- liq_imponibile, liq_imposta_lorda) per 26 righe; altri_lordi, altri_netti e liq_reddito_netto
-- sono da compilare e non hanno ancora righe.
--
-- ⚠️ Questa migration disfa in parte 20260815160000_fnz_income_dati_storici.sql, che resta al suo
-- posto: le migration sono una cronologia, non lo stato desiderato. Su un database nuovo girano
-- in ordine — prima l'insert dei dati storici, poi questa cancellazione — e il risultato è lo
-- stesso di quello in produzione. È anche la via di recupero: gli importi cancellati sono tutti
-- scritti in chiaro in quel file, quindi se un giorno servissero si rileggono da lì.
--
-- L'elenco dei kind è scritto per esteso e non ricavato per esclusione («tutto ciò che non è
-- mostrato»): un elenco esplicito si rilegge e si verifica, mentre una regola per esclusione
-- dipende da com'era fatta la pagina il giorno in cui la migration è stata scritta.

DO $$
DECLARE
  v_user      uuid;
  v_cancellate integer;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user IS NULL THEN
    RAISE NOTICE 'fnz_income: utente garsal1971@gmail.com non trovato, nessuna cancellazione.';
    RETURN;
  END IF;

  -- Solo le righe di quell'utente: se un giorno queste voci servissero a qualcun altro, non
  -- sono affari di questa migration.
  DELETE FROM fnz_income
  WHERE user_id = v_user
    AND kind IN (
      -- redditi usciti dalla prima tabella
      'fabbricati', 'abitazione_principale', 'reddito_complessivo',
      -- ritenute in busta paga (sezione ritirata per intero)
      'rit_irpef', 'rit_add_regionale', 'rit_add_com_acconto', 'rit_add_com_saldo',
      -- liquidazione: le cinque colonne tolte
      'liq_detrazioni', 'liq_imposta_netta', 'liq_acconti', 'liq_esito', 'liq_reddito_rif',
      -- contributi previdenziali (sezione ritirata per intero)
      'prev_imponibile', 'prev_contributi',
      -- premi, welfare, cedolare secca (sezione ritirata per intero)
      'prem_risultato', 'prem_imposta_sost', 'prem_welfare', 'ced_imponibile', 'ced_dovuta'
    );

  GET DIAGNOSTICS v_cancellate = ROW_COUNT;
  RAISE NOTICE 'fnz_income: % righe cancellate.', v_cancellate;
END $$;

-- Il commento della colonna citava liq_esito, che da qui in poi non ha più righe.
COMMENT ON COLUMN fnz_income.amount IS
  'Importo in euro della voce. I kind mostrati dalla pagina sono tutti positivi; la colonna non ha un CHECK, quindi un importo con segno resta possibile.';
