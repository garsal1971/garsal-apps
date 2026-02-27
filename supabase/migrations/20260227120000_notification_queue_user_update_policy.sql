-- ============================================================
-- Aggiunge policy RLS UPDATE su cm_notification_queue
-- Migration: 20260227120000_notification_queue_user_update_policy
--
-- Permette all'utente autenticato di aggiornare le proprie notifiche
-- in coda (es. cancel → status='cancelled', snooze → fire_at/send_count).
-- La policy SELECT esiste già dalla migration 20260225120000.
-- ============================================================

CREATE POLICY "Utente aggiorna la propria queue"
    ON cm_notification_queue
    FOR UPDATE
    USING     (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ============================================================
-- Verifica
-- SELECT policyname, cmd FROM pg_policies
-- WHERE tablename = 'cm_notification_queue';
-- ============================================================
