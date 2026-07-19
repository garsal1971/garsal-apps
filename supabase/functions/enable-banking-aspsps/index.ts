// Supabase Edge Function: cerca nel catalogo ASPSP di Enable Banking il nome/paese esatto di
// una banca (es. Revolut, N26), da usare in enable-banking-connect. Serve perché il nome banca
// deve combaciare esattamente con quello nel loro catalogo — "Wrong ASPSP name provided" se non
// corrisponde alla lettera.
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY.
// v1 — 2026-07-19

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
  const body = { iss: 'enablebanking.com', aud: 'api.enablebanking.com', iat: now, exp: now + 3600 };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(body))}`;
  const key = await importPrivateKey(privateKeyPem);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput));
  return `${signingInput}.${base64url(signature)}`;
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

  let body: { query?: string; country?: string };
  try {
    body = await req.json();
  } catch {
    body = {};
  }
  const query = (body.query || '').trim().toLowerCase();
  const country = (body.country || '').trim().toUpperCase();

  try {
    const jwt = await createEnableBankingJWT(appId, privateKeyPem);
    const res = await fetch(`${ENABLE_BANKING_API_BASE}/aspsps`, {
      headers: { Authorization: 'Bearer ' + jwt },
    });
    const data = await res.json();
    if (!res.ok) {
      return new Response(
        JSON.stringify({ error: { message: 'Errore Enable Banking: ' + (data?.message || res.status) } }),
        { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const all: any[] = Array.isArray(data.aspsps) ? data.aspsps : Array.isArray(data) ? data : [];
    const filtered = all.filter((a) => {
      const nameMatch = !query || String(a.name || '').toLowerCase().includes(query);
      const countryMatch = !country || String(a.country || '').toUpperCase() === country;
      return nameMatch && countryMatch;
    }).slice(0, 50);

    return new Response(
      JSON.stringify({ results: filtered.map((a) => ({ name: a.name, country: a.country, logo: a.logo || null })) }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (e) {
    return new Response(
      JSON.stringify({ error: { message: 'Errore chiamata Enable Banking: ' + (e as Error).message } }),
      { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
