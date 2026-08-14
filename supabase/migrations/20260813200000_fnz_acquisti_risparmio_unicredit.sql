-- Quattro acquisti sul portafoglio RISPARMIO, dossier UNICREDIT personale, con i
-- quattro prodotti nuovi che li accompagnano: Nvidia, TSMC (ADR), Micron, Meta.
--
-- ⚠️ I PREZZI SONO IN EURO, NON IN DOLLARI.
-- `computeHoldings()` in finanza.html confronta `fnz_transactions.price` con il
-- prezzo di `fnz_price_cache`, che `get-prices` normalizza sempre in euro
-- (TARGET_CURRENCY). Scrivere qui il prezzo in dollari significherebbe misurare il
-- costo in una valuta e il valore in un'altra: le quattro posizioni nascerebbero
-- con circa un 13 % di perdita inventata, e nessuna schermata direbbe perché.
-- Per lo stesso motivo `currency` dei prodotti è 'EUR', come per le altre azioni
-- americane già in archivio (AAPL, AMZN, LLY).
--
-- Il prezzo in euro è il controvalore diviso il numero di azioni. Il cambio che
-- ne esce è 1,1531 $/€ su tutte e quattro le righe — è la controprova che i
-- numeri del prospetto sono coerenti fra loro.
--
--   titolo   ISIN            $/az     az    ctv €     €/az registrato
--   Nvidia   US67066G1040  225,60     15    2.935     195,6667
--   TSMC     US8740391003  418,47      8    2.903     362,8750
--   Micron   US5951121038  955,89      3    2.487     829,0000
--   Meta     US30303M1027  ~589        8    4.086,67  510,8333
--
-- NOTA SU META: 8 azioni, confermate. Il controvalore del prospetto (~3.065 €)
-- corrispondeva a 6 azioni ed era rimasto indietro: il prezzo unitario si ricava
-- comunque da lì (3.065 / 6 = 510,8333 €/az, che è lo stesso cambio delle altre
-- tre righe), e il controvalore effettivo è 4.086,67 €.
--
-- Commissioni: non indicate nel prospetto, registrate a 0. Se la banca le ha
-- addebitate vanno messe in `commissione`, altrimenti il prezzo di carico è più
-- basso del reale e la plusvalenza risulterà più alta di quella vera.

-- ── I dati, tutti qui in cima ───────────────────────────────────────────────
-- Tabelle temporanee senza ON COMMIT DROP di proposito: quella clausola le
-- cancella alla fine della transazione, e se il file viene eseguito senza una
-- transazione esplicita (psql conferma ogni statement per conto suo) sparirebbero
-- subito dopo il CREATE, prima ancora dell'INSERT. Si buttano a mano in fondo.
DROP TABLE IF EXISTS _dati, _quando;

CREATE TEMP TABLE _dati (
  symbol text, name text, isin text, asset_type text, exchange text,
  quantita numeric, prezzo_eur numeric, commissione numeric
);

INSERT INTO _dati VALUES
  ('NVDA', 'NVIDIA',            'US67066G1040', 'stock', 'NASDAQ', 15, 195.6667, 0),
  ('TSM',  'TSMC (ADR)',        'US8740391003', 'stock', 'NYSE',    8, 362.8750, 0),
  ('MU',   'MICRON TECHNOLOGY', 'US5951121038', 'stock', 'NASDAQ',  3, 829.0000, 0),
  ('META', 'META PLATFORMS',    'US30303M1027', 'stock', 'NASDAQ',  8, 510.8333, 0);

-- Data di acquisto.
CREATE TEMP TABLE _quando (data date);
INSERT INTO _quando VALUES (DATE '2026-08-12');

-- ── Il dossier ──────────────────────────────────────────────────────────────
-- Creato solo se non c'è già un dossier UniCredit con quel numero. Il confronto
-- sulla banca è ILIKE e non «=» perché in archivio il nome è scritto a mano
-- ('UNICREDIT'): con un confronto esatto, un 'UniCredit' già presente non
-- verrebbe riconosciuto e qui nascerebbe un secondo dossier per lo stesso conto.
INSERT INTO fnz_dossiers (user_id, bank_name, title, number)
SELECT public.garsal_user_id(), 'UNICREDIT', 'PERSONALE', '0002'
 WHERE NOT EXISTS (
   SELECT 1 FROM fnz_dossiers d
    WHERE d.user_id = public.garsal_user_id()
      AND d.bank_name ILIKE 'unicredit' AND d.number = '0002'
 );

-- ── Controlli PRIMA di scrivere ─────────────────────────────────────────────
-- Vanno qui e non in fondo. Una JOIN che non trova il portafoglio inserisce zero
-- righe in silenzio; una che ne trova due le inserisce **due volte**, una per
-- portafoglio. Verificarlo dopo l'INSERT non basta: se il file gira senza una
-- transazione esplicita — psql conferma ogni statement per conto suo — le righe
-- sbagliate sono già scritte e l'errore arriva a cose fatte. Provato: con due
-- portafogli corrispondenti finivano dentro 8 transazioni invece di 4.
DO $$
DECLARE
  v_portafogli int;
  v_dossier    int;
