-- ============================================================
-- Aggiorna il cron job send-notifications: ogni 5 minuti
-- Migration: 20260227100000_cron_send_notifications_5min
--
-- Cambia l'intervallo da ogni minuto (*/1) a ogni 5 minuti
--
-- PRE-REQUISITO — la service role key deve essere nel vault:
--
--   SELECT vault.create_secret(
--       'eyJ...service-role-key...',
--       'supabase_service_role_key'
--   );
-- ============================================================

SELECT cron.unschedule('send-notifications');

SELECT cron.schedule(
    'send-notifications',
    '*/5 * * * *',
    $$
    SELECT net.http_post(
        url     := 'https://jajlmmdsjlvzgcxiiypk.supabase.co/functions/v1/send-notifications',
        headers := jsonb_build_object(
            'Content-Type',  'application/json',
            'Authorization', 'Bearer ' || (
                SELECT decrypted_secret
                FROM   vault.decrypted_secrets
                WHERE  name = 'supabase_service_role_key'
                LIMIT  1
            )
        ),
        body    := '{}'::jsonb
    ) AS request_id;
    $$
);


-- ============================================================
-- Verifica
-- SELECT jobname, schedule FROM cron.job WHERE jobname = 'send-notifications';
-- ============================================================
