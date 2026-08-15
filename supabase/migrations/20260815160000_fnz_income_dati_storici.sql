-- Reddito — i dati storici ricavati dai 730 e dalle CU (2017-2025).
--
-- Cinque gruppi di voci, tutti nella stessa tabella perché ogni cella è un (anno, kind, importo):
-- redditi, ritenute in busta paga, liquidazione della dichiarazione, contributi previdenziali,
-- premi/welfare/cedolare secca. I kind vivono in INCOME_SECTIONS dentro finanza.html.
--
-- Quello che NON viene archiviato, perché si ricalcola esattamente dagli altri (verificato su
-- tutti gli anni disponibili, scarto 0 tranne un arrotondamento da 1 € sul 2025 e sul 2017):
--   • netto in busta        = lordo − totale ritenute
--   • totale lav. dipendente = lordo + pensione
--   • totale trattenuto      = IRPEF + add. regionale + acconto + saldo
-- Archiviarli vorrebbe dire tenere due verità sullo stesso numero, e prima o poi divergono.
-- Il reddito complessivo invece SI archivia: è la cifra scritta sul 730, e per il 2025 non è
-- ricavabile (mancano fabbricati e abitazione principale) — calcolarlo lo farebbe sembrare
-- completo quando non lo è.
--
-- Gli anni e le celle assenti dai documenti (2013-2016, 2018, 2023, i «—» e gli «n.d.») non
-- hanno nessuna riga: assenza di riga = dato non disponibile. È diverso da zero, e per questo
-- non si inserisce uno zero al posto loro.

-- ── Nomi definitivi delle prime quattro voci ────────────────────────
-- Erano provvisori (affitto / dichiarazione) e i documenti li chiamano altrimenti. Le UPDATE
-- non fanno niente se nessuno ha ancora compilato la pagina, ma se qualcosa c'è va rinominato
-- adesso: dopo l'insert qui sotto, una riga «affitto» resterebbe invisibile per sempre.
UPDATE fnz_income SET kind = 'fabbricati'          WHERE kind = 'affitto';
UPDATE fnz_income SET kind = 'reddito_complessivo' WHERE kind = 'dichiarazione';

-- L'esito della liquidazione è con segno: rimborso positivo, debito negativo. La colonna non ha
-- nessun CHECK, ma il commento diceva il contrario.
COMMENT ON COLUMN fnz_income.amount IS
  'Importo in euro della voce. Quasi sempre positivo; liq_esito è con segno (rimborso > 0, debito < 0).';

