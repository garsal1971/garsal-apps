// Supabase Edge Function: avvia il collegamento di un conto via Enable Banking (PSD2,
// restricted mode). Firma un JWT con la chiave privata dell'applicazione, chiama
// POST /auth su Enable Banking e restituisce l'URL a cui reindirizzare l'utente per il
// consenso sulla propria banca (login + autorizzazione).
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY (PEM),
// SUPABASE_SERVICE_ROLE_KEY (per decodificare in sicurezza l'utente chiamante).
//
// Il callback dopo il consenso arriva su enable-banking-callback, che crea davvero la riga
// in cm_bank_connections — questa function non scrive nel database.
//
// v1 — 2026-07-18
// v2 — 2026-08-03: i conti sono condivisi tra moduli. "module" dice a quale modulo serve il
//   conto ('cost_analysis' = Spese Famiglia, 'fondo' = modulo Fondi) e viaggia nello state
//   fino al callback, che lo usa per riportare l'utente sulla pagina giusta. ownerPersonId
//   (la persona di Analisi Costi a cui attribuire le spese) diventa facoltativo: per un conto
//   del fondo non esiste "chi ha speso".
// v3 — 2026-08-04: "iban" facoltativo, passato in access.accounts (vedi il commento sulla
//   chiamata /auth: senza, UniCredit autorizza una sessione senza nessun conto dentro).
// v4 — 2026-08-04: header PSU (psu-ip-address) su ogni chiamata a Enable Banking. UniCredit
//   lo dichiara obbligatorio in required_psu_headers e senza autorizzava un consenso senza
//   conti, in silenzio; Revolut non lo richiede, per questo funzionava.
// v5 — 2026-08-06: l'IBAN viene validato (formato + checksum mod-97) prima di partire — un
//   IBAN sbagliato produce lo stesso consenso vuoto di un IBAN mancante, ma lo si scopre solo
//   dopo l'SCA. La riga di traccia in cm_sync_log viene creata con l'id in mano e l'id viaggia
//   nello state fino al callback, che lo aggancia alla connessione appena creata: così l'app
//   può DIRE cosa è stato spedito per quel collegamento invece di dedurlo. replaceConnectionId
//   dice quale collegamento vuoto questo consenso viene a sostituire.

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

// Un IBAN sbagliato di una cifra vale quanto un IBAN assente: la banca non riconosce nessun
// conto e restituisce una sessione autorizzata e vuota, dopo che l'utente ha già fatto login
// e SCA. Il checksum mod-97 (ISO 13616) costa niente e intercetta i refusi prima di partire.
function ibanIsValid(iban: string): boolean {
  if (!/^[A-Z]{2}[0-9]{2}[A-Z0-9]{6,30}$/.test(iban)) return false;
  const rearranged = iban.slice(4) + iban.slice(0, 4);
  let remainder = 0n;
  for (const ch of rearranged) {
    const digits = ch >= '0' && ch <= '9' ? ch : String(ch.charCodeAt(0) - 55);
    remainder = BigInt(String(remainder) + digits) % 97n;
  }
  return remainder === 1n;
}

