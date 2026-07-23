-- Terza pulizia: stessa logica delle due precedenti
-- (20260723200000_ca_delete_bank_sync_transactions.sql,
-- 20260723213000_ca_delete_bank_sync_transactions_2.sql). Cancella solo le righe inserite
-- direttamente dal sync (import_source = 'bank_sync'), non tocca le transazioni CSV — questa
-- volta il deploy della edge function con il nuovo matching viene verificato prima di far
-- ripartire l'import, per escludere che i doppioni fossero dovuti a un sync lanciato contro la
-- versione precedente della funzione, non ancora aggiornata.
DO $$
DECLARE v_user_id uuid;
BEGIN
  SELECT id INTO v_user_id FROM auth.users WHERE email = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user_id IS NULL THEN RETURN; END IF;

  DELETE FROM ca_transactions
  WHERE user_id = v_user_id
    AND import_source = 'bank_sync';
END $$;
