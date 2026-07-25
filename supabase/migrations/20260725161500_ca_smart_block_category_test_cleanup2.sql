-- Pulizia delle transazioni fittizie e notifiche smart_block create dai test del picker
-- categorie con navigazione ◀ ▶ (migration 20260725150000 e 20260725160000). Il DELETE su
-- ca_transactions elimina a cascata anche le eventuali righe in ca_transaction_categories
-- (FK ON DELETE CASCADE).
delete from ca_transactions
where user_id = (select id from auth.users where email = 'garsal1971@gmail.com' limit 1)
  and description like 'TEST — cancellami%';

delete from cm_notification_queue
where user_id = (select id from auth.users where email = 'garsal1971@gmail.com' limit 1)
  and app = 'cost_analysis'
  and title like 'TEST —%';