// Alcune banche pretendono di sapere da quale IP arriva l'utente che sta autorizzando: lo
// dichiarano in required_psu_headers nel catalogo /aspsps (UniCredit IT chiede
// "psu-ip-address"). Senza quell'header il consenso viene comunque autorizzato, ma la banca
// non espone nessun conto — e non arriva nessun errore che lo spieghi.
// L'IP è quello del browser dell'utente, che raggiunge la Edge Function in x-forwarded-for:
// se manca (chiamata da un job, senza utente davanti) l'header non si inventa.
function psuHeaders(req: Request): Record<string, string> {
  const fwd = req.headers.get('x-forwarded-for') || '';
  const ip = fwd.split(',')[0].trim();
  if (!ip) return {};
  const headers: Record<string, string> = { 'psu-ip-address': ip };
  const ua = req.headers.get('user-agent');
  if (ua) headers['psu-user-agent'] = ua;
  return headers;
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

  let body: {
    aspspName?: string; country?: string; ownerPersonId?: string; displayName?: string;
    module?: string; iban?: string; allowAllAccounts?: boolean; clientVersion?: string;
    replaceConnectionId?: string;
  };
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
  const module = (body.module || 'cost_analysis').trim();
  const iban = (body.iban || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
  // Rinuncia esplicita all'IBAN: la manda solo la casella "la mia banca non lo richiede".
  const allowAllAccounts = body.allowAllAccounts === true;
  const clientVersion = (body.clientVersion || '').trim() || 'non dichiarata';
  // Collegamento vuoto che questo consenso viene a sostituire: il callback ci sposta sopra i
  // fondi e poi lo elimina, così l'utente non resta con due righe e il fondo agganciato a
  // quella morta.
  const replaceConnectionId = (body.replaceConnectionId || '').trim() || null;
  if (!aspspName || !country) {
    return new Response(
      JSON.stringify({ error: { message: 'Servono "aspspName" e "country".' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
  if (module !== 'cost_analysis' && module !== 'fondo') {
    return new Response(
      JSON.stringify({ error: { message: '"module" ammette solo "cost_analysis" o "fondo".' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
  // Su Spese Famiglia ogni transazione va attribuita a una persona: senza owner il sync non
  // saprebbe a chi intestare la spesa. Sul fondo la domanda non si pone.
  if (module === 'cost_analysis' && !ownerPersonId) {
    return new Response(
      JSON.stringify({ error: { message: 'Per un conto di Spese Famiglia serve "ownerPersonId".' } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  // Un consenso senza IBAN, con le banche che pretendono l'elenco dei conti, produce una
  // sessione autorizzata e vuota: l'utente fa login e SCA per niente e se ne accorge dopo.
  // È già successo tre volte, quindi il rifiuto sta qui e non solo nel form: una pagina
  // vecchia (che l'IBAN non lo chiede nemmeno) deve fallire subito e a voce alta.
  if (!iban && !allowAllAccounts) {
    return new Response(
      JSON.stringify({ error: { message:
        `Manca l'IBAN del conto. Senza, alcune banche (UniCredit) autorizzano un consenso che non ` +
        `collega nessun conto. Se la tua banca non lo richiede, spunta l'opzione apposita nel form. ` +
        `Se non vedi il campo IBAN la pagina è una versione vecchia (client: ${clientVersion}): ricaricala.` } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
  // Un IBAN spedito ma sbagliato fallisce esattamente come un IBAN non spedito, e costa un
  // giro di SCA per scoprirlo: si controlla qui, dove passa qualunque client.
  if (iban && !ibanIsValid(iban)) {
    return new Response(
      JSON.stringify({ error: { message:
        `IBAN non valido (${iban.slice(0, 6)}…${iban.slice(-4)}): il codice di controllo non torna. ` +
        `Ricopialo dall'estratto conto — un carattere sbagliato basta perché la banca non riconosca ` +
        `nessun conto e il consenso nasca vuoto.` } }),
      { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const supabaseProjectRef = new URL(Deno.env.get('SUPABASE_URL') || '').hostname.split('.')[0];
  const redirectUrl = `https://${supabaseProjectRef}.supabase.co/functions/v1/enable-banking-callback`;
  const psu = psuHeaders(req);
  const PSU_TYPE = 'personal';

  let jwt: string;
  try {
    jwt = await createEnableBankingJWT(appId, privateKeyPem);
  } catch (e) {
    return new Response(
      JSON.stringify({ error: { message: 'Chiave Enable Banking non utilizzabile: ' + (e as Error).message } }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  // Cosa la banca dichiara di pretendere, letto al momento della richiesta e non a posteriori:
  // quando la sessione torna autorizzata e vuota, la domanda successiva è sempre "e la banca
  // cosa voleva?" — e il catalogo può cambiare tra il collegamento e il momento in cui lo si
  // va a guardare. Da qui escono anche due valori che entrano nella richiesta: il tetto alla
  // durata del consenso e il metodo di autenticazione.
  let aspspDeclares = 'catalogo non letto';
  let maxValiditySeconds: number | null = null;
  let authMethodName: string | null = null;
  try {
    const res = await fetch(`${ENABLE_BANKING_API_BASE}/aspsps?country=${encodeURIComponent(country)}`, {
      headers: { Authorization: 'Bearer ' + jwt },
    });
    if (res.ok) {
      const data = await res.json();
      const list: any[] = Array.isArray(data.aspsps) ? data.aspsps : Array.isArray(data) ? data : [];
      const entry = list.find((a) => String(a?.name || '').toLowerCase() === aspspName.toLowerCase());
      if (!entry) {
        aspspDeclares = `banca non trovata nel catalogo ${country}`;
      } else {
        const required: string[] = Array.isArray(entry.required_psu_headers) ? entry.required_psu_headers : [];
        const missing = required.filter((h) => !(h.toLowerCase() in psu));
        const methods: any[] = Array.isArray(entry.auth_methods) ? entry.auth_methods : [];
        // UniCredit ne dichiara due, entrambe REDIRECT: la prima riga di log le appiattiva
        // sul solo nome e le rendeva indistinguibili. Quello che conta è a quale psu_type
        // ciascuna appartiene e se pretende credenziali da passare nella richiesta — un
        // metodo scelto da Enable Banking per il psu_type sbagliato autorizza un'utenza che
        // non ha conti da esporre, e il sintomo sarebbe esattamente una sessione vuota.
        const describeMethod = (m: any) => typeof m === 'string' ? m :
          `${m?.name || m?.approach || '?'}` +
          `${m?.psu_type ? `/${m.psu_type}` : ''}` +
          `${m?.hidden_method ? '/hidden' : ''}` +
          `${Array.isArray(m?.credentials) && m.credentials.length
            ? `/credenziali:[${m.credentials.map((c: any) => c?.name || c?.title || '?').join('|')}]` : ''}`;
        // Si manda auth_method solo quando la banca lega esplicitamente un metodo al nostro
        // psu_type, uno solo, e gli dà un nome. Se il legame non è dichiarato la scelta resta
        // a Enable Banking: indovinare un identificativo fa fallire /auth e basta, e questa
        // versione serve prima di tutto a vedere com'è fatta davvero quella lista.
        const forOurPsuType = methods.filter((m: any) => m?.psu_type === PSU_TYPE);
        if (forOurPsuType.length === 1 && typeof forOurPsuType[0]?.name === 'string' && forOurPsuType[0].name) {
          authMethodName = forOurPsuType[0].name;
        }
        maxValiditySeconds = Number.isFinite(entry.maximum_consent_validity) ? entry.maximum_consent_validity : null;
        aspspDeclares =
          `psu_types: [${(entry.psu_types || []).join(', ')}]` +
          ` · auth_methods: [${methods.map(describeMethod).join(' ; ')}]` +
          ` · required_psu_headers: [${required.join(', ')}]${missing.length ? ` — NON INVIATI: [${missing.join(', ')}]` : ''}` +
          ` · maximum_consent_validity: ${entry.maximum_consent_validity ?? 'n/d'}` +
          (entry.beta ? ' · BETA' : '') + (entry.sandbox ? ' · SANDBOX' : '');
      }
    } else {
      aspspDeclares = `catalogo non leggibile (HTTP ${res.status})`;
    }
  } catch (e) {
    aspspDeclares = 'catalogo non leggibile: ' + (e as Error).message;
  }

  // Durata del consenso: 180 giorni è il massimo PSD2, ma UniCredit dichiara esattamente
  // 15 552 000 secondi — 180 giorni tondi. Chiedere il massimo esatto significa appoggiarsi
  // al bordo: basta qualche secondo di scarto tra l'orologio di qui e quello di Enable
  // Banking per sforare. Un'ora di margine non toglie niente a nessuno.
  const requestedSeconds = Math.min(180 * 24 * 3600, maxValiditySeconds ?? 180 * 24 * 3600) - 3600;
  const validUntil = new Date(Date.now() + requestedSeconds * 1000).toISOString();

  // Traccia di cosa si sta per chiedere: quando la sessione torna autorizzata ma senza
  // conti, l'unico modo per sapere se l'IBAN era stato spedito è averlo scritto qui prima
  // di partire. L'IBAN viene mascherato: serve sapere se c'era, non qual era.
  // L'id della riga viaggia nello state: il callback ci scrive sopra la connessione appena
  // creata, e da lì in poi l'app sa dire cosa era stato chiesto PER QUEL collegamento —
  // prima poteva solo guardare access.accounts nella sessione e tirare a indovinare.
  const accessRequested = {
    valid_until: validUntil,
    balances: true,
    transactions: true,
    ...(iban ? { accounts: [{ iban }] } : {}),
  };

  let logId: string | null = null;
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL');
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    if (supabaseUrl && serviceRoleKey) {
      const { data: logRow } = await createClient(supabaseUrl, serviceRoleKey)
        .from('cm_sync_log')
        .insert({
          user_id: userId,
          finished_at: new Date().toISOString(),
          status: 'consent_request',
          imported_count: 0,
          error_message: `Richiesta consenso ${aspspName} (${country}) · modulo ${module} · IBAN ` +
            (iban ? `${iban.slice(0, 6)}…${iban.slice(-4)}` : 'NON INVIATO (rinuncia esplicita)') +
            ` · access.accounts: ${accessRequested.accounts ? 'valorizzato' : 'null (tutti i conti)'}` +
            ` · psu-ip-address: ${psu['psu-ip-address'] ? 'inviato' : 'ASSENTE'}` +
            ` · psu_type inviato: ${PSU_TYPE}` +
            ` · auth_method inviato: ${authMethodName || 'nessuno (scelta lasciata a Enable Banking)'}` +
            ` · valid_until chiesto: ${validUntil}` +
            ` · client ${clientVersion}` +
            ` · la banca dichiara → ${aspspDeclares}`,
        })
        .select('id')
        .single();
      logId = logRow?.id || null;
    }
  } catch {
    // La traccia è diagnostica: se non si riesce a scrivere, il collegamento va avanti lo stesso.
  }

  const state = base64url(JSON.stringify({
    userId,
    ownerPersonId: ownerPersonId || null,
    aspspName,
    displayName: body.displayName || null,
    module,
    validUntil,
    logId,
    ibanRequested: iban || null,
    replaceConnectionId,
  }));

  try {
    const authRes = await fetch(`${ENABLE_BANKING_API_BASE}/auth`, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + jwt, 'Content-Type': 'application/json', ...psu },
      body: JSON.stringify({
        // access.accounts elenca i conti su cui il consenso deve valere. ATTENZIONE a cosa
        // NON fa: mandarlo non garantisce che venga applicato. Con UniCredit (IT) le sessioni
        // del 5 e del 6 agosto 2026 sono partite con accounts: [{iban}] e sono tornate con
        // access.accounts: null e accounts: [] — Enable Banking o la banca lo scartano, quindi
        // il consenso finisce per valere genericamente su "tutti i conti" e la banca non ne
        // espone nessuno. Con Revolut invece funziona anche senza elenco. Lo si manda lo
        // stesso perché quando viene applicato è la strada giusta, ma NON è la spiegazione di
        // una sessione vuota: quella va cercata in cosa la banca dichiara (loggato qui sopra)
        // e nel tipo di conto — PSD2 copre i conti di pagamento, non depositi e libretti.
        // balances/transactions erano già i default aggiunti da Enable Banking: meglio
        // dichiararli invece di ereditarli in silenzio.
        access: accessRequested,
        aspsp: { name: aspspName, country },
        state,
        redirect_url: redirectUrl,
        psu_type: PSU_TYPE,
        // Solo se il catalogo ne dichiara uno solo per il nostro psu_type e con un nome
        // esplicito: altrimenti sceglie Enable Banking, come ha sempre fatto.
        ...(authMethodName ? { auth_method: authMethodName } : {}),
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
