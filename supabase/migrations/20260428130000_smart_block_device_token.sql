-- ============================================================
-- Migration: smart_block_device_token
-- Data: 2026-04-28
--
-- 1. Aggiunge smart_block_device_token a cm_user_notification_settings
--    → tasks.html salva/legge il token del dispositivo Android
--      con piena persistenza cross-browser (niente più localStorage)
--
-- 2. Crea cm_smart_block_devices — registro pubblico dei dispositivi
--    → L'app Android scrive il proprio UUID al primo avvio (anon INSERT)
--    → tasks.html mostra la lista e l'utente seleziona il proprio dispositivo
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
CREATE POLICY "anon_insert_device"
    ON cm_smart_block_devices
    FOR INSERT
    TO anon
    WITH CHECK (true);

-- Solo utenti autenticati leggono la lista (tasks.html)
CREATE POLICY "auth_select_devices"
    ON cm_smart_block_devices
    FOR SELECT
    TO authenticated
    USING (true);

-- Consente all'app Android (anon) di aggiornare il label del proprio dispositivo
CREATE POLICY "anon_update_own_device"
    ON cm_smart_block_devices
    FOR UPDATE
    TO anon
    USING (true)
    WITH CHECK (true);
