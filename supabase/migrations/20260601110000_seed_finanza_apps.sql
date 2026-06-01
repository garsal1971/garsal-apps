-- Seed Finanza, Casa Rosa and Contabilità apps in cm_apps
INSERT INTO cm_apps (title, description, score_query, color, active, html_file, riservato)
VALUES 
('Finanza', 'Gestione portafogli, titoli e patrimonio netto', 'SELECT COUNT(*)::int FROM transactions WHERE user_id = auth.uid()', '#4f46e5', true, 'finanza.html', true),
('Casa Rosa', 'Situazione cassa e pagamenti Casa Rosa', 'SELECT COUNT(*)::int FROM cntrs_transactions WHERE user_id = auth.uid()', '#db2777', true, 'casarosa.html', true),
('Contabilità', 'Gestione conto cointestato e report annuali', 'SELECT COUNT(*)::int FROM acct_transactions WHERE user_id = auth.uid()', '#0891b2', true, 'contabilita.html', true)
ON CONFLICT (title) DO UPDATE SET
  html_file = EXCLUDED.html_file,
  score_query = EXCLUDED.score_query,
  color = EXCLUDED.color,
  riservato = EXCLUDED.riservato;
