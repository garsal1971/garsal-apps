// Supabase Edge Function: scarica le transazioni nuove per un conto collegato via Enable
// Banking (GET /accounts/{id}/transactions), le deduplica per external_id, applica la
// categorizzazione MCC (deterministica — merchant appreso/regole/AI restano al client, che
// le applica in automatico ad ogni caricamento sulle transazioni ancora senza categoria) e le
// inserisce con spender_person_id preso dal proprietario del conto.
//
// NOTA: i nomi esatti dei campi nella risposta di /transactions non sono stati verificati con
// una chiamata reale (non testabile da questo ambiente) — l'estrazione prova più varianti di
// nome campo comuni allo standard Berlin Group NextGenPSD2 su cui si basa Enable Banking, e la
// riga CSV/JSON originale viene comunque salvata per intero in ca_transactions.raw così nulla
// va perso anche se un campo non viene riconosciuto correttamente al primo giro.
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY.
// v1 — 2026-07-18

import { createClient } from 'npm:@supabase/supabase-js@2';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const ENABLE_BANKING_API_BASE = 'https://api.enablebanking.com';

function base64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : new Uint8Array(input);
  let str = '';
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN (RSA )?PRIVATE KEY-----/, '')
    .replace(/-----END (RSA )?PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');
  const binaryDer = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    'pkcs8',
    binaryDer.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
}

