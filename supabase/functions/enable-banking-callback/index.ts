// Supabase Edge Function: riceve il redirect da Enable Banking dopo il consenso
// dell'utente sulla propria banca. Scambia il "code" con una sessione (POST /sessions),
// crea le righe in cm_bank_connections (una per conto restituito) e reindirizza il
// browser alla pagina da cui è partito il collegamento, con l'esito.
//
// Questo è l'URL da registrare come redirect_uri nell'applicazione Enable Banking:
//   https://<project-ref>.supabase.co/functions/v1/enable-banking-callback
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY.
// SUPABASE_URL e SUPABASE_SERVICE_ROLE_KEY sono già forniti automaticamente da Supabase
// a ogni edge function.
//
// v1 — 2026-07-18
// v2 — 2026-08-03: la gestione dei conti collegati è passata a finanza.html ed è condivisa
//   tra moduli. Il redirect finale non è più fisso su cost-analysis.html: si ricava dal
//   "module" che enable-banking-connect ha messo nello state.
// v3 — 2026-08-03: lettura dei conti tollerante alle varianti di formato (UniCredit non
//   restituiva niente sul formato assunto per Revolut), con GET /sessions/{id} di riserva e
//   salvataggio del collegamento anche quando la lista resta vuota, più traccia diagnostica
//   in cm_sync_log.
// v4 — 2026-08-06: la riga "consent_request" scritta da enable-banking-connect viene agganciata
//   alla connessione appena creata (state.logId), così l'app sa dire cosa era stato chiesto per
//   quel collegamento. Con state.replaceConnectionId il consenso rifatto prende il posto di un
//   collegamento rimasto senza conto: i fondi ci vengono spostati sopra e la riga vuota sparisce.

import { createClient } from 'npm:@supabase/supabase-js@2';

const ENABLE_BANKING_API_BASE = 'https://api.enablebanking.com';
const APP_BASE_URL = 'https://garsal.netlify.app';
// Dove torna l'utente dopo il consenso, per modulo. La gestione dei conti sta in
// finanza.html (sezione Conti Collegati) per entrambi: cost-analysis.html non ha più
// la schermata di collegamento.
const MODULE_REDIRECT: Record<string, string> = {
  cost_analysis: `${APP_BASE_URL}/finanza.html`,
  fondo: `${APP_BASE_URL}/finanza.html`,
};
const DEFAULT_REDIRECT_URL = `${APP_BASE_URL}/finanza.html`;

