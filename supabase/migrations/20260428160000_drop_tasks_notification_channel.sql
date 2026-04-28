-- ============================================================
-- Rimozione ts_tasks.notification_channel (ridondante)
-- Migration: 20260428160000_drop_tasks_notification_channel
--
-- Il canale di notifica è già memorizzato in
-- cm_notification_rules.channel. Il campo su ts_tasks era
-- duplicato e usato solo per ripristinare il selettore UI
-- al momento della modifica. tasks.html ora legge il canale
-- direttamente da cm_notification_rules.
-- ============================================================

ALTER TABLE ts_tasks DROP COLUMN IF EXISTS notification_channel;

-- ============================================================
-- Fine migration
-- ============================================================
