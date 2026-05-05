-- ============================================================
-- Aggiunge CHECK constraint su cm_notification_rules.entity_type
-- Migration: 20260226110000_rules_entity_type_check
--
-- Valori ammessi per entity_type (mappati all'app):
--   app='tasks'   → entity_type='task'
--   app='habits'  → entity_type='habit'
--   app='events'  → entity_type='event'
--   app='weight'  → entity_type='objective'
-- ============================================================

-- Normalizza entity_type storico: l'app scriveva i sub-tipi di task
-- (es. 'simple_recurring', 'recurring', 'multiple') invece del tipo generico.
UPDATE cm_notification_rules
    SET entity_type = CASE app
        WHEN 'tasks'  THEN 'task'
        WHEN 'habits' THEN 'habit'
        WHEN 'events' THEN 'event'
        WHEN 'weight' THEN 'objective'
        ELSE entity_type
    END
    WHERE entity_type NOT IN ('task', 'habit', 'event', 'objective');

ALTER TABLE cm_notification_rules
    DROP CONSTRAINT IF EXISTS chk_entity_type;
ALTER TABLE cm_notification_rules
    ADD CONSTRAINT chk_entity_type
        CHECK (entity_type IN ('task', 'habit', 'event', 'objective'));
