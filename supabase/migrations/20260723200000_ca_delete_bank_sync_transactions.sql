-- Elimina tutte le transazioni inserite direttamente dalla sincronizzazione bancaria
-- (import_source = 'bank_sync'), per ripulire i doppioni generati dal confronto data ESATTO
-- nella logica di merge (booking_date della banca spesso diverso di un giorno dalla data CSV,
-- quindi il match falliva e la transazione veniva inserita come nuova invece di agganciarsi a
-- quella CSV già presente).
--
-- NON tocca le transazioni importate da CSV, anche quelle che sono state agganciate
-- (bank_connection_id valorizzato) a una sincronizzazione: quelle mantengono
-- import_source = 'csv' e restano intatte con categoria/persona già assegnate. Cascata
-- automatica su ca_transaction_categories (ON DELETE CASCADE) per le sole righe eliminate.
DO $$
DECLARE v_user_id uuid;
BEGIN
  SELECT id INTO v_user_id FROM auth.users WHERE email = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user_id IS NULL THEN RETURN; END IF;

  DELETE FROM ca_transactions
  WHERE user_id = v_user_id
    AND import_source = 'bank_sync';
END $$;
