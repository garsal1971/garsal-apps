-- Seed dei 13 viaggi confermati (periodi fuori Bologna). I 3 periodi con data di rientro
-- incerta (Palermo rientro ~2024-05-02, Palermo dal 2025-05-20, Palermo dal 2026-04-18) sono
-- intenzionalmente esclusi: vanno verificati a mano prima di poterli aggiungere.
DO $$
DECLARE v_user_id uuid;
BEGIN
  SELECT id INTO v_user_id FROM auth.users WHERE email = 'garsal1971@gmail.com' LIMIT 1;
  IF v_user_id IS NULL THEN RETURN; END IF;
  IF EXISTS (SELECT 1 FROM ca_trips WHERE user_id = v_user_id) THEN RETURN; END IF;

  INSERT INTO ca_trips (user_id, name, start_date, end_date) VALUES
    (v_user_id, 'Lanzarote',          '2024-05-20', '2024-05-27'),
    (v_user_id, 'Palermo',            '2024-07-08', '2024-07-22'),
    (v_user_id, 'Corfù',              '2024-08-13', '2024-08-27'),
    (v_user_id, 'Praga',              '2024-12-26', '2024-12-30'),
    (v_user_id, 'Palma di Maiorca',   '2025-04-05', '2025-04-07'),
    (v_user_id, 'Palermo',            '2025-07-24', '2025-08-05'),
    (v_user_id, 'Salonicco',          '2025-08-09', '2025-08-21'),
    (v_user_id, 'Lanzarote',          '2025-12-21', '2025-12-28'),
    (v_user_id, 'Palermo',            '2026-02-05', '2026-02-09'),
    (v_user_id, 'Palermo',            '2026-03-01', '2026-03-23'),
    (v_user_id, 'Palermo',            '2026-04-09', '2026-04-14'),
    (v_user_id, 'Palermo',            '2026-06-17', '2026-06-22'),
    (v_user_id, 'Zara/Zadar',         '2026-07-26', '2026-07-30');
END $$;
