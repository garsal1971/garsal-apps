-- ============================================================
-- Smart Block: device token e pulizia schema
-- Migration: 20260428130000_smart_block_setup
-- Data: 2026-04-28
--
-- 1. Aggiunge smart_block_device_token a cm_user_notification_settings
--    → tasks.html legge il token tramite RPC get_smart_block_token (anon)
--
-- 2. Crea cm_smart_block_devices — registro opzionale dei dispositivi
--    → L'app Android può registrare il proprio UUID (anon INSERT)
--
-- 3. Rimuove ts_tasks.notification_channel (ridondante)
--    → Il canale è già memorizzato in cm_notification_rules.channel
--    → tasks.html legge il canale dalla regola al momento della modifica
-- ============================================================

-- 1. Colonna nel profilo notifiche utente
ALTER TABLE cm_user_notification_settings
ADD COLUMN IF NOT EXISTS smart_block_device_token text;

-- 2. Tabella registro dispositivi (senza user_id — identità per device_token)
CREATE TABLE IF NOT EXISTS cm_smart_block_devices (
    device_token  text         PRIMARY KEY,
    label         text,                        -- es. "Samsung Galaxy S24"
    registered_at timestamptz  NOT NULL DEFAULT now()
);

ALTER TABLE cm_smart_block_devices ENABLE ROW LEVEL SECURITY;

-- Chiunque (incluso anon) può registrare il proprio dispositivo
DROP POLICY IF EXISTS "anon_insert_device" ON cm_smart_block_devices;
CREATE POLICY "anon_insert_device"
    ON cm_smart_block_devices
    FOR INSERT
    TO anon
    WITH CHECK (true);

-- Solo utenti autenticati leggono la lista (tasks.html)
DROP POLICY IF EXISTS "auth_select_devices" ON cm_smart_block_devices;
CREATE POLICY "auth_select_devices"
    ON cm_smart_block_devices
    FOR SELECT
    TO authenticated
    USING (true);

-- Consente all'app Android (anon) di aggiornare il label del proprio dispositivo
DROP POLICY IF EXISTS "anon_update_own_device" ON cm_smart_block_devices;
CREATE POLICY "anon_update_own_device"
    ON cm_smart_block_devices
    FOR UPDATE
    TO anon
    USING (true)
    WITH CHECK (true);

-- 3. Rimuove colonna ridondante da ts_tasks
ALTER TABLE ts_tasks DROP COLUMN IF EXISTS notification_channel;

-- ============================================================
-- Fine migration
-- ============================================================
