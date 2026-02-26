-- ============================================================
-- Tabella dei preset di promemoria per l'app Tasks
-- Migration: 20260226150000_reminder_presets
--
-- ts_reminder_presets — valori disponibili nel selettore
-- promemoria del task. Sostituisce la lista hardcoded in JS.
--
-- Colonne:
--   label          text    Etichetta visualizzata (es. "1 ora")
--   offset_minutes int     Anticipo effettivo in minuti
--   sort_order     int     Ordine di visualizzazione
--   active         bool    Se false, non compare nel selettore
-- ============================================================

CREATE TABLE IF NOT EXISTS ts_reminder_presets (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    label          text        NOT NULL,
    offset_minutes int         NOT NULL CHECK (offset_minutes > 0),
    sort_order     int         NOT NULL DEFAULT 0,
    active         boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_reminder_preset_label UNIQUE (label)
);

-- Dati iniziali — rispecchiano la lista hardcoded originale
INSERT INTO ts_reminder_presets (label, offset_minutes, sort_order) VALUES
    ('1 minuto',    1,      10),
    ('5 minuti',    5,      20),
    ('10 minuti',   10,     30),
    ('15 minuti',   15,     40),
    ('30 minuti',   30,     50),
    ('1 ora',       60,     60),
    ('2 ore',       120,    70),
    ('4 ore',       240,    80),
    ('8 ore',       480,    90),
    ('1 giorno',    1440,   100),
    ('2 giorni',    2880,   110),
    ('3 giorni',    4320,   120),
    ('1 settimana', 10080,  130),
    ('2 settimane', 20160,  140)
ON CONFLICT (label) DO NOTHING;

-- RLS: lettura pubblica (autenticati), scrittura solo admin
ALTER TABLE ts_reminder_presets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "reminder_presets_select"
    ON ts_reminder_presets FOR SELECT
    USING (auth.uid() IS NOT NULL);

COMMENT ON TABLE ts_reminder_presets IS
    'Preset di anticipo disponibili nel selettore promemoria dei task.';
