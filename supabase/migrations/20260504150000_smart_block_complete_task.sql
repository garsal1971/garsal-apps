-- ============================================================
-- Smart Block: RPC completamento task per client Android (anon)
-- Migration: 20260504150000_smart_block_complete_task
--
-- L'app Android usa la anon key senza sessione utente.
-- task_complete è GRANTata solo a authenticated e service_role.
-- Questa funzione wrapper verifica il device token e chiama
-- task_complete internamente (SECURITY DEFINER).
-- ============================================================

CREATE OR REPLACE FUNCTION public.smart_block_complete_task(
    p_device_token text,
    p_task_id      uuid,
    p_today        date
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_token_ok boolean;
BEGIN
    SELECT EXISTS(
        SELECT 1 FROM cm_smart_block_devices
         WHERE device_token = p_device_token
        UNION ALL
        SELECT 1 FROM cm_user_notification_settings
         WHERE smart_block_device_token = p_device_token
    ) INTO v_token_ok;

    IF NOT v_token_ok THEN
        RETURN jsonb_build_object('ok', false, 'error', 'device token non riconosciuto');
    END IF;

    RETURN task_complete(p_task_id, p_today);
END;
$$;

GRANT EXECUTE ON FUNCTION public.smart_block_complete_task(text, uuid, date) TO anon;
