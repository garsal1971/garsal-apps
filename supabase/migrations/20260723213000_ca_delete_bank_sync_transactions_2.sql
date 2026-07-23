-- Seconda pulizia: la modifica al matching CSV/sync (descrizione+importo, poi data con
-- tolleranza) non ha eliminato del tutto i doppioni — nuove transazioni con
-- import_source = 'bank_sync' sono state comunque inserite invece di agganciarsi alle righe
-- CSV già presenti. Stessa identica logica della pulizia precedente
-- (20260723200000_ca_delete_bank_sync_transactions.sql): cancella solo le righe inserite
-- direttamente dal sync, non tocca le transazioni CSV (anche quelle agganciate a una
-- sincronizzazione via merge, che restano import_source = 'csv').
DO $$
DECLARE v_user_id uuid;
BEGIN
  SELECT id INTO v_user_id FROM auth.users WHERE email = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user_id IS NULL THEN RETURN; END IF;

  DELETE FROM ca_transactions
  WHERE user_id = v_user_id
    AND import_source = 'bank_sync';
END $$;
