-- Prepara cm_notification_rules/cm_notification_queue per le notifiche Smart Block della
-- nuova edge function schedulata revolut-auto-categorize.
--
-- 1) Allarga il vincolo cm_notification_rules_app_check per ammettere 'cost_analysis'
--    (stesso pattern di 20260705140000_notification_rules_allow_ta_firi_app.sql).
ALTER TABLE cm_notification_rules DROP CONSTRAINT IF EXISTS cm_notification_rules_app_check;
ALTER TABLE cm_notification_rules ADD CONSTRAINT cm_notification_rules_app_check
  CHECK (app = ANY (ARRAY['tasks'::text, 'habits'::text, 'events'::text, 'weight'::text,
                           'quick'::text, 'ta_firi'::text, 'cost_analysis'::text]));

-- 2) Riga "ancora" fissa: esiste solo per soddisfare la FK NOT NULL di
--    cm_notification_queue.rule_id. Resta SEMPRE enabled=false: revolut-auto-categorize scrive
--    direttamente in cm_notification_queue e non deve mai passare da fill-notification-queue,
--    che per le regole smart_block con enabled=true cancella ad ogni run TUTTE le righe pending
--    (non solo quelle future) e le rigenera solo se reminder_presets.due_at è nel futuro — una
--    riga enabled=true finirebbe cancellata e mai ripristinata dal nostro inserimento diretto.
-- entity_id è di tipo uuid in produzione (non text come nello schema originale della tabella —
-- un altro caso di drift schema applicato solo su Supabase, come entity_title/notification_spec):
-- non può contenere una stringa libera come 'revolut-sync', va generato un uuid vero.
-- entity_type ha inoltre un vincolo CHECK (chk_entity_type, anch'esso non presente in nessuna
-- migration locale) che ammette solo un set fisso di valori: 'task' è l'unico che risulta già
-- usato con successo anche da un'app diversa da 'tasks' (ta-firi.html, app='ta_firi'), quindi è
-- il valore più sicuro da riusare qui senza conoscere l'elenco completo ammesso.
DO $$
DECLARE v_user_id uuid;
BEGIN
  SELECT id INTO v_user_id FROM auth.users WHERE email = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user_id IS NULL THEN RETURN; END IF;
  IF EXISTS (
    SELECT 1 FROM cm_notification_rules
    WHERE user_id = v_user_id AND app = 'cost_analysis' AND channel = 'smart_block'
  ) THEN RETURN; END IF;

  INSERT INTO cm_notification_rules
    (user_id, app, entity_id, entity_type, entity_title, reminder_presets, notification_spec, channel, enabled)
  VALUES
    (v_user_id, 'cost_analysis', gen_random_uuid(), 'task', 'Revolut — categorizzazione automatica',
     '{}'::jsonb, '{}'::jsonb, 'smart_block', false);
END $$;