async function createEnableBankingJWT(appId: string, privateKeyPem: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { typ: 'JWT', alg: 'RS256', kid: appId };
  const jwtBody = { iss: 'enablebanking.com', aud: 'api.enablebanking.com', iat: now, exp: now + 3600 };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(jwtBody))}`;
  const key = await importPrivateKey(privateKeyPem);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput));
  return `${signingInput}.${base64url(signature)}`;
}

function decodeSupabaseJwtSub(token: string): string | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.sub || null;
  } catch {
    return null;
  }
}

// Estrae un campo da più varianti di nome plausibili (snake_case / camelCase / Berlin Group).
function pick(obj: any, ...keys: string[]): unknown {
  for (const k of keys) {
    if (obj && obj[k] !== undefined && obj[k] !== null) return obj[k];
  }
  return null;
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== 'POST') {
    return new Response('Method Not Allowed', { status: 405, headers: corsHeaders });
  }

  const appId = Deno.env.get('ENABLE_BANKING_APP_ID');
  const privateKeyPem = Deno.env.get('ENABLE_BANKING_PRIVATE_KEY');
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!appId || !privateKeyPem || !supabaseUrl || !supabaseServiceRoleKey) {
    return new Response(
      JSON.stringify({ error: { message: 'Configurazione mancante (secrets Enable Banking o Supabase).' } }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const authHeader = req.headers.get('authorization') || '';
  const userId = decodeSupabaseJwtSub(authHeader.replace(/^Bearer\s+/i, ''));
  if (!userId) {
    return new Response(
      JSON.stringify({ error: { message: 'Utente non autenticato.' } }),
      { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  let body: { bankConnectionId?: string };
  try {
    body = await req.json();
  } catch {
    return new Response(
      JSON.stringify({ error: { message: 'Body JSON non valido.' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
  const bankConnectionId = (body.bankConnectionId || '').trim();
  if (!bankConnectionId) {
    return new Response(
      JSON.stringify({ error: { message: 'Serve "bankConnectionId".' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const supabase = createClient(supabaseUrl, supabaseServiceRoleKey);

  const { data: connection, error: connError } = await supabase
    .from('ca_bank_connections')
    .select('*')
    .eq('id', bankConnectionId)
    .eq('user_id', userId)
    .maybeSingle();
  if (connError || !connection) {
    return new Response(
      JSON.stringify({ error: { message: 'Conto collegato non trovato.' } }),
      { status: 404, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
  if (!connection.account_id) {
    return new Response(
      JSON.stringify({ error: { message: 'Questo conto non ha ancora un account_id valido (registrato manualmente, non collegato via OAuth).' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const { data: syncLog } = await supabase
    .from('ca_sync_log')
    .insert({ user_id: userId, bank_connection_id: bankConnectionId, status: 'running' })
    .select()
    .single();

  try {
    const jwt = await createEnableBankingJWT(appId, privateKeyPem);
    const txRes = await fetch(`${ENABLE_BANKING_API_BASE}/accounts/${connection.account_id}/transactions`, {
      headers: { Authorization: 'Bearer ' + jwt },
    });
    const txData = await txRes.json();
    if (!txRes.ok) {
      throw new Error('Errore Enable Banking: ' + (txData?.message || txRes.status));
    }

    const rawTransactions: unknown[] = Array.isArray(txData.transactions) ? txData.transactions : [];

    const { data: mccMap } = await supabase
      .from('ca_mcc_category_map')
      .select('mcc, category_id')
      .eq('user_id', userId);

    const rows = rawTransactions.map((tx: any) => {
      const amountRaw = pick(tx, 'transaction_amount', 'transactionAmount') as any;
      const amount = parseFloat(pick(amountRaw, 'amount') as string) || 0;
      const currency = (pick(amountRaw, 'currency') as string) || null;
      const description =
        (pick(tx, 'remittance_information_unstructured', 'remittanceInformationUnstructured') as string) ||
        (pick(tx, 'creditor_name', 'creditorName') as string) ||
        (pick(tx, 'debtor_name', 'debtorName') as string) ||
        '';
      const date = (pick(tx, 'booking_date', 'bookingDate', 'value_date', 'valueDate') as string) || null;
      const externalId = (pick(tx, 'entry_reference', 'entryReference', 'transaction_id', 'transactionId') as string) || null;
      const mcc = (pick(tx, 'merchant_category_code', 'merchantCategoryCode', 'mcc') as string) || null;
      return { date, amount, currency, description, externalId, mcc, raw: tx };
    }).filter((t) => t.date && t.externalId);

    if (!rows.length) {
      await supabase.from('ca_sync_log').update({ finished_at: new Date().toISOString(), status: 'success', imported_count: 0 }).eq('id', syncLog?.id);
      return new Response(JSON.stringify({ imported: 0 }), { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    }

    const insertRows = rows.map((t) => ({
      user_id: userId,
      date: t.date,
      amount: t.amount,
      currency: t.currency,
      description: t.description,
      type: 'bank_sync',
      spender_person_id: connection.owner_person_id,
      person_source: 'unassigned',
      bank_connection_id: bankConnectionId,
      external_id: t.externalId,
      mcc: t.mcc,
      import_source: 'bank_sync',
      raw: t.raw,
    }));

    const { data: inserted, error: insertError } = await supabase
      .from('ca_transactions')
      .upsert(insertRows, { onConflict: 'bank_connection_id,external_id', ignoreDuplicates: true })
      .select('id, mcc');
    if (insertError) throw new Error(insertError.message);

    // Categorizzazione MCC (deterministica) per le sole transazioni appena inserite
    const categoryRows: { transaction_id: string; category_id: string; source: string }[] = [];
    for (const t of inserted || []) {
      const match = (mccMap || []).find((m) => m.mcc === t.mcc);
      if (match) categoryRows.push({ transaction_id: t.id, category_id: match.category_id, source: 'mcc' });
    }
    if (categoryRows.length) {
      await supabase.from('ca_transaction_categories').insert(categoryRows);
    }

    await supabase.from('ca_sync_log').update({
      finished_at: new Date().toISOString(),
      status: 'success',
      imported_count: inserted?.length || 0,
    }).eq('id', syncLog?.id);

    return new Response(JSON.stringify({ imported: inserted?.length || 0 }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e) {
    await supabase.from('ca_sync_log').update({
      finished_at: new Date().toISOString(),
      status: 'error',
      error_message: (e as Error).message,
    }).eq('id', syncLog?.id);
    return new Response(
      JSON.stringify({ error: { message: (e as Error).message } }),
      { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
