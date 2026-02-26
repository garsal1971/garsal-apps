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

ALTER TABLE cm_notification_rules
    ADD CONSTRAINT chk_entity_type
        CHECK (entity_type IN ('task', 'habit', 'event', 'objective'));
