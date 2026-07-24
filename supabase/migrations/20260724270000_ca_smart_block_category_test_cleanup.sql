-- Pulizia delle transazioni fittizie create dai test del picker categorie (migration
-- 20260724200000 e i rinvii successivi): tutte le righe con descrizione "TEST — cancellami..."
-- per l'utente. Il DELETE su ca_transactions elimina a cascata anche le eventuali righe in
-- ca_transaction_categories (FK ON DELETE CASCADE).
delete from ca_transactions
where user_id = (select id from auth.users where email = 'garsal1971@gmail.com' limit 1)
  and description like 'TEST — cancellami%';

-- Pulizia delle notifiche Smart Block di test rimaste in coda (titoli "TEST — ...").
delete from cm_notification_queue
where user_id = (select id from auth.users where email = 'garsal1971@gmail.com' limit 1)
  and app = 'cost_analysis'
  and title like 'TEST —%';
