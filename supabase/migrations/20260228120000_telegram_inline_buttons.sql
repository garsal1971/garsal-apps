-- ============================================================
-- Migration: 20260228120000_telegram_inline_buttons
--
-- Aggiorna il check constraint su cm_notification_queue.status
-- per includere i valori 'sending' e 'snoozed' usati dalla
-- Edge function send-notifications (già in uso nel codice).
--
-- Aggiunge la colonna send_count se non già presente.
-- ============================================================

-- Aggiunge send_count se non esiste
ALTER TABLE cm_notification_queue
  ADD COLUMN IF NOT EXISTS send_count integer NOT NULL DEFAULT 0;

-- Aggiorna il check constraint per includere tutti gli status usati
ALTER TABLE cm_notification_queue
  DROP CONSTRAINT IF EXISTS cm_notification_queue_status_check;

ALTER TABLE cm_notification_queue
  ADD CONSTRAINT cm_notification_queue_status_check
  CHECK (status IN ('pending', 'sending', 'sent', 'failed', 'cancelled', 'snoozed'));

-- ============================================================
-- Fine migration
-- ============================================================
