-- ============================================================
-- Aggiunge stato 'terminato' a ts_tasks
-- Migration: 20260227150000_tasks_terminato_status
--
-- Semantica:
--   terminato → task concluso definitivamente (nessuna occorrenza futura):
--               - task 'single'   → al primo completamento o fallimento
--               - task 'multiple' → quando viene completata/fallita l'ultima data
--
--   Differenza da 'completed':
--               'completed' rimane per workflow e recurring che esauriscono
--               il pattern; 'terminato' marca esplicitamente i task senza
--               più date programmate.
-- ============================================================

-- 1. Rimuovi il vecchio CHECK constraint (nome standard Postgres)
ALTER TABLE ts_tasks
    DROP CONSTRAINT IF EXISTS ts_tasks_status_check;

-- 2. Aggiunta del valore 'terminato' al tipo enum, se la colonna è un enum
--    (se è un VARCHAR con CHECK, il blocco sopra/sotto è sufficiente)
DO $$
BEGIN
    -- Tenta di aggiungere 'terminato' se status è un tipo enum
    IF EXISTS (
        SELECT 1 FROM pg_type t
        JOIN pg_enum e ON e.enumtypid = t.oid
        JOIN pg_attribute a ON a.atttypid = t.oid
        JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relname = 'ts_tasks'
          AND a.attname = 'status'
    ) THEN
        -- È un enum: aggiungi il valore se non esiste già
        IF NOT EXISTS (
            SELECT 1 FROM pg_enum e
            JOIN pg_type t ON t.oid = e.enumtypid
            JOIN pg_attribute a ON a.atttypid = t.oid
            JOIN pg_class c ON c.oid = a.attrelid
            WHERE c.relname = 'ts_tasks'
              AND a.attname = 'status'
              AND e.enumlabel = 'terminato'
        ) THEN
            ALTER TYPE task_status ADD VALUE IF NOT EXISTS 'terminato';
        END IF;
    ELSE
        -- È VARCHAR: ricrea il CHECK constraint includendo 'terminato'
        ALTER TABLE ts_tasks
            ADD CONSTRAINT ts_tasks_status_check
            CHECK (status IN ('active', 'completed', 'terminato', 'archived'));
    END IF;
END;
$$;

-- ============================================================
-- Verifica stati validi dopo la migration:
-- SELECT DISTINCT status FROM ts_tasks ORDER BY status;
-- ============================================================
