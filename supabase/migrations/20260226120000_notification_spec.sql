-- ============================================================
-- Aggiunge:
--   1. cm_notification_offsets — tabella di configurazione
--      degli anticipi notifica (es. "5 minuti prima")
--   2. cm_notification_rules.notification_spec — colonna JSONB
--      che descrive QUANDO e COME generare le notifiche
--
-- Struttura JSON di notification_spec:
--
--   Tipo "quanto_prima":
--   {
--     "type": "quanto_prima",
--     "offsets": [2, 5]    -- array di id da cm_notification_offsets
--   }
--
--   (altri tipi previsti in future migration)
-- ============================================================

-- ------------------------------------------------------------
-- 1. Tabella di configurazione degli anticipi
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cm_notification_offsets (
    id          serial      PRIMARY KEY,
    label       text        NOT NULL,          -- etichetta leggibile (IT)
    minutes     integer     NOT NULL CHECK (minutes > 0),  -- minuti di anticipo
    sort_order  integer     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  cm_notification_offsets           IS 'Configurazione degli anticipi disponibili per le notifiche (es. "5 minuti prima").';
COMMENT ON COLUMN cm_notification_offsets.minutes   IS 'Numero di minuti prima dell''evento a cui inviare la notifica.';
COMMENT ON COLUMN cm_notification_offsets.sort_order IS 'Ordine di visualizzazione nel selettore.';

-- Valori predefiniti
INSERT INTO cm_notification_offsets (id, label, minutes, sort_order) VALUES
    (1,  '1 minuto prima',   1,    10),
    (2,  '5 minuti prima',   5,    20),
    (3,  '10 minuti prima',  10,   30),
    (4,  '15 minuti prima',  15,   40),
    (5,  '30 minuti prima',  30,   50),
    (6,  '1 ora prima',      60,   60),
    (7,  '2 ore prima',      120,  70),
    (8,  '1 giorno prima',   1440, 80)
ON CONFLICT (id) DO NOTHING;

-- Blocca il riutilizzo degli id già assegnati
SELECT setval('cm_notification_offsets_id_seq', 8, true);

-- ------------------------------------------------------------
-- 2. Colonna notification_spec in cm_notification_rules
-- ------------------------------------------------------------
ALTER TABLE cm_notification_rules
    ADD COLUMN IF NOT EXISTS notification_spec jsonb;

COMMENT ON COLUMN cm_notification_rules.notification_spec IS
'Specifica JSON della notifica. Esempi:
  { "type": "quanto_prima", "offsets": [2, 5] }
  Il campo "type" identifica il tipo di logica;
  "offsets" è un array di id di cm_notification_offsets.';

-- Vincolo: se presente, deve avere almeno il campo "type"
ALTER TABLE cm_notification_rules
    ADD CONSTRAINT chk_notification_spec_type
        CHECK (
            notification_spec IS NULL
            OR (notification_spec ? 'type')
        );
