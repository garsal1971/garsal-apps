-- Sesto rinvio della notifica di test (payload con parent_id, per il picker v1.3.7 con
-- categorie principali chiuse di default). Riusa le transazioni fittizie se ancora senza
-- categoria, stessa logica delle precedenti.
do $$
declare
  v_user_id      uuid;
  v_rule_id      uuid;
  v_tx1          uuid;
  v_tx2          uuid;
  v_device_token text;
  v_categories   jsonb;
  v_transactions jsonb;
begin
  select id into v_user_id from auth.users where email = 'garsal1971@gmail.com' limit 1;
  if v_user_id is null then return; end if;

  select id into v_rule_id from cm_notification_rules
    where user_id = v_user_id and app = 'cost_analysis' and channel = 'smart_block';
  if v_rule_id is null then return; end if;

  select t.id into v_tx1 from ca_transactions t
    where t.user_id = v_user_id and t.description = 'TEST — cancellami (picker categoria)'
      and not exists (select 1 from ca_transaction_categories c where c.transaction_id = t.id)
    order by t.created_at desc limit 1;
  select t.id into v_tx2 from ca_transactions t
    where t.user_id = v_user_id and t.description = 'TEST — cancellami 2 (picker categoria)'
      and not exists (select 1 from ca_transaction_categories c where c.transaction_id = t.id)
    order by t.created_at desc limit 1;

  if v_tx1 is null then
    insert into ca_transactions (user_id, date, amount, currency, description, person_source, import_source)
      values (v_user_id, current_date, -9.99, 'EUR', 'TEST — cancellami (picker categoria)', 'unassigned', 'csv')
      returning id into v_tx1;
  end if;
  if v_tx2 is null then
    insert into ca_transactions (user_id, date, amount, currency, description, person_source, import_source)
      values (v_user_id, current_date - 1, -25.00, 'EUR', 'TEST — cancellami 2 (picker categoria)', 'unassigned', 'csv')
      returning id into v_tx2;
  end if;

  select smart_block_device_token into v_device_token
    from cm_user_notification_settings where user_id = v_user_id;

  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'name', name, 'icon', icon, 'parent_id', parent_id) order by name), '[]'::jsonb)
    into v_categories
    from ca_categories where user_id = v_user_id;

  v_transactions := jsonb_build_array(
    jsonb_build_object('id', v_tx1, 'description', 'TEST — cancellami (picker categoria)',
                        'amount', -9.99, 'currency', 'EUR', 'date', current_date::text),
    jsonb_build_object('id', v_tx2, 'description', 'TEST — cancellami 2 (picker categoria)',
                        'amount', -25.00, 'currency', 'EUR', 'date', (current_date - 1)::text)
  );

  insert into cm_notification_queue (rule_id, user_id, app, entity_id, title, body, channel, fire_at, status, metadata)
  values (
    v_rule_id, v_user_id, 'cost_analysis', v_rule_id,
    'TEST — categorizza 2 transazioni finte (rinvio 6)',
    'Tocca una categoria per ciascuna transazione di test.',
    'smart_block', now(), 'pending',
    jsonb_strip_nulls(jsonb_build_object(
      'device_token', v_device_token,
      'cost_analysis', jsonb_build_object('transactions', v_transactions, 'categories', v_categories)
    ))
  );
end $$;
