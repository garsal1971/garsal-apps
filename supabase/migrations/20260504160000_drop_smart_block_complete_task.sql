-- Rimozione smart_block_complete_task (funzione ridondante)
DROP FUNCTION IF EXISTS public.smart_block_complete_task(text, uuid, date);
