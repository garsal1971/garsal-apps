-- ============================================================
-- SOS — configurazione d'esempio: «Binge serale»
-- ============================================================
-- È l'esempio chiesto insieme all'app, e serve anche da modello per i SOS
-- successivi: un nome, che cosa si sta fronteggiando, quanto dura il primo
-- countdown, le risposte con i loro punti e le loro percentuali, e le frasi
-- che scorrono mentre il tempo passa.
--
-- Le due risposte che fissano la scala sono quelle chieste:
--   · Male     → −500 punti, +10 % di tempo al prossimo giro
--   · Bene     → +100 punti, tempo invariato
-- Le altre due stanno in mezzo e sotto, così la percentuale può anche
-- scendere: un SOS che si supera senza fatica non ha motivo di durare
-- sempre uguale.
--
-- Idempotente e intestato all'utente cercato per email: su un database dove
-- quell'utente non esiste (progetto dev) la migration non fa nulla e non
-- fallisce.

DO $$
DECLARE
  v_user uuid;
  v_sos  uuid;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE lower(email) = 'garsal1971@gmail.com';
  IF v_user IS NULL THEN
    RAISE NOTICE 'utente garsal1971@gmail.com non trovato: salto il SOS di esempio';
    RETURN;
  END IF;

  -- ── il SOS ────────────────────────────────────────────────────────────
  SELECT id INTO v_sos FROM sos_types WHERE user_id = v_user AND name = 'Binge serale';
  IF v_sos IS NULL THEN
    INSERT INTO sos_types (user_id, name, description, emoji, color,
                           base_seconds, current_seconds, min_seconds, max_seconds,
                           position, active)
    VALUES (v_user, 'Binge serale', 'Desiderio di cibo', '🍫', '#EE334E',
            600, 600, 300, 1800, 0, true)
    RETURNING id INTO v_sos;
  END IF;

  -- ── le risposte ───────────────────────────────────────────────────────
  -- Si riscrivono da capo: sono la configurazione, non uno storico, e
  -- sos_sessions.outcome_label conserva comunque com'era chiamata la
  -- risposta scelta al momento del giro.
  DELETE FROM sos_outcomes WHERE sos_type_id = v_sos;
  INSERT INTO sos_outcomes (user_id, sos_type_id, label, emoji, points, time_delta_pct, position)
  VALUES
    (v_user, v_sos, 'Male — ho ceduto',            '😞', -500,  10.0, 0),
    (v_user, v_sos, 'Così così — ho piluccato',    '😐',  -50,   5.0, 1),
    (v_user, v_sos, 'Bene — è passata',            '🙂',  100,   0.0, 2),
    (v_user, v_sos, 'Benissimo — non ci penso più','💪',  250, -10.0, 3);

  -- ── il testo che scorre durante il countdown ──────────────────────────
  DELETE FROM sos_messages WHERE sos_type_id = v_sos;
  INSERT INTO sos_messages (user_id, sos_type_id, text, position)
  VALUES
    (v_user, v_sos, 'La voglia non è un ordine: è un''onda. Sale, resta un po'', e poi scende da sola.', 0),
    (v_user, v_sos, 'Non stai rinunciando a niente. Stai solo aspettando.', 1),
    (v_user, v_sos, 'Fra dieci minuti sarai contento di aver premuto questo bottone.', 2),
    (v_user, v_sos, 'Bevi un bicchiere d''acqua. Respira lento. Il tempo sta già passando.', 3),
    (v_user, v_sos, 'La fame vera aspetta. Questa no: questa passa.', 4),
    (v_user, v_sos, 'Domani mattina questo momento non esisterà più. La scelta che fai adesso sì.', 5),
    (v_user, v_sos, 'Hai già resistito altre volte. Non è la prima e non sarà l''ultima.', 6),
    (v_user, v_sos, 'Non sei in guerra con te stesso. Stai solo prendendo tempo.', 7);

  RAISE NOTICE 'SOS «Binge serale» configurato (%).', v_sos;
END $$;
