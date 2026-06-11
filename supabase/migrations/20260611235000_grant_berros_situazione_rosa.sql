-- Autorizza berros1974@gmail.com alla pagina situazione-rosa.html
-- (accesso ospite in sola lettura, gestito da cm_guest_access)
INSERT INTO cm_guest_access (email, page, note)
VALUES ('berros1974@gmail.com', 'situazione-rosa.html', 'Accesso in sola lettura a Situazione Rosa')
ON CONFLICT (email, page) DO NOTHING;
