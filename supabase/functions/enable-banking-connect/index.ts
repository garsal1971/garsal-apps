// Supabase Edge Function: avvia il collegamento di un conto via Enable Banking (PSD2,
// restricted mode). Firma un JWT con la chiave privata dell'applicazione, chiama
// POST /auth su Enable Banking e restituisce l'URL a cui reindirizzare l'utente per il
// consenso sulla propria banca (login + autorizzazione).
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY (PEM),
// SUPABASE_SERVICE_ROLE_KEY (per decodificare in sicurezza l'utente chiamante).
//
// Il callback dopo il consenso arriva su enable-banking-callback, che crea davvero la riga
// in ca_bank_connections — questa function non scrive nel database.
//
// v1 — 2026-07-18

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
  // Nota: Web Crypto accetta solo PKCS8 ("PRIVATE KEY"). Se la chiave scaricata da Enable
  // Banking è in formato PKCS1 ("RSA PRIVATE KEY"), l'import fallisce qui — va convertita con:
  // openssl pkcs8 -topk8 -inform PEM -outform PEM -in key.pem -out key_pkcs8.pem -nocrypt
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
  const body = { iss: 'enablebanking.com', aud: 'api.enablebanking.com', iat: now, exp: now + 3600 };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(body))}`;
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

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== 'POST') {
    return new Response('Method Not Allowed', { status: 405, headers: corsHeaders });
  }

  const appId = Deno.env.get('ENABLE_BANKING_APP_ID');
  const privateKeyPem = Deno.env.get('ENABLE_BANKING_PRIVATE_KEY');
  if (!appId || !privateKeyPem) {
    return new Response(
      JSON.stringify({ error: { message: 'ENABLE_BANKING_APP_ID / ENABLE_BANKING_PRIVATE_KEY non configurate nei Supabase Secrets.' } }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const authHeader = req.headers.get('authorization') || '';
  const userToken = authHeader.replace(/^Bearer\s+/i, '');
  const userId = decodeSupabaseJwtSub(userToken);
  if (!userId) {
    return new Response(
      JSON.stringify({ error: { message: 'Utente non autenticato.' } }),
      { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  let body: { aspspName?: string; country?: string; ownerPersonId?: string; displayName?: string };
  try {
    body = await req.json();
  } catch {
    return new Response(
      JSON.stringify({ error: { message: 'Body JSON non valido.' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const aspspName = (body.aspspName || '').trim();
  const country = (body.country || '').trim().toUpperCase();
  const ownerPersonId = (body.ownerPersonId || '').trim();
  if (!aspspName || !country || !ownerPersonId) {
    return new Response(
      JSON.stringify({ error: { message: 'Servono "aspspName", "country" e "ownerPersonId".' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  // Correlazione col callback: dati necessari per creare la riga ca_bank_connections dopo il
  // consenso, dato che il redirect dalla banca non porta con sé l'header Authorization.
  const validUntil = new Date(Date.now() + 180 * 24 * 3600 * 1000).toISOString(); // 180 giorni, il massimo consentito da PSD2
  const state = base64url(JSON.stringify({
    userId,
    ownerPersonId,
    aspspName,
    displayName: body.displayName || null,
    validUntil,
  }));

  const supabaseProjectRef = new URL(Deno.env.get('SUPABASE_URL') || '').hostname.split('.')[0];
  const redirectUrl = `https://${supabaseProjectRef}.supabase.co/functions/v1/enable-banking-callback`;

  try {
    const jwt = await createEnableBankingJWT(appId, privateKeyPem);
    const authRes = await fetch(`${ENABLE_BANKING_API_BASE}/auth`, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + jwt, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        access: { valid_until: validUntil },
        aspsp: { name: aspspName, country },
        state,
        redirect_url: redirectUrl,
        psu_type: 'personal',
      }),
    });

    const data = await authRes.json();
    if (!authRes.ok) {
      return new Response(
        JSON.stringify({ error: { message: 'Errore Enable Banking: ' + (data?.message || data?.error || authRes.status) } }),
        { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    return new Response(JSON.stringify({ url: data.url }), {
      status: 200,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (e) {
    return new Response(
      JSON.stringify({ error: { message: 'Errore chiamata Enable Banking: ' + (e as Error).message } }),
      { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
