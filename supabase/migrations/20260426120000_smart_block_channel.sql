-- ============================================================
-- Smart Block: supporto canale 'smart_block'
-- Migration: 20260426120000_smart_block_channel
--
-- Cambiamenti:
--   1. cm_notification_rules.channel — aggiunge 'smart_block'
--      al CHECK constraint
--   2. ts_tasks.notification_channel — colonna per persistere
--      la scelta del canale per task (telegram | smart_block)
--   3. cm_notification_queue — nuove RLS per l'app Android
--      (client anon) che legge/aggiorna i propri item smart_block
-- ============================================================

-- ------------------------------------------------------------
-- 1. cm_notification_rules.channel: aggiunge 'smart_block'
-- ------------------------------------------------------------
-- Rimuove il vecchio vincolo inline (nome auto-generato da Postgres)
ALTER TABLE cm_notification_rules
    DROP CONSTRAINT IF EXISTS cm_notification_rules_channel_check;

-- Nuovo vincolo con smart_block
ALTER TABLE cm_notification_rules
    ADD CONSTRAINT cm_notification_rules_channel_check
        CHECK (channel IN ('telegram', 'browser', 'push', 'smart_block'));

-- ------------------------------------------------------------
-- 2. ts_tasks: colonna notification_channel
-- ------------------------------------------------------------
ALTER TABLE ts_tasks
    ADD COLUMN IF NOT EXISTS notification_channel text
        NOT NULL DEFAULT 'telegram'
        CHECK (notification_channel IN ('telegram', 'smart_block'));

COMMENT ON COLUMN ts_tasks.notification_channel IS
    'Canale di notifica scelto per i promemoria del task: telegram | smart_block.';

-- ------------------------------------------------------------
-- 3. cm_notification_queue: RLS per client Android (anon)
--
-- L'app Android usa la anon key senza sessione utente autenticata.
-- Permette di leggere i propri item smart_block filtrando per
-- device_token nel campo payload (JSONB). Il filtro sull'UUID
-- rende l'accesso praticamente impossibile da indovinare.
-- ------------------------------------------------------------

-- Lettura: anon può vedere item con channel='smart_block'
-- (il filtro device_token è applicato lato client nell'app)
DROP POLICY IF EXISTS "smart_block_anon_select" ON cm_notification_queue;
CREATE POLICY "smart_block_anon_select"
    ON cm_notification_queue
    FOR SELECT
    TO anon
    USING (channel = 'smart_block');

-- Aggiornamento status: anon può segnare 'sent' o 'failed'
-- solo su item smart_block
DROP POLICY IF EXISTS "smart_block_anon_update" ON cm_notification_queue;
CREATE POLICY "smart_block_anon_update"
    ON cm_notification_queue
    FOR UPDATE
    TO anon
    USING  (channel = 'smart_block')
    WITH CHECK (
        channel = 'smart_block'
        AND status IN ('sent', 'failed')
    );

-- ============================================================
-- Fine migration
-- ============================================================
