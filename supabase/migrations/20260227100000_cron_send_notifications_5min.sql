-- ============================================================
-- Aggiorna il cron job send-notifications: ogni 5 minuti
-- Migration: 20260227100000_cron_send_notifications_5min
--
-- Cambia l'intervallo da ogni minuto (*/1) a ogni 5 minuti
--
-- PRE-REQUISITO — eseguire UNA VOLTA nel SQL Editor di Supabase:
--
--   ALTER DATABASE postgres
--       SET "app.supabase_service_role_key" = 'eyJ...service-role-key...';
--   SELECT pg_reload_conf();
--
-- Senza questa impostazione il cron riceve 401 dall'edge function.
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
            'Authorization', 'Bearer ' || current_setting('app.supabase_service_role_key', true)
        ),
        body    := '{}'::jsonb
    ) AS request_id;
    $$
);


-- ============================================================
-- Verifica
-- SELECT jobname, schedule FROM cron.job WHERE jobname = 'send-notifications';
-- ============================================================
