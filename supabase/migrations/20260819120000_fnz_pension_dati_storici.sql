-- Previdenza — dati storici dall'estratto conto previdenziale INPS (emesso il 19/08/2026) e
-- dalla simulazione "La mia Pensione" (previsione per i lavoratori), stesso giorno.
--
-- "al diritto e al calcolo" è la colonna che conta davvero (i "Contributi utili pensione"): il
-- documento riporta anche le settimane solari del periodo, ma per il part-time le due cifre
-- divergono (es. 25 settimane solari → 22 utili) ed è la seconda quella che l'INPS usa. È quella
-- che finisce in weeks_valid, come da commento sulla colonna nella migration di struttura.
--
-- Idempotente come fnz_income_dati_storici.sql: intestato all'utente cercato per email, salta
-- senza fallire se quell'utente non esiste (progetto dev). Non c'è un vincolo di unicità su cui
-- fare ON CONFLICT (i periodi possono ripetersi legittimamente, es. i tre "Mater/pater/congedi"
-- del 2010): la DO $$ cancella prima le righe di questo utente e le reinserisce, così rilanciare
-- la migration non duplica.

DO $$
DECLARE
  v_user uuid;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user IS NULL THEN
    RAISE NOTICE 'fnz_pension: utente garsal1971@gmail.com non trovato, dati storici non inseriti.';
    RETURN;
  END IF;

  -- ── Previsione pensionistica ("La mia Pensione", previsione per i lavoratori) ─────────
  INSERT INTO fnz_pension_forecast
    (user_id, scenario, pension_date, gross_amount, last_salary, replacement_rate, issued_at)
  VALUES
    (v_user, 'vecchiaia',   '2039-07-01', 4255.00, 5639.00, 75.46, '2026-08-19'),
    (v_user, 'anticipata',  '2036-09-01', 3602.00, 5393.00, 66.79, '2026-08-19')
  ON CONFLICT (user_id, scenario) DO UPDATE SET
    pension_date     = EXCLUDED.pension_date,
    gross_amount     = EXCLUDED.gross_amount,
    last_salary      = EXCLUDED.last_salary,
    replacement_rate = EXCLUDED.replacement_rate,
    issued_at        = EXCLUDED.issued_at,
    updated_at       = now();

  -- ── Estratto conto contributivo, regime generale ────────────────────────────────────
  DELETE FROM fnz_pension_contributions WHERE user_id = v_user;

  INSERT INTO fnz_pension_contributions
    (user_id, period_start, period_end, contribution_type, weeks_valid, income_amount, employer, note)
  SELECT v_user, period_start::date, period_end::date, contribution_type, weeks_valid, income_amount, employer, note
  FROM (VALUES
    ('1992-10-01','1992-12-31','Lavoro dipendente',            13.000,  4599.56, 'ENTE BANCO DI SICILIA', ''),
    ('1993-01-01','1993-09-30','Lavoro dipendente',            39.000, 17545.59, 'ENTE BANCO DI SICILIA', ''),
    ('1993-10-01','1993-12-31','Lavoro dipendente',            13.000,  6864.74, 'ENTE BANCO DI SICILIA', ''),
    ('1994-01-01','1994-12-31','Lavoro dipendente',            52.000, 23990.97, 'ENTE BANCO DI SICILIA', ''),
    ('1995-01-01','1995-12-31','Lavoro dipendente',            52.000,  8347.49, 'ENTE BANCO DI SICILIA', ''),
    ('1996-01-01','1996-09-30','Lavoro dipendente',            39.000, 13652.02, 'ENTE BANCO DI SICILIA', ''),
    ('1996-10-01','1996-12-31','Lavoro dipendente',            13.000,  7883.19, 'ENTE BANCO DI SICILIA', ''),
    ('1997-01-01','1997-12-31','Lavoro dipendente',            52.000, 27213.14, 'ENTE BANCO DI SICILIA', ''),
    ('1998-01-01','1998-12-31','Lavoro dipendente',            52.000, 26828.38, 'ENTE BANCO DI SICILIA', ''),
    ('1999-01-01','1999-12-31','Lavoro dipendente',            52.000, 29121.97, 'ENTE BANCO DI SICILIA', ''),
    ('2000-01-01','2000-12-31','Lavoro dipendente',            52.000, 31619.04, 'ENTE BANCO DI SICILIA', ''),
    ('2001-01-01','2001-12-31','Lavoro dipendente',            52.000, 30785.00, 'ENTE BANCO DI SICILIA', ''),
    ('2002-01-01','2002-06-30','Lavoro dipendente',            26.000, 13460.00, 'ENTE BANCO DI SICILIA', ''),
    ('2002-07-01','2002-12-31','Lavoro dipendente',            26.000, 17510.00, 'ENTE BANCO DI SICILIA', ''),
    ('2003-01-01','2003-12-31','Lavoro dipendente',            52.000, 34326.00, 'ENTE BANCO DI SICILIA', ''),
    ('2004-01-01','2004-12-31','Lavoro dipendente',            52.000, 36736.00, 'ENTE BANCO DI SICILIA', ''),
    ('2005-01-01','2005-07-31','Lavoro dipendente',             0.000,  1058.00, 'ENTE BANCO DI SICILIA', ''),
    ('2005-01-01','2005-12-31','Lavoro dipendente',            52.000, 44484.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2006-01-01','2006-12-31','Lavoro dipendente',            52.000, 47239.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2006-01-01','2006-12-31','Donaz.sangue (ad integ.)',      0.000,   336.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2007-01-01','2007-12-31','Lavoro dipendente',            52.000, 41142.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2007-01-01','2007-12-31','Donaz.sangue (ad integ.)',      0.000,   340.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2008-01-01','2008-02-29','Lavoro dipendente',             9.000,  8053.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2008-01-01','2008-02-29','Donaz.sangue (ad integ.)',      0.000,   118.00, 'S.P.A. CAPITALIA INFORMATICA', ''),
    ('2008-03-01','2008-12-31','Lavoro dipendente',            45.000, 40553.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2008-03-01','2008-12-31','Donaz.sangue (ad integ.)',      0.000,   119.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2009-01-01','2009-12-31','Lavoro dipendente',            52.000, 47570.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2009-01-01','2009-12-31','Donaz.sangue (ad integ.)',      0.000,   257.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2010-01-01','2010-12-31','Lavoro dipendente',            46.000, 42170.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2010-01-01','2010-12-31','Congedi assist.Hand.',          7.000,  7018.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2010-01-01','2010-12-31','Mater/pater/congedi(Int)',      0.000,   586.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2010-01-01','2010-12-31','Mater/pater/congedi(Int)',      0.000,   588.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2010-01-01','2010-12-31','Mater/pater/congedi(Int)',      0.000,  1841.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2010-01-01','2010-12-31','Donaz.sangue (ad integ.)',      0.000,   132.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2011-01-01','2011-12-31','Lavoro dipendente',            17.000, 14314.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2011-01-01','2011-12-31','Maternita'' e congedi',         2.000,  3409.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2011-01-01','2011-12-31','Congedi assist.Hand.',         34.000, 28334.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2011-01-01','2011-12-31','Mater/pater/congedi(Int)',      0.000,   471.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2012-01-01','2012-12-31','Lavoro dipendente',            46.000, 36230.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2012-01-01','2012-12-31','Maternita'' e congedi',         7.000,  9890.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2013-01-01','2013-12-31','Lavoro dipendente',            49.000, 44246.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2013-01-01','2013-12-31','Congedi e permessi',            4.000,  1681.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2013-01-01','2013-12-31','Mater/pater/congedi(Int)',      0.000,   121.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2014-01-01','2014-12-31','Lavoro dipendente',            52.000, 52214.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2014-01-01','2014-12-31','Congedi/permessi ad int.',      0.000,    91.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2014-01-01','2014-12-31','Congedi/permessi ad int.',      0.000,   821.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2015-01-01','2015-12-31','Lavoro dipendente',            28.000, 27380.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2015-01-01','2015-12-31','Congedi/permessi ad int.',      0.000,    45.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2015-01-01','2015-12-31','Mater/pater/congedi(Int)',      0.000,   251.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2015-06-01','2015-11-30','Lav.dipend. part-time',        22.000, 18009.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2015-06-01','2015-11-30','Congedi e permessi',            2.000,  1076.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2016-01-01','2016-01-31','Lav.dipend. part-time',         5.000,  2736.00, 'SOC. CONS. UNICREDIT SERVICES', 'Contributi ridotti al numero massimo riconoscibile nel periodo'),
    ('2016-02-01','2016-12-31','Lav.dipend. part-time',        39.000, 37638.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2017-01-01','2017-12-31','Lav.dipend. part-time',        43.000, 41040.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2018-01-01','2018-12-31','Lav.dipend. part-time',        43.000, 40842.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2019-01-01','2019-12-31','Lav.dipend. part-time',        43.000, 41415.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2020-01-01','2020-12-31','Lav.dipend. part-time',        43.000, 42918.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2021-01-01','2021-12-31','Lavoro dipendente',            52.000, 53310.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2022-01-01','2022-09-30','Lavoro dipendente',            40.000, 39959.00, 'SOC. CONS. UNICREDIT SERVICES', ''),
    ('2022-10-01','2022-12-31','Lavoro dipendente',            14.000, 16651.00, 'S.P.A. UNICREDIT S.P.A.', ''),
    ('2023-01-01','2023-12-31','Lavoro dipendente',            52.000, 59074.00, 'S.P.A. UNICREDIT S.P.A.', ''),
    ('2024-01-01','2024-12-31','Lavoro dipendente',            52.000, 58759.00, 'S.P.A. UNICREDIT S.P.A.', ''),
    ('2025-01-01','2025-12-31','Lavoro dipendente',            52.000, 60408.00, 'S.P.A. UNICREDIT S.P.A.', ''),
    ('2026-01-01','2026-06-30','Lavoro dipendente',            27.000, 28544.00, 'S.P.A. UNICREDIT S.P.A.', '')
  ) AS t(period_start, period_end, contribution_type, weeks_valid, income_amount, employer, note);
END $$;
