-- ============================================================
-- Aggiunge policy RLS DELETE su cm_notification_queue
-- Migration: 20260227130000_notification_queue_user_delete_policy
--
-- Permette all'utente di eliminare definitivamente solo le proprie
-- notifiche con status = 'cancelled' (hard delete dal bottone UI).
-- ============================================================

CREATE POLICY "Utente elimina la propria queue annullata"
    ON cm_notification_queue
    FOR DELETE
    USING (auth.uid() = user_id AND status = 'cancelled');

-- ============================================================
-- Verifica:
-- SELECT policyname, cmd FROM pg_policies
-- WHERE tablename = 'cm_notification_queue';
-- ============================================================
