-- ============================================================
-- Aggiunta campi a cm_notification_rules
-- Migration: 20260226100000_rules_add_entity_fields
--
-- Le app inseriscono direttamente in cm_notification_rules
-- tutti i dati necessari all'invio. Job 1 legge solo questa
-- tabella: nessuna query su ts_tasks, hb_habits, ecc.
--
-- Nuovi campi:
--   entity_title  — testo dell'entità (es. "Chiamare medico")
--   due_at        — scadenza dell'entità in UTC
--                   Job 1 calcola: fire_at = due_at - offset_minutes
--
-- Responsabilità delle app:
--   - INSERT della regola al salvataggio del task/habit
--   - UPDATE di due_at se la scadenza cambia
--   - DELETE della regola se il task/habit viene eliminato
--     o i reminder vengono disattivati
-- ============================================================

ALTER TABLE cm_notification_rules
    ADD COLUMN entity_title text        NOT NULL DEFAULT '',
    ADD COLUMN due_at       timestamptz NOT NULL DEFAULT now();

-- Rimuovi il DEFAULT temporaneo (le app devono sempre fornire i valori)
ALTER TABLE cm_notification_rules
    ALTER COLUMN entity_title DROP DEFAULT,
    ALTER COLUMN due_at       DROP DEFAULT;

COMMENT ON COLUMN cm_notification_rules.entity_title IS
    'Titolo leggibile dell''entità — es. "Chiamare medico". Inserito dall''app.';

COMMENT ON COLUMN cm_notification_rules.due_at IS
    'Scadenza dell''entità in UTC. Job 1 calcola fire_at = due_at - offset_minutes.';
