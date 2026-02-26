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

-- 1. Rimuovi il vecchio vincolo UNIQUE che includeva offset_minutes
ALTER TABLE cm_notification_rules
    DROP CONSTRAINT IF EXISTS cm_notification_rules_user_id_app_entity_id_offset_minutes_ch;

-- 2. Rimuovi le colonne
ALTER TABLE cm_notification_rules
    DROP COLUMN IF EXISTS offset_minutes,
    DROP COLUMN IF EXISTS offset_label;

-- 3. notification_spec obbligatorio (sostituisce i campi rimossi)
ALTER TABLE cm_notification_rules
    ALTER COLUMN notification_spec SET NOT NULL;

-- 4. Nuovo UNIQUE: una regola per entità × canale
ALTER TABLE cm_notification_rules
    ADD CONSTRAINT uq_rules_entity_channel
        UNIQUE (user_id, app, entity_id, channel);
