-- Notifica di test per il picker categorie Analisi Costi, con 3 transazioni finte per poter
-- verificare i nuovi bottoni ◀ ▶ di navigazione (avanti/indietro senza categorizzare).
do $$
declare
  v_user_id      uuid;
  v_rule_id      uuid;
  v_tx1          uuid;
  v_tx2          uuid;
  v_tx3          uuid;
  v_device_token text;
  v_categories   jsonb;
  v_transactions jsonb;
begin
  select id into v_user_id from auth.users where email = 'garsal1971@gmail.com' limit 1;
  if v_user_id is null then return; end if;

  select id into v_rule_id from cm_notification_rules
    where user_id = v_user_id and app = 'cost_analysis' and channel = 'smart_block';
  if v_rule_id is null then return; end if;

  insert into ca_transactions (user_id, date, amount, currency, description, person_source, import_source)
    values (v_user_id, current_date, -9.99, 'EUR', 'TEST — cancellami (nav 1)', 'unassigned', 'csv')
    returning id into v_tx1;
  insert into ca_transactions (user_id, date, amount, currency, description, person_source, import_source)
    values (v_user_id, current_date - 1, -25.00, 'EUR', 'TEST — cancellami (nav 2)', 'unassigned', 'csv')
    returning id into v_tx2;
  insert into ca_transactions (user_id, date, amount, currency, description, person_source, import_source)
    values (v_user_id, current_date - 2, -3.50, 'EUR', 'TEST — cancellami (nav 3)', 'unassigned', 'csv')
    returning id into v_tx3;

  select smart_block_device_token into v_device_token
    from cm_user_notification_settings where user_id = v_user_id;

  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'name', name, 'icon', icon, 'parent_id', parent_id) order by name), '[]'::jsonb)
    into v_categories
    from ca_categories where user_id = v_user_id and visible_in_app = true;

  v_transactions := jsonb_build_array(
    jsonb_build_object('id', v_tx1, 'description', 'TEST — cancellami (nav 1)',
                        'amount', -9.99, 'currency', 'EUR', 'date', current_date::text),
    jsonb_build_object('id', v_tx2, 'description', 'TEST — cancellami (nav 2)',
                        'amount', -25.00, 'currency', 'EUR', 'date', (current_date - 1)::text),
    jsonb_build_object('id', v_tx3, 'description', 'TEST — cancellami (nav 3)',
                        'amount', -3.50, 'currency', 'EUR', 'date', (current_date - 2)::text)
  );

  insert into cm_notification_queue (rule_id, user_id, app, entity_id, title, body, channel, fire_at, status, metadata)
  values (
    v_rule_id, v_user_id, 'cost_analysis', v_rule_id,
    'TEST — categorizza 3 transazioni finte (nav ◀ ▶)',
    'Tocca una categoria per ciascuna transazione di test, oppure prova i bottoni ◀ ▶.',
    'smart_block', now(), 'pending',
    jsonb_strip_nulls(jsonb_build_object(
      'device_token', v_device_token,
      'cost_analysis', jsonb_build_object('transactions', v_transactions, 'categories', v_categories)
    ))
  );
end $$;