function base64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : new Uint8Array(input);
  let str = '';
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64urlDecode(input: string): string {
  const padded = input.replace(/-/g, '+').replace(/_/g, '/');
  return atob(padded);
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

// I conti possono arrivare in tre posti diversi, e non sempre insieme: "accounts_data"
// (oggetti completi, con IBAN), "accounts" (spesso solo gli uid come stringhe) e
// "account_ids". Si leggono tutti e si uniscono per uid, tenendo l'IBAN quando c'è: leggere
// solo "accounts" significa perdere il conto quando la banca popola soltanto accounts_data.
// uid deve essere una stringa — in Enable Banking account_id è spesso l'oggetto { iban }, e
// prenderlo per buono salverebbe "[object Object]" come identificativo del conto.
// NOTA: questa funzione è duplicata in enable-banking-callback e
// enable-banking-refresh-accounts (le Edge Function non condividono moduli): vanno tenute
// allineate.
function extractAccounts(data: any): { uid: string; iban: string | null }[] {
  const found = new Map<string, string | null>();
  const add = (uid: unknown, iban: unknown) => {
    if (typeof uid !== 'string' || !uid) return;
    const ibanStr = typeof iban === 'string' && iban ? iban : null;
    if (!found.has(uid) || (ibanStr && !found.get(uid))) found.set(uid, ibanStr);
  };
  for (const key of ['accounts_data', 'accounts', 'account_ids']) {
    const list = (data || {})[key];
    if (!Array.isArray(list)) continue;
    for (const acc of list) {
      if (typeof acc === 'string') { add(acc, null); continue; }
      const uid = [acc?.uid, acc?.account_id, acc?.id, acc?.resource_id, acc?.resourceId, acc?.identification_hash]
        .find((v: unknown) => typeof v === 'string' && v.length > 0);
      add(uid, acc?.account_id?.iban || acc?.identification?.iban || acc?.iban || null);
    }
  }
  return [...found.entries()].map(([uid, iban]) => ({ uid, iban }));
}

// Diagnostica leggibile senza esporre l'intera risposta: chiavi presenti e forma dei campi
// che dovrebbero contenere i conti.
function describeResponse(data: any): string {
  try {
    const keys = Object.keys(data || {}).join(', ');
    const shape = (v: unknown) =>
      Array.isArray(v) ? `array(${v.length})${v.length ? ' di ' + typeof v[0] : ''}` : v === undefined ? 'assente' : typeof v;
    return `chiavi: [${keys}] · accounts: ${shape(data?.accounts)} · account_ids: ${shape(data?.account_ids)}`.slice(0, 900);
  } catch {
    return 'risposta non ispezionabile';
  }
}

// module può mancare (consenso rifiutato prima ancora di poter leggere lo state): in quel
// caso si torna comunque su finanza.html, dove sta la gestione dei conti.
function redirectTo(status: 'success' | 'error' | 'partial', message?: string, module?: string): Response {
  const url = new URL((module && MODULE_REDIRECT[module]) || DEFAULT_REDIRECT_URL);
  url.searchParams.set('bank_connect', status);
  if (module) url.searchParams.set('bank_connect_module', module);
  if (message) url.searchParams.set('bank_connect_message', message);
  return new Response(null, { status: 302, headers: { Location: url.toString() } });
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
  const url = new URL(req.url);
  const code = url.searchParams.get('code');
  const stateRaw = url.searchParams.get('state');
  const bankError = url.searchParams.get('error') || url.searchParams.get('error_description');

  if (bankError) {
    return redirectTo('error', 'Consenso rifiutato o annullato sulla banca.');
  }
  if (!code || !stateRaw) {
    return redirectTo('error', 'Callback incompleto (code o state mancante).');
  }

  let state: {
    userId: string;
    ownerPersonId: string | null;
    aspspName: string;
    displayName: string | null;
    module?: string;
    validUntil: string;
    logId?: string | null;
    ibanRequested?: string | null;
    replaceConnectionId?: string | null;
  };
  try {
    state = JSON.parse(base64urlDecode(stateRaw));
  } catch {
    return redirectTo('error', 'State non valido.');
  }
  // I collegamenti creati prima della v2 non hanno "module" nello state.
  const module = state.module === 'fondo' ? 'fondo' : 'cost_analysis';

  const appId = Deno.env.get('ENABLE_BANKING_APP_ID');
  const privateKeyPem = Deno.env.get('ENABLE_BANKING_PRIVATE_KEY');
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!appId || !privateKeyPem || !supabaseUrl || !supabaseServiceRoleKey) {
    return redirectTo('error', 'Configurazione mancante lato server.', module);
  }

  try {
    const jwt = await createEnableBankingJWT(appId, privateKeyPem);
    const psu = psuHeaders(req);
    const sessionRes = await fetch(`${ENABLE_BANKING_API_BASE}/sessions`, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + jwt, 'Content-Type': 'application/json', ...psu },
      body: JSON.stringify({ code }),
    });
    const sessionData = await sessionRes.json();
    if (!sessionRes.ok) {
      return redirectTo('error', 'Errore Enable Banking: ' + (sessionData?.message || sessionRes.status), module);
    }

    const supabase = createClient(supabaseUrl, supabaseServiceRoleKey);
    const sessionId = sessionData.session_id || sessionData.sessionId || null;

    // Prima versione: si leggeva solo sessionData.accounts come array di oggetti con "uid".
    // Con Revolut funzionava, con UniCredit no — la lista dei conti è tornata vuota e il
    // collegamento veniva buttato via con un errore, perdendo anche la sessione appena
    // ottenuta. Ora si accettano tutte le forme plausibili e, se non basta, si richiede la
    // sessione con una GET dedicata: certi ASPSP popolano i conti solo lì.
    let accounts = extractAccounts(sessionData);
    let accountsSource = 'POST /sessions';
    let detailData: any = null;
    if (!accounts.length && sessionId) {
      const detailRes = await fetch(`${ENABLE_BANKING_API_BASE}/sessions/${sessionId}`, {
        headers: { Authorization: 'Bearer ' + jwt, ...psu },
      });
      detailData = await detailRes.json().catch(() => ({}));
      if (detailRes.ok) {
        accounts = extractAccounts(detailData);
        accountsSource = 'GET /sessions/{id}';
      }
    }

    // Se il consenso era stato chiesto per un IBAN preciso e la banca ne restituisce più di
    // uno, quello richiesto va per primo: è la riga che eredita il fondo quando questo
    // consenso ne sostituisce uno vuoto.
    const normIban = (s: string | null | undefined) => (s || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    const wanted = normIban(state.ibanRequested);
    if (wanted && accounts.length > 1) {
      accounts.sort((a, b) => Number(normIban(b.iban) === wanted) - Number(normIban(a.iban) === wanted));
    }

    // Nessun conto nemmeno così: la riga si salva lo stesso, senza account_id. Il consenso
    // è già stato dato e rifarlo da capo costa un altro giro di SCA; con la riga in mano si
    // vede cosa è arrivato e si può correggere. Il sync si rifiuta di partire finché
    // account_id è nullo, quindi non c'è il rischio di importare dal conto sbagliato.
    const rows = accounts.length
      ? accounts.map((acc) => ({
          user_id: state.userId,
          provider: 'enable_banking',
          aspsp_name: state.aspspName,
          display_name: state.displayName || acc.iban || null,
          owner_person_id: state.ownerPersonId || null,
          account_id: acc.uid,
          consent_id: sessionId,
          consent_expires_at: sessionData.access?.valid_until || state.validUntil,
          status: 'active',
          module,
        }))
      : [{
          user_id: state.userId,
          provider: 'enable_banking',
          aspsp_name: state.aspspName,
          display_name: state.displayName || null,
          owner_person_id: state.ownerPersonId || null,
          account_id: null,
          consent_id: sessionId,
          consent_expires_at: sessionData.access?.valid_until || state.validUntil,
          status: 'active',
          module,
        }];

    const { data: inserted, error } = await supabase.from('cm_bank_connections').insert(rows).select('id, account_id');
    if (error) {
      return redirectTo('error', 'Errore salvataggio conto: ' + error.message, module);
    }
    const newConnectionId = inserted?.[0]?.id || null;

    // La richiesta di consenso era stata tracciata prima di partire, senza sapere quale
    // connessione ne sarebbe nata: adesso si sa. Da qui in poi l'app può mostrare, su quel
    // collegamento, se l'IBAN era stato spedito davvero — che è l'unica cosa che distingue
    // "consenso chiesto male" da "banca che ignora l'IBAN".
    if (state.logId && newConnectionId) {
      await supabase.from('cm_sync_log')
        .update({ bank_connection_id: newConnectionId })
        .eq('id', state.logId)
        .eq('user_id', state.userId);
    }

    // Consenso rifatto per rimpiazzare un collegamento rimasto senza conto: i fondi che lo
    // usavano vanno spostati sul nuovo, altrimenti restano agganciati a una riga che non
    // sincronizzerà mai — è il passaggio che a mano ci si dimentica. Si sostituisce solo una
    // riga davvero vuota e davvero dell'utente. Se anche il nuovo consenso nasce senza conti
    // la sostituzione si fa lo stesso, sul collegamento appena creato: due righe vuote per lo
    // stesso conto non aiutano nessuno, e quella nuova ha almeno una sessione fresca da
    // rileggere. Il fondo resta agganciato a un conto senza account_id, e l'app lo dice.
    const firstWithAccount = (inserted || []).find((r) => r.account_id)?.id || null;
    const replacementTarget = firstWithAccount || newConnectionId;
    if (state.replaceConnectionId && replacementTarget && state.replaceConnectionId !== replacementTarget) {
      const { data: old } = await supabase
        .from('cm_bank_connections')
        .select('id, account_id')
        .eq('id', state.replaceConnectionId)
        .eq('user_id', state.userId)
        .is('account_id', null)
        .maybeSingle();
      if (old) {
        await supabase.from('fnz_funds')
          .update({ bank_connection_id: replacementTarget })
          .eq('bank_connection_id', old.id)
          .eq('user_id', state.userId);
        // cm_sync_log.bank_connection_id cancella in cascata: le righe del tentativo fallito
        // si staccano prima, sono la storia di come ci si è arrivati.
        await supabase.from('cm_sync_log')
          .update({ bank_connection_id: null })
          .eq('bank_connection_id', old.id);
        await supabase.from('cm_bank_connections').delete().eq('id', old.id).eq('user_id', state.userId);
      }
    }

    if (!accounts.length) {
      // Traccia diagnostica: senza sapere cosa ha risposto davvero la banca non si può
      // capire perché la lista è vuota. Finisce in cm_sync_log, visibile dall'app.
      await supabase.from('cm_sync_log').insert({
        user_id: state.userId,
        bank_connection_id: inserted?.[0]?.id || null,
        finished_at: new Date().toISOString(),
        status: 'no_accounts',
        imported_count: 0,
        error_message: `Nessun conto nella risposta (ultimo tentativo: ${accountsSource}).` +
          ` IBAN richiesto nel consenso: ${state.ibanRequested ? 'sì' : 'NO'}.` +
          ` POST /sessions → ${describeResponse(sessionData)}` +
          (detailData ? ` · GET /sessions/${sessionId} → ${describeResponse(detailData)}` : ''),
      });
      return redirectTo('partial', 'La banca non ha restituito nessun conto: il collegamento è stato salvato senza conto associato.', module);
    }

    return redirectTo('success', undefined, module);
  } catch (e) {
    return redirectTo('error', 'Errore imprevisto: ' + (e as Error).message, module);
  }
});