BEGIN
  IF public.garsal_user_id() IS NULL THEN
    RAISE EXCEPTION 'utente garsal1971@gmail.com non trovato';
  END IF;

  SELECT count(*) INTO v_portafogli FROM fnz_portfolios
   WHERE user_id = public.garsal_user_id() AND name ILIKE '%risparmio%';
  IF v_portafogli <> 1 THEN
    RAISE EXCEPTION 'portafogli con «risparmio» nel nome: % (ne serve esattamente 1)', v_portafogli;
  END IF;

  SELECT count(*) INTO v_dossier FROM fnz_dossiers
   WHERE user_id = public.garsal_user_id()
     AND bank_name ILIKE 'unicredit' AND number = '0002';
  IF v_dossier <> 1 THEN
    RAISE EXCEPTION 'dossier UniCredit n. 0002 trovati: % (ne serve esattamente 1)', v_dossier;
  END IF;
END $$;

-- ── I prodotti ──────────────────────────────────────────────────────────────
-- `asset_type = 'stock'` è corretto e non è il caso di SMEA: NVDA, TSM, MU e META
-- sono i ticker veri di quei titoli, non nomi nostri, quindi la richiesta diretta
-- a Twelve Data col simbolo li identifica senza ambiguità.
INSERT INTO fnz_products (user_id, symbol, name, asset_type, exchange, currency, isin, tags)
SELECT public.garsal_user_id(), d.symbol, d.name, d.asset_type, d.exchange, 'EUR', d.isin,
       '{"RISCHIO": "ALTO", "STRUMENTO": "AZIONE", "TASSAZIONE": "CAP. GAIN 26%"}'::jsonb
  FROM _dati d
ON CONFLICT (user_id, symbol) DO NOTHING;

-- ── Le transazioni ──────────────────────────────────────────────────────────
-- Il NOT EXISTS rende il file rieseguibile senza sdoppiare gli acquisti: senza,
-- un secondo passaggio raddoppierebbe quantità e costo del portafoglio.
-- Portafoglio e dossier entrano come **sottoquery scalari**, non come JOIN. Con
-- una JOIN, due righe corrispondenti moltiplicano le transazioni in silenzio e il
-- danno è fatto; come sottoquery scalare, due righe sono un errore di PostgreSQL
-- («more than one row returned by a subquery used as an expression») e zero righe
-- danno NULL, che su `portfolio_id NOT NULL` è a sua volta un errore. Così il file
-- non può sdoppiare gli acquisti nemmeno se chi lo esegue tira dritto dopo il
-- primo errore, invece di fermarsi: il controllo qui sopra dà il messaggio
-- comprensibile, questa forma dà la garanzia.
INSERT INTO fnz_transactions
       (user_id, portfolio_id, product_id, dossier_id, type, quantity, price, commission, date)
SELECT public.garsal_user_id(),
       (SELECT pf.id FROM fnz_portfolios pf
         WHERE pf.user_id = public.garsal_user_id() AND pf.name ILIKE '%risparmio%'),
       pr.id,
       (SELECT ds.id FROM fnz_dossiers ds
         WHERE ds.user_id = public.garsal_user_id()
           AND ds.bank_name ILIKE 'unicredit' AND ds.number = '0002'),
       'BUY', d.quantita, d.prezzo_eur, d.commissione, q.data
  FROM _dati d
 CROSS JOIN _quando q
  JOIN fnz_products pr ON pr.user_id = public.garsal_user_id() AND pr.symbol = d.symbol
 WHERE NOT EXISTS (
   SELECT 1 FROM fnz_transactions t
    WHERE t.user_id = public.garsal_user_id() AND t.product_id = pr.id
      AND t.date = q.data
      AND t.quantity = d.quantita AND t.price = d.prezzo_eur
 );

-- ── Verifica finale: ci sono tutte e quattro, né meno né di più ─────────────
DO $$
DECLARE
  v_mancanti int;
  v_totali   int;
BEGIN
  SELECT count(*) INTO v_mancanti
    FROM _dati d CROSS JOIN _quando q
   WHERE NOT EXISTS (
     SELECT 1 FROM fnz_transactions t
       JOIN fnz_products pr ON pr.id = t.product_id
      WHERE pr.symbol = d.symbol AND t.date = q.data
        AND t.quantity = d.quantita AND t.price = d.prezzo_eur
        AND t.user_id = public.garsal_user_id()
   );
  IF v_mancanti > 0 THEN
    RAISE EXCEPTION 'transazioni non inserite: % su 4', v_mancanti;
  END IF;

  SELECT count(*) INTO v_totali
    FROM fnz_transactions t
    JOIN fnz_products pr ON pr.id = t.product_id
   CROSS JOIN _quando q
   WHERE t.user_id = public.garsal_user_id() AND t.date = q.data
     AND pr.symbol IN ('NVDA', 'TSM', 'MU', 'META');
  IF v_totali <> 4 THEN
    RAISE EXCEPTION 'in archivio ci sono % transazioni per questi 4 titoli in data odierna, non 4', v_totali;
  END IF;
END $$;

DROP TABLE IF EXISTS _dati, _quando;
