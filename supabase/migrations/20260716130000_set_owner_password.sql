-- Imposta una password per l'account garsal1971@gmail.com, che finora
-- accedeva solo con Google OAuth (nessuna password in auth.users).
-- Serve per il login email+password nell'app Android Situazione Rosa,
-- così da non dipendere più dal flusso "magic link" via email.
--
-- Il valore reale della password NON è mai committato in chiaro nel repo:
-- questo file contiene solo un segnaposto, sostituito a deploy-time dal
-- workflow .github/workflows/deploy.yml a partire dal secret GitHub
-- OWNER_PASSWORD. Se il secret non è configurato, il deploy fallisce
-- volutamente invece di usare il segnaposto come password reale.
--
-- Non tocca nulla se una password è già presente (idempotente anche se
-- rieseguita, es. su un progetto Supabase ripristinato da zero).
update auth.users
set encrypted_password = crypt('__OWNER_PASSWORD_PLACEHOLDER__', gen_salt('bf')),
    updated_at = now()
where email = 'garsal1971@gmail.com'
  and encrypted_password is null;
