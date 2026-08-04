// Supabase Edge Function: rilegge i conti di una sessione Enable Banking già ottenuta.
//
// Serve quando il consenso è andato a buon fine ma la lista dei conti è tornata vuota (è
// successo con UniCredit): la sessione resta valida, quindi non c'è motivo di rifare login e
// SCA per riprovare. Se i conti compaiono, la connessione viene completata con il suo
// account_id e i conti in più diventano righe nuove.
//
// Restituisce anche la risposta grezza della sessione (troncata): senza vedere cosa manda
// davvero la banca non si può capire perché la lista sia vuota, e questa è l'unica finestra
// che abbiamo su quella risposta.
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY.
// v1 — 2026-08-04

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
  return crypto.subtle.importKey('pkcs8', binaryDer.buffer, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
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

// Stessa logica del callback: i conti arrivano in forme diverse a seconda dell'ASPSP.
// uid deve essere una stringa — account_id può essere un oggetto { iban }, e prenderlo per
// buono significherebbe salvare "[object Object]" come identificativo del conto.
function extractAccounts(data: any): { uid: string; iban: string | null }[] {
  const raw: unknown[] = Array.isArray(data?.accounts)
    ? data.accounts
    : Array.isArray(data?.account_ids)
      ? data.account_ids
      : [];
  return raw
    .map((acc: any) => {
      if (typeof acc === 'string') return { uid: acc, iban: null };
      const candidates = [acc?.uid, acc?.account_id, acc?.id, acc?.resource_id, acc?.resourceId, acc?.identification_hash];
      const uid = candidates.find((v) => typeof v === 'string' && v.length > 0) as string | undefined;
      const iban = acc?.account_id?.iban || acc?.identification?.iban || acc?.iban || null;
      return uid ? { uid, iban: iban ? String(iban) : null } : null;
    })
    .filter((x): x is { uid: string; iban: string | null } => x !== null);
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: corsHeaders });
  if (req.method !== 'POST') return new Response('Method Not Allowed', { status: 405, headers: corsHeaders });

  const json = (payload: unknown, status = 200) =>
    new Response(JSON.stringify(payload), { status, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });

  const appId = Deno.env.get('ENABLE_BANKING_APP_ID');
  const privateKeyPem = Deno.env.get('ENABLE_BANKING_PRIVATE_KEY');
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!appId || !privateKeyPem || !supabaseUrl || !supabaseServiceRoleKey) {
    return json({ error: { message: 'Configurazione mancante (secrets Enable Banking o Supabase).' } }, 500);
  }

  const userId = decodeSupabaseJwtSub((req.headers.get('authorization') || '').replace(/^Bearer\s+/i, ''));
  if (!userId) return json({ error: { message: 'Utente non autenticato.' } }, 401);

  let body: { bankConnectionId?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: { message: 'Body JSON non valido.' } }, 400);
  }
  const bankConnectionId = (body.bankConnectionId || '').trim();
  if (!bankConnectionId) return json({ error: { message: 'Serve "bankConnectionId".' } }, 400);

  const supabase = createClient(supabaseUrl, supabaseServiceRoleKey);
  const { data: connection } = await supabase
    .from('cm_bank_connections')
    .select('*')
    .eq('id', bankConnectionId)
    .eq('user_id', userId)
    .maybeSingle();
  if (!connection) return json({ error: { message: 'Conto collegato non trovato.' } }, 404);
  if (!connection.consent_id) {
    return json({ error: { message: 'Questo collegamento non ha una sessione salvata: va rifatto il consenso in banca.' } }, 400);
  }

  try {
    const jwt = await createEnableBankingJWT(appId, privateKeyPem);
    const res = await fetch(`${ENABLE_BANKING_API_BASE}/sessions/${connection.consent_id}`, {
      headers: { Authorization: 'Bearer ' + jwt },
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      return json({ error: { message: `Enable Banking ha risposto ${res.status}: ${data?.message || 'errore'}` } }, 502);
    }

    const accounts = extractAccounts(data);
    // La risposta grezza è la ragione d'essere di questa function: va restituita anche (anzi,
    // soprattutto) quando i conti non ci sono.
    const rawPreview = JSON.stringify(data).slice(0, 4000);

    if (!accounts.length) {
      await supabase.from('cm_sync_log').insert({
        user_id: userId,
        bank_connection_id: connection.id,
        finished_at: new Date().toISOString(),
        status: 'no_accounts',
        imported_count: 0,
        error_message: `Rilettura sessione: nessun conto. Risposta: ${rawPreview.slice(0, 900)}`,
      });
      return json({ accounts: [], updated: 0, created: 0, sessionStatus: data?.status || null, raw: rawPreview });
    }

    // Il primo conto completa la connessione esistente; gli altri diventano righe nuove, ma
    // solo se non ci sono già (la rilettura si può ripetere quante volte si vuole).
    const { data: siblings } = await supabase
      .from('cm_bank_connections')
      .select('id, account_id')
      .eq('user_id', userId)
      .eq('consent_id', connection.consent_id);
    const known = new Set((siblings || []).map((s) => s.account_id).filter(Boolean));

    let updated = 0;
    let created = 0;
    for (const acc of accounts) {
      if (known.has(acc.uid)) continue;
      if (!connection.account_id && updated === 0) {
        await supabase
          .from('cm_bank_connections')
          .update({ account_id: acc.uid, display_name: connection.display_name || acc.iban || null })
          .eq('id', connection.id);
        updated++;
      } else {
        await supabase.from('cm_bank_connections').insert({
          user_id: userId,
          provider: connection.provider,
          aspsp_name: connection.aspsp_name,
          display_name: acc.iban || null,
          owner_person_id: connection.owner_person_id,
          account_id: acc.uid,
          consent_id: connection.consent_id,
          consent_expires_at: connection.consent_expires_at,
          status: 'active',
          module: connection.module,
        });
        created++;
      }
      known.add(acc.uid);
    }

    return json({
      accounts: accounts.map((a) => ({ uid: a.uid, iban: a.iban })),
      updated,
      created,
      sessionStatus: data?.status || null,
      raw: rawPreview,
    });
  } catch (e) {
    return json({ error: { message: (e as Error).message } }, 502);
  }
});
