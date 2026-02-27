-- ============================================================
-- Aggiunge stato 'snoozed' a cm_notification_queue
-- Migration: 20260227140000_notification_queue_snoozed_status
--
-- Semantica:
--   snoozed → l'utente ha sospeso la notifica; fire_at è stato
--             spostato in avanti. Il job send-notifications
--             riporterà lo stato a 'pending' quando fire_at <= now.
--
-- Flusso:
--   pending/sending/sent → [utente clicca Sospendi] → snoozed
--   snoozed → [Job 2 Fase 0, fire_at <= now]        → pending
--   pending → [Job 2 Fase 1]                         → sending → sent
-- ============================================================

-- 1. Aggiorna CHECK constraint per includere 'snoozed'
ALTER TABLE cm_notification_queue
    DROP CONSTRAINT IF EXISTS cm_notification_queue_status_check;

ALTER TABLE cm_notification_queue
    ADD CONSTRAINT cm_notification_queue_status_check
    CHECK (status IN ('pending', 'sending', 'sent', 'failed', 'cancelled', 'snoozed'));

-- 2. Ricrea l'indice parziale includendo 'snoozed'
--    (Job 2 Fase 0 deve trovare i snoozed con fire_at <= now)
DROP INDEX IF EXISTS idx_queue_fire_at_status;

CREATE INDEX idx_queue_fire_at_status
    ON cm_notification_queue (fire_at, status)
    WHERE status IN ('pending', 'sending', 'snoozed');

-- ============================================================
-- Verifica stati validi:
-- SELECT DISTINCT status FROM cm_notification_queue;
-- ============================================================
