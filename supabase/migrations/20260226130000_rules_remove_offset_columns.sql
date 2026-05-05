-- ============================================================
-- Rimuove offset_minutes e offset_label da cm_notification_rules
-- Migration: 20260226130000_rules_remove_offset_columns
--
-- Con notification_spec JSONB gli anticipi sono gestiti
-- tramite l'array "offsets" → i due campi scalari sono ridondanti.
--
-- Cambiamenti:
--   - DROP COLUMN offset_minutes
--   - DROP COLUMN offset_label
--   - DROP vecchio UNIQUE (user_id, app, entity_id, offset_minutes, channel)
--   - ADD nuovo  UNIQUE (user_id, app, entity_id, channel)
--   - notification_spec diventa NOT NULL
-- ============================================================

-- 0. Rimuovi temporaneamente chk_entity_type per permettere la normalizzazione
--    dei dati senza blocchi (verrà ricreato a fine migration)
ALTER TABLE cm_notification_rules
    DROP CONSTRAINT IF EXISTS chk_entity_type;

-- 1. Rimuovi il vecchio vincolo UNIQUE che includeva offset_minutes
ALTER TABLE cm_notification_rules
    DROP CONSTRAINT IF EXISTS cm_notification_rules_user_id_app_entity_id_offset_minutes_ch;

-- 2. Rimuovi le colonne
ALTER TABLE cm_notification_rules
    DROP COLUMN IF EXISTS offset_minutes,
    DROP COLUMN IF EXISTS offset_label;

-- 3. Normalizza entity_type (l'app scriveva sub-tipi task invece di 'task')
UPDATE cm_notification_rules
    SET entity_type = CASE app
        WHEN 'tasks'  THEN 'task'
        WHEN 'habits' THEN 'habit'
        WHEN 'events' THEN 'event'
        WHEN 'weight' THEN 'objective'
        ELSE entity_type
    END
    WHERE entity_type NOT IN ('task', 'habit', 'event', 'objective');

-- 4. notification_spec obbligatorio (sostituisce i campi rimossi)
UPDATE cm_notification_rules
    SET notification_spec = '{}'::jsonb
    WHERE notification_spec IS NULL;
ALTER TABLE cm_notification_rules
    ALTER COLUMN notification_spec SET NOT NULL;

-- 5. Ricrea chk_entity_type sui dati ora normalizzati
ALTER TABLE cm_notification_rules
    ADD CONSTRAINT chk_entity_type
        CHECK (entity_type IN ('task', 'habit', 'event', 'objective'));

-- 6. Nuovo UNIQUE: una regola per entità × canale
ALTER TABLE cm_notification_rules
    DROP CONSTRAINT IF EXISTS uq_rules_entity_channel;
ALTER TABLE cm_notification_rules
    ADD CONSTRAINT uq_rules_entity_channel
        UNIQUE (user_id, app, entity_id, channel);
