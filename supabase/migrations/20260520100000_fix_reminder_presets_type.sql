-- Converte reminder_presets da text[] a jsonb.
-- La colonna era text[] ma il codice (tasks.html e SupabaseApi.kt) la tratta
-- come oggetto JSON { reminders: [...], due_at: '...' }.
-- Questo causava l'errore 42846 "cannot cast type text[] to jsonb"
-- nella funzione task_complete quando aggiornava il campo via || jsonb_build_object(...).

ALTER TABLE cm_notification_rules
  ALTER COLUMN reminder_presets TYPE jsonb
  USING CASE
    WHEN reminder_presets IS NULL THEN NULL
    WHEN array_length(reminder_presets, 1) IS NULL THEN NULL
    WHEN array_length(reminder_presets, 1) = 1 THEN (reminder_presets[1])::jsonb
    ELSE to_jsonb(reminder_presets)
  END;
