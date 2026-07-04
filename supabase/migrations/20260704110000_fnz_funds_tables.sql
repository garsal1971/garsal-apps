-- Fondi comuni: calcolo quote di possesso attualizzate con indice ISTAT FOI
CREATE TABLE IF NOT EXISTS fnz_funds (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        text        NOT NULL,
  notes       text        NOT NULL DEFAULT '',
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS fnz_funds_user_idx ON fnz_funds(user_id);
ALTER TABLE fnz_funds ENABLE ROW LEVEL SECURITY;
CREATE POLICY "fnz_funds_own" ON fnz_funds FOR ALL USING (user_id = auth.uid());

-- Versamenti dei partecipanti (persona = testo libero, non richiede anagrafica separata)
CREATE TABLE IF NOT EXISTS fnz_fund_contributions (
  id          uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid           NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  fund_id     uuid           NOT NULL REFERENCES fnz_funds(id) ON DELETE CASCADE,
  participant text           NOT NULL,
  date        date           NOT NULL,
  amount      numeric(14, 2) NOT NULL CHECK (amount > 0),
  notes       text           NOT NULL DEFAULT '',
  created_at  timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS fnz_fund_contributions_user_idx ON fnz_fund_contributions(user_id);
CREATE INDEX IF NOT EXISTS fnz_fund_contributions_fund_idx ON fnz_fund_contributions(fund_id);
ALTER TABLE fnz_fund_contributions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "fnz_fund_contributions_own" ON fnz_fund_contributions FOR ALL USING (user_id = auth.uid());

-- Indice ISTAT FOI (media annua) — condiviso da tutti i fondi dell'utente
CREATE TABLE IF NOT EXISTS fnz_foi_index (
  user_id     uuid           NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  year        integer        NOT NULL CHECK (year > 1900),
  index_value numeric(10, 4) NOT NULL CHECK (index_value > 0),
  note        text           NOT NULL DEFAULT '',
  created_at  timestamptz    NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, year)
);
ALTER TABLE fnz_foi_index ENABLE ROW LEVEL SECURITY;
CREATE POLICY "fnz_foi_index_own" ON fnz_foi_index FOR ALL USING (user_id = auth.uid());

-- Seed: indice FOI 2022-2026 e fondo "Crypto" con i versamenti noti, per garsal1971@gmail.com
DO $$
DECLARE
  v_user_id uuid;
  v_fund_id uuid;
BEGIN
  SELECT id INTO v_user_id FROM auth.users WHERE email = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user_id IS NULL THEN
    RETURN;
  END IF;

  INSERT INTO fnz_foi_index (user_id, year, index_value, note) VALUES
    (v_user_id, 2022, 113.2000,  'Base'),
    (v_user_id, 2023, 119.5392,  '+5.6% annuo'),
    (v_user_id, 2024, 120.7346,  '+1.0% annuo'),
    (v_user_id, 2025, 122.5456,  '+1.5% annuo'),
    (v_user_id, 2026, 126.2220,  'Stima +3.0% annuo, dato provvisorio')
  ON CONFLICT (user_id, year) DO NOTHING;

  IF NOT EXISTS (SELECT 1 FROM fnz_funds WHERE user_id = v_user_id AND name = 'Crypto') THEN
    INSERT INTO fnz_funds (user_id, name, notes)
    VALUES (v_user_id, 'Crypto', 'Fondo comune per il portafoglio crypto: quote attualizzate al potere d''acquisto del primo versamento tramite indice ISTAT FOI.')
    RETURNING id INTO v_fund_id;

    INSERT INTO fnz_fund_contributions (user_id, fund_id, participant, date, amount) VALUES
      (v_user_id, v_fund_id, 'Salvatore',   '2022-02-15', 100.00),
      (v_user_id, v_fund_id, 'Ada',         '2022-02-15', 100.00),
      (v_user_id, v_fund_id, 'Lady Gauss',  '2022-02-15', 100.00),
      (v_user_id, v_fund_id, 'Filippa',     '2022-02-15', 100.00),
      (v_user_id, v_fund_id, 'Rosa',        '2022-02-15', 100.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-02-28', 1000.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-03-15', 750.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-04-01', 500.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-05-03', 500.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-05-17', 1000.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-06-07', 400.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-07-03', 500.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2022-09-12', 250.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2023-12-23', 1000.00),
      (v_user_id, v_fund_id, 'Salvatore',   '2026-07-03', 200.00);
  END IF;
END $$;
