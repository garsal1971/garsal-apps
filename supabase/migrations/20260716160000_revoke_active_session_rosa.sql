-- Forza il logout di r.bertuglia@yahoo.it lato server, senza toccare
-- l'account né la grant in cm_guest_access: cancella le sue sessioni
-- attive in auth.sessions, che tramite cascade invalida anche i
-- refresh token collegati (auth.refresh_tokens.session_id -> auth.sessions.id).
--
-- Effetto: il refresh_token salvato nell'app/browser smette di funzionare
-- e al prossimo utilizzo dovrà rifare login. La grant resta intatta,
-- quindi può rientrare normalmente con le sue credenziali.
--
-- Nota: l'access_token già emesso è un JWT stateless e resta valido fino
-- alla sua scadenza naturale (di solito ~1 ora) anche dopo questa delete.
delete from auth.sessions
where user_id = (select id from auth.users where lower(email) = 'r.bertuglia@yahoo.it');
