-- Migration for Finanza, Casa Rosa and Contabilità
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Portafogli
CREATE TABLE IF NOT EXISTS portfolios (
  id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid          NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        text          NOT NULL,
  description text          NOT NULL DEFAULT '',
  color       text          NOT NULL DEFAULT '#4f46e5',
  ownership_percentage numeric(5, 2) NOT NULL DEFAULT 100,
  created_at  timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS portfolios_user_idx ON portfolios(user_id);
ALTER TABLE portfolios ENABLE ROW LEVEL SECURITY;
CREATE POLICY "portfolios_own" ON portfolios FOR ALL USING (user_id = auth.uid());

-- Categorie tag configurabili per utente
CREATE TABLE IF NOT EXISTS tag_categories (
  id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid          NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        text          NOT NULL,
  options     text[]        NOT NULL DEFAULT '{}',
  created_at  timestamptz   NOT NULL DEFAULT now(),
  UNIQUE(user_id, name)
);
CREATE INDEX IF NOT EXISTS tag_categories_user_idx ON tag_categories(user_id);
ALTER TABLE tag_categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY "tag_categories_own" ON tag_categories FOR ALL USING (user_id = auth.uid());

-- Prodotti finanziari
CREATE TABLE IF NOT EXISTS products (
  id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid          NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  symbol      text          NOT NULL,
  name        text          NOT NULL,
  asset_type  text          NOT NULL DEFAULT 'stock',
  exchange    text          NOT NULL DEFAULT '',
  currency    text          NOT NULL DEFAULT 'EUR',
  tags        jsonb         NOT NULL DEFAULT '{}',
  isin        text,
  created_at  timestamptz   NOT NULL DEFAULT now(),
  UNIQUE(user_id, symbol)
);
CREATE INDEX IF NOT EXISTS products_user_idx ON products(user_id);
CREATE INDEX IF NOT EXISTS products_symbol_idx ON products(symbol);
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
CREATE POLICY "products_own" ON products FOR ALL USING (user_id = auth.uid());

-- Dossier titoli
CREATE TABLE IF NOT EXISTS dossiers (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  bank_name   text        NOT NULL,
  title       text        NOT NULL,
  number      text        NOT NULL,
  notes       text        NOT NULL DEFAULT '',
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, bank_name, number)
);
CREATE INDEX IF NOT EXISTS dossiers_user_idx ON dossiers(user_id);
ALTER TABLE dossiers ENABLE ROW LEVEL SECURITY;
CREATE POLICY "dossiers_own" ON dossiers FOR ALL USING (user_id = auth.uid());

