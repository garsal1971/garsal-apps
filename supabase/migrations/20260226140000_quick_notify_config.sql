-- ============================================================
-- Tabella di configurazione per notifiche tipo "quanto_prima"
-- Migration: 20260226140000_quick_notify_config
--
-- cm_quick_notify_config — una riga per utente.
-- Definisce i valori di default (anticipi e canali) da applicare
-- automaticamente quando si crea una regola con
-- notification_spec.type = 'quanto_prima'.
--
-- Colonne:
--   offsets_minutes  int[]    Anticipi in minuti (es. [15, 60, 1440])
--   active_channels  text[]   Canali abilitati (telegram | browser | push)
-- ============================================================

CREATE TABLE IF NOT EXISTS cm_quick_notify_config (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,

    -- Anticipi predefiniti in minuti prima della scadenza
    -- es. [15, 60, 1440]  →  15 min, 1 ora, 1 giorno
    offsets_minutes  int[]       NOT NULL DEFAULT '{60}'::int[],

    -- Canali attivi per questo tipo di notifica
    active_channels  text[]      NOT NULL DEFAULT '{browser}'::text[],

    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    -- Una sola configurazione per utente
    CONSTRAINT uq_quick_notify_config_user UNIQUE (user_id),

    -- Valori ammessi per i canali
    CONSTRAINT chk_quick_notify_channels CHECK (
        active_channels <@ ARRAY['telegram','browser','push']::text[]
    ),

    -- Almeno un anticipo definito
    CONSTRAINT chk_quick_notify_offsets CHECK (
        array_length(offsets_minutes, 1) > 0
    )
);

-- Trigger: aggiorna updated_at automaticamente
CREATE OR REPLACE FUNCTION cm_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_quick_notify_config_updated_at
    BEFORE UPDATE ON cm_quick_notify_config
    FOR EACH ROW EXECUTE FUNCTION cm_set_updated_at();

-- RLS: ogni utente vede e modifica solo la propria riga
ALTER TABLE cm_quick_notify_config ENABLE ROW LEVEL SECURITY;

CREATE POLICY "own_quick_notify_config_select"
    ON cm_quick_notify_config FOR SELECT
    USING (user_id = auth.uid());

CREATE POLICY "own_quick_notify_config_insert"
    ON cm_quick_notify_config FOR INSERT
    WITH CHECK (user_id = auth.uid());

CREATE POLICY "own_quick_notify_config_update"
    ON cm_quick_notify_config FOR UPDATE
    USING (user_id = auth.uid());

CREATE POLICY "own_quick_notify_config_delete"
    ON cm_quick_notify_config FOR DELETE
    USING (user_id = auth.uid());

-- Indice per lookup per utente (già coperto dall'UNIQUE, ma esplicito)
CREATE INDEX IF NOT EXISTS idx_quick_notify_config_user
    ON cm_quick_notify_config (user_id);

COMMENT ON TABLE cm_quick_notify_config IS
    'Configurazione predefinita per regole di notifica tipo "quanto_prima": anticipi e canali per utente.';
