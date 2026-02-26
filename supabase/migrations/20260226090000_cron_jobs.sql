-- ============================================================
-- Cron jobs per Edge Functions di notifica
-- Migration: 20260226090000_cron_jobs
--
-- Richiede estensioni abilitate nel Supabase Dashboard:
--   - pg_net   (Database → Extensions → pg_net)
--   - pg_cron  (Database → Extensions → pg_cron)
--
-- PREREQUISITO — esegui MANUALMENTE nella SQL Editor prima
-- di applicare questa migration:
--
--   ALTER DATABASE postgres
--     SET "app.supabase_service_role_key" = '<LA TUA SERVICE ROLE KEY>';
--
-- La service role key si trova in:
--   Supabase Dashboard → Settings → API → service_role (secret)
-- ============================================================


-- ============================================================
-- Job 1: fill-notification-queue
-- Ogni 6 ore — calcola fire_at e popola la queue
-- ============================================================
SELECT cron.schedule(
    'fill-notification-queue',
    '0 */6 * * *',
    $$
    SELECT net.http_post(
        url     := 'https://jajlmmdsjlvzgcxiiypk.supabase.co/functions/v1/fill-notification-queue',
        headers := jsonb_build_object(
            'Content-Type',  'application/json',
            'Authorization', 'Bearer ' || current_setting('app.supabase_service_role_key', true)
        ),
        body    := '{}'::jsonb
    ) AS request_id;
    $$
);


-- ============================================================
-- Job 2: send-notifications
-- Ogni minuto — invia le notifiche in coda
-- ============================================================
SELECT cron.schedule(
    'send-notifications',
    '* * * * *',
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
-- Verifica job registrati
-- SELECT * FROM cron.job;
-- ============================================================


-- ============================================================
-- Per rimuovere i job (rollback manuale):
--   SELECT cron.unschedule('fill-notification-queue');
--   SELECT cron.unschedule('send-notifications');
-- ============================================================