DO $$
DECLARE
  v_user uuid;
  v_inseriti integer;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user IS NULL THEN
    -- Progetto dev o database appena creato: non è un errore, semplicemente non c'è a chi
    -- intestare le righe. Meglio saltare che far fallire il deploy dell'intera migration.
    RAISE NOTICE 'fnz_income: utente garsal1971@gmail.com non trovato, dati storici non inseriti.';
    RETURN;
  END IF;

  INSERT INTO fnz_income (user_id, year, kind, amount)
  SELECT v_user, y, k, a::numeric
  FROM (VALUES
    -- ── 1. Redditi ────────────────────────────────────────────────
    (2025, 'lavoro',                53490),
    (2025, 'pensione',               6013),
    (2024, 'lavoro',                52043),
    (2024, 'pensione',               5994),
    (2024, 'fabbricati',             1368),
    (2024, 'abitazione_principale',  1073),
    (2024, 'reddito_complessivo',   60478),
    (2022, 'lavoro',                49503),
    (2022, 'pensione',               5154),
    (2022, 'fabbricati',              345),
    (2022, 'abitazione_principale',  1073),
    (2022, 'reddito_complessivo',   56075),
    (2021, 'lavoro',                47227),
    (2021, 'pensione',               5027),
    (2021, 'fabbricati',              139),
    (2021, 'abitazione_principale',  1073),
    (2021, 'reddito_complessivo',   53466),
    (2020, 'lavoro',                37118),
    (2020, 'pensione',               5022),
    (2020, 'abitazione_principale',  1073),
    (2020, 'reddito_complessivo',   43213),
    (2019, 'lavoro',                36106),
    (2019, 'pensione',               5002),
    (2019, 'abitazione_principale',  1073),
    (2019, 'reddito_complessivo',   42181),
    (2017, 'lavoro',                34983),
    (2017, 'abitazione_principale',  1072),

    -- ── 2. Ritenute in busta paga ─────────────────────────────────
    (2025, 'rit_irpef',             15638),
    (2025, 'rit_add_regionale',      1211),
    (2025, 'rit_add_com_acconto',     128),
    (2025, 'rit_add_com_saldo',       303),
    (2024, 'rit_irpef',             15016),
    (2024, 'rit_add_regionale',       943),
    (2024, 'rit_add_com_acconto',     124),
    (2024, 'rit_add_com_saldo',       292),
    (2022, 'rit_irpef',             14096),
    (2022, 'rit_add_regionale',       887),
    (2022, 'rit_add_com_acconto',     113),
    (2022, 'rit_add_com_saldo',       283),
    (2021, 'rit_irpef',             13420),
    (2021, 'rit_add_regionale',       841),
    (2021, 'rit_add_com_acconto',      89),
    (2021, 'rit_add_com_saldo',       289),
    (2020, 'rit_irpef',              8810),
    (2020, 'rit_add_regionale',       635),
    (2020, 'rit_add_com_acconto',      87),
    (2020, 'rit_add_com_saldo',       210),
    (2019, 'rit_irpef',              8666),
    (2019, 'rit_add_regionale',       615),
    (2019, 'rit_add_com_acconto',      85),
    (2019, 'rit_add_com_saldo',       204),
    (2017, 'rit_irpef',              8169),
    (2017, 'rit_add_regionale',       592),
    (2017, 'rit_add_com_acconto',      86),
    (2017, 'rit_add_com_saldo',       194),

    -- ── 3. Liquidazione della dichiarazione ───────────────────────
    (2025, 'liq_imponibile',        53490),
    (2025, 'liq_imposta_lorda',     15641),
    (2025, 'liq_detrazioni',            3),
    (2025, 'liq_imposta_netta',     15638),
    (2024, 'liq_imponibile',        59405),
    (2024, 'liq_imposta_lorda',     18184),
    (2024, 'liq_detrazioni',         5996),
    (2024, 'liq_imposta_netta',     12188),
    (2024, 'liq_esito',              2385),   -- rimborso
    (2024, 'liq_reddito_rif',       65878),
    (2022, 'liq_imponibile',        55002),
    (2022, 'liq_imposta_lorda',     16551),
    (2022, 'liq_detrazioni',         5053),
    (2022, 'liq_imposta_netta',     11498),
    (2022, 'liq_esito',              1854),   -- rimborso
    (2022, 'liq_reddito_rif',       61475),
    (2021, 'liq_imponibile',        52393),
    (2021, 'liq_imposta_lorda',     16229),
    (2021, 'liq_detrazioni',         5379),
    (2021, 'liq_imposta_netta',     10850),
    (2021, 'liq_acconti',            1229),
    (2021, 'liq_esito',              3419),   -- rimborso
    (2021, 'liq_reddito_rif',       55266),
    (2020, 'liq_imponibile',        42140),
    (2020, 'liq_imposta_lorda',     12333),
    (2020, 'liq_detrazioni',         2294),
    (2020, 'liq_imposta_netta',     10039),
    (2020, 'liq_esito',             -1229),   -- debito
    (2019, 'liq_imponibile',        41108),
    (2019, 'liq_imposta_lorda',     11941),
    (2019, 'liq_detrazioni',         5871),
    (2019, 'liq_imposta_netta',      6070),
    (2019, 'liq_acconti',            1009),
    (2019, 'liq_esito',              3464),   -- rimborso
    (2017, 'liq_imposta_lorda',      9614),
    (2017, 'liq_detrazioni',         1445),
    (2017, 'liq_imposta_netta',      8169),

    -- ── 4. Contributi previdenziali ───────────────────────────────
    -- Solo 2025 e 2017: negli altri anni il dato non compare nel 730, sta nella CU.
    (2025, 'prev_imponibile',       60408),
    (2025, 'prev_contributi',        5642),
    (2017, 'prev_imponibile',       41040),
    (2017, 'prev_contributi',        3799),

    -- ── 5. Premi, welfare, cedolare secca ─────────────────────────
    (2024, 'ced_imponibile',         5400),
    (2024, 'ced_dovuta',              540),
    (2022, 'prem_risultato',         1481),
    (2022, 'prem_imposta_sost',       148),
    (2022, 'prem_welfare',           1519),
    (2022, 'ced_imponibile',         5400),
    (2022, 'ced_dovuta',              540),
    (2021, 'prem_welfare',            889),
    (2021, 'ced_imponibile',         1800),
    (2021, 'ced_dovuta',              180),
    (2020, 'prem_risultato',         1767),
    (2020, 'prem_imposta_sost',       177),
    (2020, 'prem_welfare',           1233),
    (2019, 'prem_risultato',         1435),
    (2019, 'prem_imposta_sost',       143),
    (2019, 'prem_welfare',           1129)
  ) AS t(y, k, a)
  -- Idempotente: rilanciando la migration i valori vengono riallineati, non duplicati.
  ON CONFLICT (user_id, year, kind) DO UPDATE
    SET amount = EXCLUDED.amount, updated_at = now();

  GET DIAGNOSTICS v_inseriti = ROW_COUNT;
  RAISE NOTICE 'fnz_income: % righe di dati storici scritte.', v_inseriti;
END $$;
