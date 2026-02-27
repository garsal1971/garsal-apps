-- ============================================================
-- Aggiunge send_count e stato 'sending' a cm_notification_queue
-- Migration: 20260227110000_notification_queue_send_count
--
-- Cambamenti:
--   1. Nuova colonna send_count (integer, default 0)
--      — conta quante volte la notifica è stata tentata (max 5)
--   2. Aggiornamento CHECK su status: aggiunto 'sending'
--      — pending  → notifica pronta ma non ancora presa in carico
--      — sending  → in fase di invio (1–4 tentativi effettuati)
--      — sent     → 5 tentativi completati; inclusa nel digest finale
--      — failed   → canale non configurato / errore permanente
--      — cancelled → cancellata prima dell'invio
--   3. Indice aggiornato per coprire anche status='sending'
-- ============================================================

-- 1. Aggiungi colonna send_count
ALTER TABLE cm_notification_queue
    ADD COLUMN IF NOT EXISTS send_count integer NOT NULL DEFAULT 0;

-- 2. Rimuovi il vecchio CHECK e aggiungine uno nuovo con 'sending'
ALTER TABLE cm_notification_queue
    DROP CONSTRAINT IF EXISTS cm_notification_queue_status_check;

ALTER TABLE cm_notification_queue
    ADD CONSTRAINT cm_notification_queue_status_check
    CHECK (status IN ('pending', 'sending', 'sent', 'failed', 'cancelled'));

-- 3. Ricrea l'indice parziale per includere 'sending'
DROP INDEX IF EXISTS idx_queue_fire_at_status;

CREATE INDEX idx_queue_fire_at_status
    ON cm_notification_queue (fire_at, status)
    WHERE status IN ('pending', 'sending');

-- ============================================================
-- Verifica
-- SELECT column_name, data_type, column_default
-- FROM information_schema.columns
-- WHERE table_name = 'cm_notification_queue'
--   AND column_name = 'send_count';
-- ============================================================