-- Transazioni (carico = BUY, scarico = SELL)
CREATE TABLE IF NOT EXISTS transactions (
  id           uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid            NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  portfolio_id uuid            NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  product_id   uuid            NOT NULL REFERENCES products(id)   ON DELETE RESTRICT,
  dossier_id   uuid            REFERENCES dossiers(id) ON DELETE SET NULL,
  type         text            NOT NULL CHECK (type IN ('BUY', 'SELL')),
  quantity     numeric(18, 8)  NOT NULL CHECK (quantity > 0),
  price        numeric(18, 4)  NOT NULL CHECK (price >= 0),
  commission   numeric(18, 4)  NOT NULL DEFAULT 0 CHECK (commission >= 0),
  date         date            NOT NULL,
  notes        text            NOT NULL DEFAULT '',
  created_at   timestamptz     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS transactions_user_idx ON transactions(user_id);
CREATE INDEX IF NOT EXISTS transactions_portfolio_idx ON transactions(portfolio_id);
CREATE INDEX IF NOT EXISTS transactions_product_idx ON transactions(product_id);
CREATE INDEX IF NOT EXISTS transactions_dossier_idx ON transactions(dossier_id);
CREATE INDEX IF NOT EXISTS transactions_date_idx ON transactions(date DESC);
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "transactions_own" ON transactions FOR ALL USING (user_id = auth.uid());

-- Cache prezzi
CREATE TABLE IF NOT EXISTS price_cache (
  symbol       text          PRIMARY KEY,
  price        numeric(18, 4),
  prev_close   numeric(18, 4),
  change_amt   numeric(18, 4),
  change_pct   numeric(8, 4),
  currency     text          NOT NULL DEFAULT 'USD',
  market_state text          NOT NULL DEFAULT 'REGULAR',
  updated_at   timestamptz   NOT NULL DEFAULT now()
);
-- price_cache è condivisa, ma mettiamo RLS per sicurezza (solo lettura per autenticati)
ALTER TABLE price_cache ENABLE ROW LEVEL SECURITY;
CREATE POLICY "price_cache_read" ON price_cache FOR SELECT USING (auth.role() = 'authenticated');

-- Storicizzazione Prezzi
CREATE TABLE IF NOT EXISTS price_history (
  id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  symbol       text          NOT NULL,
  price        numeric(18, 4) NOT NULL,
  price_date   date          NOT NULL DEFAULT CURRENT_DATE,
  created_at   timestamptz   NOT NULL DEFAULT now(),
  UNIQUE(symbol, price_date)
);
CREATE INDEX IF NOT EXISTS price_history_symbol_idx ON price_history(symbol);
CREATE INDEX IF NOT EXISTS price_history_date_idx ON price_history(price_date DESC);
ALTER TABLE price_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY "price_history_read" ON price_history FOR SELECT USING (auth.role() = 'authenticated');

-- Trigger per sincronizzazione price_history da price_cache
CREATE OR REPLACE FUNCTION handle_price_history_sync()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO price_history (symbol, price, price_date)
  VALUES (NEW.symbol, NEW.price, CURRENT_DATE)
  ON CONFLICT (symbol, price_date)
  DO UPDATE SET
    price = EXCLUDED.price,
    created_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS on_price_cache_update ON price_cache;
CREATE TRIGGER on_price_cache_update
AFTER INSERT OR UPDATE ON price_cache
FOR EACH ROW EXECUTE FUNCTION handle_price_history_sync();

-- Log per Edge Function
CREATE TABLE IF NOT EXISTS fn_logs (
  id          bigserial    PRIMARY KEY,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  level       text         NOT NULL CHECK (level IN ('ERROR','WARN','INFO','DEBUG')),
  message     text         NOT NULL,
  data        jsonb,
  request_id  text
);
CREATE INDEX IF NOT EXISTS fn_logs_created_at_idx ON fn_logs (created_at DESC);
ALTER TABLE fn_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY "fn_logs_select" ON fn_logs FOR SELECT USING (auth.role() = 'authenticated');

-- Prestiti / Mutui
CREATE TABLE IF NOT EXISTS loans (
  id                    uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               uuid           NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  type                  text           NOT NULL CHECK (type IN ('MUTUO', 'PRESTITO')),
  name                  text           NOT NULL DEFAULT '',
  capitale              numeric(18, 2) NOT NULL CHECK (capitale >= 0),
  rata                  numeric(18, 2),
  totale_rate           integer,
  giorno_prima_rata     date,
  data_inizio_prestito  date,
  interesse             numeric(10, 6),
  notes                 text           NOT NULL DEFAULT '',
  ownership_percentage  numeric(5, 2)  NOT NULL DEFAULT 100,
  created_at            timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS loans_user_idx ON loans(user_id);
ALTER TABLE loans ENABLE ROW LEVEL SECURITY;
CREATE POLICY "loans_own" ON loans FOR ALL USING (user_id = auth.uid());

-- Altri Asset
CREATE TABLE IF NOT EXISTS other_asset_types (
  id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid          NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  name        text          NOT NULL,
  created_at  timestamptz   NOT NULL DEFAULT now(),
  UNIQUE(user_id, name)
);
CREATE INDEX IF NOT EXISTS other_asset_types_user_idx ON other_asset_types(user_id);
ALTER TABLE other_asset_types ENABLE ROW LEVEL SECURITY;
CREATE POLICY "other_asset_types_own" ON other_asset_types FOR ALL USING (user_id = auth.uid());

CREATE TABLE IF NOT EXISTS other_assets (
  id                    uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               uuid           NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title                 text           NOT NULL DEFAULT '',
  description           text           NOT NULL DEFAULT '',
  asset_type            text           NOT NULL DEFAULT '',
  value                 numeric(18, 2) NOT NULL DEFAULT 0,
  valuation_date        date           NOT NULL DEFAULT CURRENT_DATE,
  ownership_percentage  numeric(5, 2)  NOT NULL DEFAULT 100,
  notes                 text           NOT NULL DEFAULT '',
  created_at            timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS other_assets_user_idx ON other_assets(user_id);
ALTER TABLE other_assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY "other_assets_own" ON other_assets FOR ALL USING (user_id = auth.uid());

-- Contabilità Cointestata
CREATE TABLE IF NOT EXISTS acct_transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  anno integer NOT NULL,
  data date NOT NULL,
  persona text NOT NULL CHECK (persona IN ('TERESA', 'SALVATORE')),
  tipo text NOT NULL CHECK (tipo IN ('BONIFICO', 'ALTRO', 'MENSA')),
  importo numeric(12,2) NOT NULL,
  descrizione text DEFAULT '',
  causale text DEFAULT '',
  tipo_versamento text DEFAULT '',
  provenienza text,
  created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_acct_trans_user ON acct_transactions(user_id);
ALTER TABLE acct_transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "acct_transactions_own" ON acct_transactions FOR ALL USING (user_id = auth.uid());

-- Integrazioni Bancarie
CREATE TABLE IF NOT EXISTS bank_integrations (
  id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid          NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  bank_name       text          NOT NULL,
  institution_id  text          NOT NULL,
  requisition_id  uuid          NOT NULL,
  account_id      uuid,
  status          text          NOT NULL DEFAULT 'PENDING',
  last_sync       timestamptz,
  created_at      timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS bank_integrations_user_idx ON bank_integrations(user_id);
ALTER TABLE bank_integrations ENABLE ROW LEVEL SECURITY;
CREATE POLICY "bank_integrations_own" ON bank_integrations FOR ALL USING (user_id = auth.uid());

-- Storicizzazione Dashboard
CREATE TABLE IF NOT EXISTS dashboard_snapshots (
  id               uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid            NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  snapshot_date    date            NOT NULL DEFAULT CURRENT_DATE,
  patrimonio_netto numeric(18, 2)  NOT NULL,
  asset_totali     numeric(18, 2)  NOT NULL,
  debiti_totali    numeric(18, 2)  NOT NULL,
  details          jsonb           NOT NULL DEFAULT '{}',
  portafogli_totali numeric(18, 2),
  created_at       timestamptz     NOT NULL DEFAULT now(),
  updated_at       timestamptz     NOT NULL DEFAULT now(),
  UNIQUE(user_id, snapshot_date)
);
CREATE INDEX IF NOT EXISTS idx_dashboard_snapshots_user_date ON dashboard_snapshots(user_id, snapshot_date DESC);
ALTER TABLE dashboard_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY "dashboard_snapshots_own" ON dashboard_snapshots FOR ALL USING (user_id = auth.uid());

-- CASA ROSA (prefisso cntrs_)
CREATE TABLE IF NOT EXISTS cntrs_categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  name text NOT NULL,
  created_at timestamptz DEFAULT now(),
  UNIQUE(user_id, name)
);
ALTER TABLE cntrs_categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY "cntrs_categories_own" ON cntrs_categories FOR ALL USING (user_id = auth.uid());

CREATE TABLE IF NOT EXISTS cntrs_transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  anno integer NOT NULL,
  data date NOT NULL,
  descrizione text NOT NULL,
  importo_voce numeric(12,2) DEFAULT 0,
  category_id uuid REFERENCES cntrs_categories(id) ON DELETE SET NULL,
  note text DEFAULT '',
  created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cntrs_trans_user ON cntrs_transactions(user_id);
ALTER TABLE cntrs_transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "cntrs_transactions_own" ON cntrs_transactions FOR ALL USING (user_id = auth.uid());

CREATE TABLE IF NOT EXISTS cntrs_legacy_data (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  foglio text NOT NULL,
  dati jsonb NOT NULL,
  created_at timestamptz DEFAULT now()
);
ALTER TABLE cntrs_legacy_data ENABLE ROW LEVEL SECURITY;
CREATE POLICY "cntrs_legacy_data_own" ON cntrs_legacy_data FOR ALL USING (user_id = auth.uid());

CREATE TABLE IF NOT EXISTS cntrs_saldi (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
  data date NOT NULL,
  saldo_contabile numeric(12,2) DEFAULT 0,
  saldo_disponibile numeric(12,2) DEFAULT 0,
  note text DEFAULT '',
  created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cntrs_saldi_user ON cntrs_saldi(user_id);
ALTER TABLE cntrs_saldi ENABLE ROW LEVEL SECURITY;
CREATE POLICY "cntrs_saldi_own" ON cntrs_saldi FOR ALL USING (user_id = auth.uid());
