// Supabase Edge Function schedulata (cron configurato da Supabase Dashboard, non da questo
// repo — vedi fill-notification-queue per lo stesso pattern): legge le nuove transazioni dal
// conto Revolut collegato via Enable Banking, le categorizza in modo deterministico (MCC →
// merchant appreso → regole — NESSUNA AI), e se restano transazioni senza categoria manda una
// notifica Smart Block (app Android) per ricordare di finire a mano la categorizzazione.
//
// Fetch/dedup/merge/attribuzione carta duplicati (non importati) da enable-banking-sync/
// index.ts: quella funzione è pensata per essere chiamata da un browser con un JWT utente reale
// (verify_jwt=false + decodifica manuale del claim "sub"), questa invece gira solo su
// invocazione cron senza utente interattivo — niente config.toml, verify_jwt resta al default
// (true), lo user id viene dal secret REVOLUT_SYNC_USER_ID invece che da un JWT.
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY,
// REVOLUT_SYNC_USER_ID (uuid dell'utente — impostato una tantum con `supabase secrets set`).
// v1 — 2026-07-24 · v1.1 — 2026-07-24: fix entity_id/entity_type per il vincolo di
// cm_notification_rules (vedi migration 20260724120000), nessuna modifica funzionale
// v1.2 — 2026-07-24: modalità test dry-run — POST con body { "test_transactions": [...] }
// (stesso shape di Enable Banking: transaction_amount.amount/currency, credit_debit_indicator,
// remittance_information, booking_date, entry_reference, merchant_category_code) simula
// dedup/categorizzazione/notifica senza scrivere nulla, per testare la pipeline senza toccare
// il conto Revolut reale.
// v1.3 — 2026-07-24: metadata.device_token sulla notifica (senza il blocco non compariva mai
// sul telefono) + metadata.cost_analysis.{transactions,categories} per la categorizzazione
// interattiva dalla schermata di blocco (vedi BlockWindowManager.kt + RPC
// ca_smart_block_set_category, migration 20260724190000).
// v1.3.1 — 2026-07-24: ogni categoria include ora parent_id, per il picker Android con lista
// raggruppata principale/sottocategorie + ricerca (stesso pattern di
// renderTxCategorySuggestions in cost-analysis.html).
// v1.3.2 — 2026-07-24: filtra le categorie con ca_categories.visible_in_app=false dal payload
// dello Smart Block (migration 20260724290000).
// v1.3.3 — 2026-07-24: la cascata principale → sotto-categorie ora avviene in scrittura lato
// cost-analysis.html (propagazione al salvataggio), non più ricalcolata qui — si filtra solo
// sul valore proprio di ogni categoria.
// v1.3.4 — 2026-07-24: non accoda più una notifica smart_block se ce n'è già una pending per
// la stessa regola, per non accumulare righe duplicate ad ogni run del cron finché l'utente non
// le smaltisce.
// v1.4 — 2026-08-01: ogni transazione nel payload Smart Block porta anche `spender` (nome della
// persona che ha pagato, da ca_people via ca_transactions.spender_person_id) e `time` (ora del
// pagamento estratta da ca_transactions.raw, vedi extractPaymentTime) — servono a
// BlockWindowManager.kt per dire chi ha fatto il pagamento e a che ora.
// v1.5 — 2026-08-01: due destinatari invece di uno. Oltre alla notifica di Salvatore (tutte le
// transazioni senza categoria, invariata) ne viene accodata una per il telefono di Teresa con le
// sole transazioni fatte con la sua carta (spender_person_id = ca_people 'Teresa', attribuzione
// già fatta da enable-banking-sync via ca_card_person_map). Le due righe si distinguono per
// metadata.device_token, quindi il controllo "ne esiste già una pending" è ora per destinatario
// e non più per regola: altrimenti la pending di uno bloccherebbe la notifica dell'altro.

import { createClient } from 'npm:@supabase/supabase-js@2';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
};

const ENABLE_BANKING_API_BASE = 'https://api.enablebanking.com';

// ── Telefono di Teresa (flavor "teresa" dell'APK Smart Blocker) ────────────────────────────
// Riceve una notifica separata con le sole transazioni fatte con la sua carta. Il token è una
// costante condivisa con l'APK (build.gradle → buildConfigField FIXED_DEVICE_TOKEN): non è un
// segreto e non protegge nulla — la policy smart_block_anon_select lascia leggere tutte le
// righe smart_block a chiunque abbia la anon key — serve solo a indirizzare la riga al telefono
// giusto. Se cambia qui va cambiato anche là, altrimenti il suo telefono smette di vedere le
// notifiche (senza errori: semplicemente nessuna riga risulta "sua").
const TERESA_DEVICE_TOKEN = 'teresa-smartblock';
// Come si chiama Teresa in ca_people. Il collegamento carta → persona lo fa già
// enable-banking-sync via ca_card_person_map: qui serve solo a risalire dal nome all'id.
const TERESA_PERSON_NAME = 'teresa';

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

// Estrae un campo da più varianti di nome plausibili (snake_case / camelCase / Berlin Group).
function pick(obj: any, ...keys: string[]): unknown {
  for (const k of keys) {
    if (obj && obj[k] !== undefined && obj[k] !== null) return obj[k];
  }
  return null;
}

// Ora del pagamento — né ca_transactions.date (è un `date`, senza orario) né created_at (è il
// momento del sync, non del pagamento) la contengono: l'unico posto dove sopravvive è
// ca_transactions.raw.
//   · import CSV Revolut  → la riga originale, con "started date"/"completed date" in formato
//     'YYYY-MM-DD HH:MM:SS' (cost-analysis.html ne tiene solo la parte data);
//   · sync bancario       → il JSON di Enable Banking, dove l'orario, quando c'è, sta in un
//     campo *_date_time o transaction_time.
// Vale la stessa nota di enable-banking-sync: i nomi dei campi non sono garantiti, quindi dopo
// i campi noti si scansionano tutti i valori stringa in cerca di un 'YYYY-MM-DD HH:MM' — un
// orario da solo viene accettato solo dai campi noti, per non scambiare per orario un "18:30"
// finito dentro una descrizione. Se non si trova nulla si restituisce null e l'app non mostra
// alcun orario, invece di inventarne uno.
const DATETIME_RE = /\d{4}-\d{2}-\d{2}[T ](?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?/;
const BARE_TIME_RE = /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/;
const TIME_ZONE = 'Europe/Rome';

/** 'HH:MM' da una stringa data+ora; se porta un fuso esplicito (Z o ±HH:MM) l'ora viene
    convertita a Europe/Rome, altrimenti si prende alla lettera com'è scritta. */
function timeFromDateTime(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const m = value.match(DATETIME_RE);
  if (!m) return null;
  const stamp = m[0];
  if (/(?:Z|[+-]\d{2}:?\d{2})$/.test(stamp)) {
    const d = new Date(stamp);
    if (!isNaN(d.getTime())) {
      return new Intl.DateTimeFormat('it-IT', {
        timeZone: TIME_ZONE, hour: '2-digit', minute: '2-digit', hour12: false,
      }).format(d);
    }
  }
  return stamp.slice(11, 16);
}

function extractPaymentTime(raw: unknown): string | null {
  if (!raw || typeof raw !== 'object') return null;
  const obj = raw as Record<string, unknown>;
  const knownKeys = [
    'transaction_time', 'transactionTime',
    'booking_date_time', 'bookingDateTime',
    'value_date_time', 'valueDateTime',
    'transaction_date_time', 'transactionDateTime',
    'started date', 'Started Date', 'completed date', 'Completed Date',
    'data di inizio', 'Data di inizio', 'data di completamento', 'Data di completamento',
  ];
  for (const k of knownKeys) {
    const v = obj[k];
    const fromDateTime = timeFromDateTime(v);
    if (fromDateTime) return fromDateTime;
    if (typeof v === 'string' && BARE_TIME_RE.test(v.trim())) return v.trim().slice(0, 5);
  }
  return scanForDateTime(obj, 2);
}

/** Scansione generica in profondità limitata: la prima stringa data+ora incontrata vince. */
function scanForDateTime(node: unknown, depth: number): string | null {
  if (depth < 0) return null;
  const direct = timeFromDateTime(node);
  if (direct) return direct;
  if (node && typeof node === 'object') {
    for (const v of Object.values(node as Record<string, unknown>)) {
      const t = scanForDateTime(v, depth - 1);
      if (t) return t;
    }
  }
  return null;
}

// PostgREST limita di default una select() senza .range() a 1000 righe (stesso bug/fix già
// applicato in enable-banking-sync e lato client con fetchAllRows in cost-analysis.html).
async function fetchAllRows(makeQuery: (from: number, to: number) => PromiseLike<{ data: any[] | null; error: any }>): Promise<any[]> {
  const PAGE_SIZE = 1000;
  const all: any[] = [];
  let from = 0;
  while (true) {
    const { data, error } = await makeQuery(from, from + PAGE_SIZE - 1);
    if (error) throw new Error(error.message);
    all.push(...(data || []));
    if (!data || data.length < PAGE_SIZE) break;
    from += PAGE_SIZE;
  }
  return all;
}

// ==========================================================================
// Categorizzazione deterministica — porting di categorizeRow/normalizeMerchant/matchesPattern
// da cost-analysis.html (solo categoria, "per chi" fuori scope per questa funzione).
// ==========================================================================
function normalizeMerchant(desc: string | null): string {
  return (desc || '')
    .toUpperCase()
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[*#]?\s*\d{3,}$/, '')
    .trim();
}

function matchesPattern(description: string | null, pattern: string): boolean {
  return (description || '').toUpperCase().includes((pattern || '').toUpperCase());
}

function categorizeCategory(
  description: string | null,
  mcc: string | null,
  mccMap: { mcc: string; category_id: string }[],
  merchantMap: { id: string; merchant_key: string }[],
  merchantMapCategories: { merchant_map_id: string; category_id: string }[],
  rules: { category_id: string; pattern: string; priority: number | null }[]
): { categoryId: string | null; source: 'mcc' | 'learned' | 'rule' | null } {
  if (mcc) {
    const m = mccMap.find((x) => x.mcc === mcc);
    if (m) return { categoryId: m.category_id, source: 'mcc' };
  }
  const key = normalizeMerchant(description);
  const mm = merchantMap.find((x) => x.merchant_key === key);
  if (mm) {
    const learned = merchantMapCategories.find((c) => c.merchant_map_id === mm.id);
    if (learned) return { categoryId: learned.category_id, source: 'learned' };
  }
  // Più regole possono matchare la stessa descrizione: vince quella con priorità più alta.
  const sorted = [...rules].sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));
  const r = sorted.find((x) => matchesPattern(description, x.pattern));
  if (r) return { categoryId: r.category_id, source: 'rule' };
  return { categoryId: null, source: null };
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders });
  }

  // Modalità test: POST con body { "test_transactions": [ ... shape Enable Banking ... ] }
  // salta la vera chiamata a Enable Banking e usa queste transazioni finte. Nessuna scrittura
  // (ca_transactions, ca_transaction_categories, cm_notification_queue, ca_sync_log) viene
  // eseguita: la function calcola dedup/categorizzazione/notifica e restituisce solo l'anteprima
  // in JSON. verify_jwt resta true (default) quindi resta comunque richiesta una chiamata
  // autenticata (anon key o service role key vanno bene).
  let testTransactions: unknown[] | null = null;
  if (req.method === 'POST') {
    try {
      const body = await req.json();
      if (Array.isArray(body?.test_transactions)) testTransactions = body.test_transactions;
    } catch {
      // body assente o non JSON: sync normale via cron, si ignora
    }
  }
  const isDryRun = testTransactions !== null;

  const appId = Deno.env.get('ENABLE_BANKING_APP_ID');
  const privateKeyPem = Deno.env.get('ENABLE_BANKING_PRIVATE_KEY');
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  const userId = Deno.env.get('REVOLUT_SYNC_USER_ID');
  if (!supabaseUrl || !supabaseServiceRoleKey || !userId || (!isDryRun && (!appId || !privateKeyPem))) {
    return new Response(
      JSON.stringify({ error: { message: 'Configurazione mancante (secrets Enable Banking, Supabase o REVOLUT_SYNC_USER_ID).' } }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }

  const supabase = createClient(supabaseUrl, supabaseServiceRoleKey);

  const { data: connection } = await supabase
    .from('ca_bank_connections')
    .select('*')
    .eq('user_id', userId)
    .eq('aspsp_name', 'Revolut')
    .eq('status', 'active')
    .not('account_id', 'is', null)
    .maybeSingle();

  if (!connection && !isDryRun) {
    return new Response(
      JSON.stringify({ skipped: 'no_active_revolut_connection' }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
  // In dry-run non serve una connessione Revolut reale (si testano solo dedup/categorizzazione):
  // se manca, bankConnectionId resta null e la query pendingSameConnection sotto viene saltata.
  const bankConnectionId = (connection?.id as string | undefined) ?? null;

  const { data: syncLog } = isDryRun
    ? { data: null }
    : await supabase
        .from('ca_sync_log')
        .insert({ user_id: userId, bank_connection_id: bankConnectionId, status: 'running' })
        .select()
        .single();

  try {
    const rawTransactions: unknown[] = [];
    let pageCount = 0;
    if (isDryRun) {
      rawTransactions.push(...(testTransactions as unknown[]));
    } else {
      const jwt = await createEnableBankingJWT(appId as string, privateKeyPem as string);

      const MAX_PAGES = 50;
      let continuationKey: string | null = null;
      do {
        const url = new URL(`${ENABLE_BANKING_API_BASE}/accounts/${connection!.account_id}/transactions`);
        if (continuationKey) url.searchParams.set('continuation_key', continuationKey);
        const txRes = await fetch(url.toString(), { headers: { Authorization: 'Bearer ' + jwt } });
        const txData = await txRes.json();
        if (!txRes.ok) {
          throw new Error('Errore Enable Banking: ' + (txData?.message || txRes.status));
        }
        const page: unknown[] = Array.isArray(txData.transactions) ? txData.transactions : [];
        rawTransactions.push(...page);
        continuationKey = txData.continuation_key || null;
        pageCount++;
      } while (continuationKey && pageCount < MAX_PAGES);
    }

    const { data: mccMap } = await supabase
      .from('ca_mcc_category_map')
      .select('mcc, category_id')
      .eq('user_id', userId);
    const { data: merchantMap } = await supabase
      .from('ca_merchant_map')
      .select('id, merchant_key')
      .eq('user_id', userId);
    const merchantMapIds = (merchantMap || []).map((m: any) => m.id);
    const { data: merchantMapCategories } = merchantMapIds.length
      ? await supabase.from('ca_merchant_map_categories').select('merchant_map_id, category_id').in('merchant_map_id', merchantMapIds)
      : { data: [] as any[] };
    const { data: rules } = await supabase
      .from('ca_rules')
      .select('category_id, pattern, priority')
      .eq('user_id', userId);

    const rows = rawTransactions.map((tx: any) => {
      const amountRaw = pick(tx, 'transaction_amount', 'transactionAmount') as any;
      const absAmount = Math.abs(parseFloat(pick(amountRaw, 'amount') as string) || 0);
      const indicator = (pick(tx, 'credit_debit_indicator', 'creditDebitIndicator') as string) || '';
      const amount = indicator === 'CRDT' ? absAmount : -absAmount;
      const currency = (pick(amountRaw, 'currency') as string) || null;
      const remittanceInfo = pick(
        tx,
        'remittance_information',
        'remittanceInformation',
        'remittance_information_unstructured',
        'remittanceInformationUnstructured'
      );
      const remittanceText = Array.isArray(remittanceInfo) ? remittanceInfo.join(' ') : ((remittanceInfo as string) || '');
      const creditor = pick(tx, 'creditor') as any;
      const debtor = pick(tx, 'debtor') as any;
      const creditorName = (creditor && (pick(creditor, 'name') as string)) || '';
      const debtorName = (debtor && (pick(debtor, 'name') as string)) || '';
      const description = remittanceText || creditorName || debtorName || '';
      const matchDescription = Array.isArray(remittanceInfo) && remittanceInfo.length > 1
        ? String(remittanceInfo[1] || '')
        : '';
      const date = (pick(tx, 'booking_date', 'bookingDate', 'value_date', 'valueDate') as string) || null;
      const externalId = (pick(tx, 'entry_reference', 'entryReference', 'transaction_id', 'transactionId') as string) || null;
      const mcc = (pick(tx, 'merchant_category_code', 'merchantCategoryCode', 'mcc') as string) || null;
      const bankTxCode = pick(tx, 'bank_transaction_code', 'bankTransactionCode') as any;
      const type = (bankTxCode && (pick(bankTxCode, 'code') as string)) || null;
      const cardIdList = pick(tx, 'debtor_account_additional_identification', 'debtorAccountAdditionalIdentification') as any[];
      const card = Array.isArray(cardIdList) && cardIdList.length ? cardIdList[0] : null;
      const cardIssuer = (card && (pick(card, 'issuer') as string)) || null;
      const cardIdentification = (card && (pick(card, 'identification') as string)) || null;
      return { date, amount, currency, description, matchDescription, externalId, mcc, type, cardIssuer, cardIdentification, raw: tx };
    }).filter((t) => t.date && t.externalId);

    let importedCount = 0;

    if (rows.length) {
      // Stessa logica dedup/merge di enable-banking-sync: descrizione+importo, poi
      // importo+data±5gg, poi data più vicina in caso di ambiguità residua.
      const AMOUNT_EPSILON = 0.01;
      const DATE_TOLERANCE_DAYS = 5;
      const dateDiffDays = (a: string, b: string): number =>
        Math.abs((new Date(a + 'T00:00:00Z').getTime() - new Date(b + 'T00:00:00Z').getTime()) / 86400000);

      const unlinked = await fetchAllRows((from, to) =>
        supabase
          .from('ca_transactions')
          .select('id, date, amount, mcc, type, spender_person_id, description')
          .eq('user_id', userId)
          .is('bank_connection_id', null)
          .range(from, to)
      );
      const pendingSameConnection = bankConnectionId
        ? await fetchAllRows((from, to) =>
            supabase
              .from('ca_transactions')
              .select('id, date, amount, mcc, type, spender_person_id, description')
              .eq('user_id', userId)
              .eq('bank_connection_id', bankConnectionId)
              .eq('raw->>status', 'PDNG')
              .range(from, to)
          )
        : [];

      const candidatePool = [...unlinked, ...pendingSameConnection];
      const toInsert: typeof rows = [];
      const toMerge: { existingId: string; row: (typeof rows)[number] }[] = [];
      for (const row of rows) {
        const desc = (row.matchDescription || '').trim().toLowerCase();
        const sameAmount = (c: (typeof candidatePool)[number]) => Math.abs(Number(c.amount) - row.amount) < AMOUNT_EPSILON;

        let matches = desc
          ? candidatePool.filter((c) => sameAmount(c) && (c.description || '').trim().toLowerCase() === desc)
          : [];
        if (!matches.length) {
          matches = candidatePool.filter((c) => sameAmount(c) && c.date && dateDiffDays(c.date, row.date) <= DATE_TOLERANCE_DAYS);
        }
        if (matches.length > 1) {
          const withDate = matches.filter((c) => c.date);
          if (withDate.length) {
            const minDiff = Math.min(...withDate.map((c) => dateDiffDays(c.date, row.date)));
            const narrowed = withDate.filter((c) => dateDiffDays(c.date, row.date) === minDiff);
            if (narrowed.length === 1) matches = narrowed;
          }
        }
        if (matches.length === 1) {
          toMerge.push({ existingId: matches[0].id, row });
          candidatePool.splice(candidatePool.indexOf(matches[0]), 1);
        } else {
          toInsert.push(row);
        }
      }

      let dryRunPreview: {
        date: string | null; description: string; amount: number; currency: string | null; mcc: string | null;
        wouldMergeWithExistingId: string | null; category: { id: string; source: string } | null;
      }[] = [];

      if (!isDryRun) {
        const { data: cardMap } = await supabase
          .from('ca_card_person_map')
          .select('card_issuer, card_identification, person_id')
          .eq('user_id', userId);

        let { data: nucleo } = await supabase
          .from('ca_people')
          .select('id')
          .eq('user_id', userId)
          .eq('name', 'NUCLEO')
          .maybeSingle();
        if (!nucleo) {
          const { data: createdNucleo } = await supabase
            .from('ca_people')
            .insert({ user_id: userId, name: 'NUCLEO', color: '#6B7280' })
            .select('id')
            .single();
          nucleo = createdNucleo;
        }
        const nucleoId = nucleo?.id || connection!.owner_person_id || null;

        const spenderFor = (t: (typeof rows)[number]) => {
          const mapping = t.cardIssuer && t.cardIdentification
            ? (cardMap || []).find((m) => m.card_issuer === t.cardIssuer && m.card_identification === t.cardIdentification)
            : null;
          return mapping ? mapping.person_id : nucleoId;
        };

        const newTxIds: string[] = [];

        if (toMerge.length) {
          const existingById = new Map([...unlinked, ...pendingSameConnection].map((c) => [c.id, c]));
          for (const { existingId, row } of toMerge) {
            const existing = existingById.get(existingId);
            const update: Record<string, unknown> = {
              bank_connection_id: bankConnectionId,
              external_id: row.externalId,
              raw: row.raw,
            };
            if (!existing?.mcc) update.mcc = row.mcc;
            if (!existing?.type) update.type = row.type;
            if (!existing?.spender_person_id) update.spender_person_id = spenderFor(row);
            const { error: updErr } = await supabase.from('ca_transactions').update(update).eq('id', existingId);
            if (!updErr) newTxIds.push(existingId);
          }
        }

        if (toInsert.length) {
          const insertRows = toInsert.map((t) => ({
            user_id: userId,
            date: t.date,
            amount: t.amount,
            currency: t.currency,
            description: t.description,
            type: t.type,
            spender_person_id: spenderFor(t),
            person_source: 'unassigned',
            bank_connection_id: bankConnectionId,
            external_id: t.externalId,
            mcc: t.mcc,
            import_source: 'bank_sync',
            raw: t.raw,
          }));
          const { data: insertedRows, error: insertError } = await supabase
            .from('ca_transactions')
            .upsert(insertRows, { onConflict: 'bank_connection_id,external_id', ignoreDuplicates: true })
            .select('id, description, mcc');
          if (insertError) throw new Error(insertError.message);
          importedCount = (insertedRows || []).length;
          newTxIds.push(...(insertedRows || []).map((t: any) => t.id));
        }

        // Categorizzazione deterministica (MCC → merchant appreso → regole) per tutte le
        // transazioni toccate in questo run che non hanno già una categoria.
        if (newTxIds.length) {
          const { data: existingCats } = await supabase
            .from('ca_transaction_categories')
            .select('transaction_id')
            .in('transaction_id', newTxIds);
          const alreadyCategorized = new Set((existingCats || []).map((c: any) => c.transaction_id));
          const toCategorize = newTxIds.filter((id) => !alreadyCategorized.has(id));
          if (toCategorize.length) {
            const { data: txsToCategorize } = await supabase
              .from('ca_transactions')
              .select('id, description, mcc')
              .in('id', toCategorize);
            const categoryRows: { transaction_id: string; category_id: string; source: string }[] = [];
            for (const t of txsToCategorize || []) {
              const { categoryId, source } = categorizeCategory(
                t.description, t.mcc, mccMap || [], merchantMap || [], merchantMapCategories || [], rules || []
              );
              if (categoryId && source) categoryRows.push({ transaction_id: t.id, category_id: categoryId, source });
            }
            if (categoryRows.length) {
              await supabase.from('ca_transaction_categories').insert(categoryRows);
            }
          }
        }
      } else {
        // Dry-run: nessuna scrittura, solo calcolo dell'anteprima (merge/insert + categoria
        // deterministica) per ogni transazione finta ricevuta.
        const preview = [
          ...toMerge.map((m) => ({ ...m.row, wouldMergeWithId: m.existingId as string | null })),
          ...toInsert.map((r) => ({ ...r, wouldMergeWithId: null as string | null })),
        ];
        dryRunPreview = preview.map((r) => {
          const { categoryId, source } = categorizeCategory(
            r.description, r.mcc, mccMap || [], merchantMap || [], merchantMapCategories || [], rules || []
          );
          return {
            date: r.date, description: r.description, amount: r.amount, currency: r.currency, mcc: r.mcc,
            wouldMergeWithExistingId: r.wouldMergeWithId,
            category: categoryId && source ? { id: categoryId, source } : null,
          };
        });
      }

      if (isDryRun) {
        // Niente scan aggiuntivo dell'intero storico (allTx/allCats — costoso su tabelle
        // grandi, è la causa più probabile dei timeout osservati in test): il conteggio
        // "no-categoria" globale viene mostrato dalla dashboard di cost-analysis.html, qui
        // basta la statistica sul solo batch di test.
        const newWithoutCategory = dryRunPreview.filter((p) => !p.wouldMergeWithExistingId && !p.category).length;

        return new Response(JSON.stringify({
          mode: 'dry_run',
          note: 'Nessuna scrittura eseguita: transazioni non salvate, notifica non accodata. Solo simulazione sul batch di test.',
          receivedCount: rows.length,
          wouldMergeCount: dryRunPreview.filter((p) => p.wouldMergeWithExistingId).length,
          wouldInsertCount: dryRunPreview.filter((p) => !p.wouldMergeWithExistingId).length,
          uncategorizedInBatch: newWithoutCategory,
          transactions: dryRunPreview,
        }), { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
      }
    } else if (isDryRun) {
      // Nessuna transazione finta valida (filtrata da .filter(date && externalId)).
      return new Response(JSON.stringify({
        mode: 'dry_run',
        note: 'Nessuna transazione valida in test_transactions (servono almeno booking_date/date e entry_reference/transaction_id).',
        receivedCount: 0,
        transactions: [],
      }), { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    }

    // Conteggio transazioni ancora senza categoria (stessa definizione del contatore
    // dashboard in cost-analysis.html): tutte le transazioni dell'utente, non solo quelle
    // toccate in questo run.
    const allTx = await fetchAllRows((from, to) =>
      supabase.from('ca_transactions').select('id').eq('user_id', userId).range(from, to)
    );
    const allCats = await fetchAllRows((from, to) =>
      supabase.from('ca_transaction_categories').select('transaction_id').range(from, to)
    );
    const categorizedIds = new Set(allCats.map((c: any) => c.transaction_id));
    const uncategorizedIds = allTx.filter((t: any) => !categorizedIds.has(t.id)).map((t: any) => t.id as string);
    const uncategorizedCount = uncategorizedIds.length;

    if (uncategorizedCount > 0) {
      // Un solo utente ha app='cost_analysis'+channel='smart_block': niente filtro su
      // entity_id (è uuid in produzione, non una stringa libera come 'revolut-sync').
      const { data: rule } = await supabase
        .from('cm_notification_rules')
        .select('id')
        .eq('user_id', userId)
        .eq('app', 'cost_analysis')
        .eq('channel', 'smart_block')
        .maybeSingle();
      if (!rule) {
        console.error('[revolut-auto-categorize] riga ancora cm_notification_rules (app=cost_analysis) non trovata — notifica non inviata');
      } else {
        // Le righe già pending vanno lette CON il metadata, non solo contate: i destinatari sono
        // due (vedi sotto) e si distinguono unicamente per metadata.device_token. Un semplice
        // "esiste una pending su questa regola" bloccherebbe la notifica di Teresa ogni volta che
        // Salvatore non ha ancora smaltito la sua, e viceversa.
        const { data: pendingRows } = await supabase
          .from('cm_notification_queue')
          .select('id, metadata')
          .eq('rule_id', rule.id)
          .eq('status', 'pending');
        const pendingTokens = new Set(
          (pendingRows || []).map((r: any) => (r.metadata?.device_token as string) || '')
        );

        // Senza metadata.device_token l'app Android (SupabaseApi.queryQueue → myToken) non
        // considera mai questa riga "sua" e il blocco non compare mai sul telefono — stesso
        // campo che fill-notification-queue popola da cm_user_notification_settings.
        const { data: notifSettings } = await supabase
          .from('cm_user_notification_settings')
          .select('smart_block_device_token')
          .eq('user_id', userId)
          .maybeSingle();
        const mainDeviceToken = notifSettings?.smart_block_device_token || null;

        // Nome della persona per il campo `spender` del payload (v1.4) e, per il telefono di
        // Teresa, filtro delle transazioni. spender_person_id può essere nullo (transazione non
        // ancora attribuita a nessuno) e in quel caso `spender` resta null.
        const { data: peopleRows } = await supabase
          .from('ca_people')
          .select('id, name')
          .eq('user_id', userId);
        const people = peopleRows || [];
        const personName = new Map<string, string>(
          people.map((p: any) => [p.id as string, p.name as string] as [string, string])
        );

        // parent_id incluso per raggruppare categorie/sottocategorie nel picker Android come
        // in cost-analysis.html (renderTxCategorySuggestions): principali in grassetto, figlie
        // indentate subito sotto, entrambe selezionabili. La cascata principale → sotto-categorie
        // di visible_in_app avviene lato client al salvataggio (cost-analysis.html saveCategory,
        // che propaga il valore a tutte le figlie): qui basta filtrare sul valore proprio di
        // ciascuna riga, senza ricalcolare nulla rispetto al padre.
        const { data: visibleUserCategories } = await supabase
          .from('ca_categories')
          .select('id, name, icon, parent_id')
          .eq('user_id', userId)
          .eq('visible_in_app', true)
          .order('name');
        const visibleCategories = visibleUserCategories || [];

        // ── Destinatari ──────────────────────────────────────────────────────────────────
        // 1) Salvatore: tutte le transazioni senza categoria (comportamento invariato).
        // 2) Teresa: solo quelle fatte con la SUA carta, cioè con spender_person_id uguale alla
        //    sua riga di ca_people — l'attribuzione la fa enable-banking-sync tramite
        //    ca_card_person_map (carta → persona), qui non si reinterpreta nulla.
        // Le transazioni di Teresa restano anche nella notifica di Salvatore: se lei non
        // risponde, lui può comunque chiudere il lavoro. La sovrapposizione è voluta e non fa
        // danni: ca_smart_block_set_category riscrive la categoria (delete + insert), quindi se
        // la stessa transazione viene categorizzata da tutti e due vince l'ultimo che tocca.
        // Una riga già accodata non si aggiorna da sola: la transazione sparisce dalla lista
        // dell'altro solo alla notifica successiva, quando non risulta più senza categoria.
        const teresaPerson = people.find(
          (p: any) => String(p.name || '').trim().toLowerCase() === TERESA_PERSON_NAME
        );
        let teresaUncategorizedIds: string[] = [];
        if (teresaPerson) {
          const teresaTx = await fetchAllRows((from, to) =>
            supabase
              .from('ca_transactions')
              .select('id')
              .eq('user_id', userId)
              .eq('spender_person_id', teresaPerson.id)
              .range(from, to)
          );
          const teresaIds = new Set(teresaTx.map((t: any) => t.id as string));
          teresaUncategorizedIds = uncategorizedIds.filter((id) => teresaIds.has(id));
        } else {
          console.warn(
            `[revolut-auto-categorize] nessuna persona "${TERESA_PERSON_NAME}" in ca_people — notifica del suo telefono saltata`
          );
        }

        const recipients = [
          {
            who: 'salvatore',
            token: mainDeviceToken,
            ids: uncategorizedIds,
            title: (n: number) => `${n} transazion${n === 1 ? 'e' : 'i'} Revolut da categorizzare`,
            body: (n: number) =>
              `Dopo il sync automatico restano ${n} transazioni senza categoria. Apri Analisi Costi per completarle.`,
          },
          {
            who: 'teresa',
            token: TERESA_DEVICE_TOKEN,
            ids: teresaUncategorizedIds,
            title: (n: number) => `${n} pagament${n === 1 ? 'o' : 'i'} con la tua carta da categorizzare`,
            body: (n: number) =>
              `${n === 1 ? 'È arrivato 1 pagamento Revolut fatto' : `Sono arrivati ${n} pagamenti Revolut fatti`} con la tua carta senza categoria. Scegli la categoria direttamente da qui.`,
          },
        ];

        for (const r of recipients) {
          if (!r.ids.length) continue;
          if (!r.token) {
            console.error(`[revolut-auto-categorize] device token mancante per ${r.who} — notifica non accodata`);
            continue;
          }
          if (pendingTokens.has(r.token)) {
            console.log(`[revolut-auto-categorize] notifica già pending per ${r.who}, non ne accodo un'altra`);
            continue;
          }

          // Payload per la categorizzazione interattiva dalla schermata di blocco (Android,
          // BlockWindowManager): fino a MAX_EMBEDDED transazioni ancora senza categoria +
          // l'elenco delle categorie dell'utente, così l'app può proporre una lista da toccare
          // senza dover fare altre query. Se l'utente ne categorizza solo una parte, l'app
          // aggiorna direttamente metadata.cost_analysis.transactions (PATCH) togliendo quelle
          // già fatte — questa insert scrive solo lo stato iniziale.
          const MAX_EMBEDDED = 20;
          const { data: txRows } = await supabase
            .from('ca_transactions')
            .select('id, description, amount, currency, date, spender_person_id, raw')
            .in('id', r.ids.slice(0, MAX_EMBEDDED));
          // `raw` serve solo a ricavare l'orario e NON finisce nel payload: è il JSON grezzo
          // della banca (o l'intera riga CSV), pesante e inutile all'app.
          const embeddedTx = (txRows || []).map((t: any) => ({
            id: t.id,
            description: t.description,
            amount: t.amount,
            currency: t.currency,
            date: t.date,
            time: extractPaymentTime(t.raw),
            spender: t.spender_person_id ? (personName.get(t.spender_person_id) || null) : null,
          }));

          const count = r.ids.length;
          await supabase.from('cm_notification_queue').insert({
            rule_id: rule.id,
            user_id: userId,
            app: 'cost_analysis',
            entity_id: rule.id, // entity_id è uuid: riusa l'id della regola stessa, non serve altro
            title: r.title(count),
            body: r.body(count),
            channel: 'smart_block',
            fire_at: new Date().toISOString(),
            status: 'pending',
            metadata: {
              device_token: r.token,
              cost_analysis: { transactions: embeddedTx, categories: visibleCategories },
            },
          });
          console.log(`[revolut-auto-categorize] notifica accodata per ${r.who}: ${count} transazioni`);
        }
      }
    }

    if (syncLog) {
      await supabase.from('ca_sync_log').update({
        finished_at: new Date().toISOString(),
        status: 'success',
        imported_count: importedCount,
      }).eq('id', syncLog.id);
    }

    return new Response(JSON.stringify({
      imported: importedCount,
      uncategorized: uncategorizedCount,
      totalFetched: rawTransactions.length,
      pages: pageCount,
    }), { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
  } catch (e) {
    if (syncLog) {
      await supabase.from('ca_sync_log').update({
        finished_at: new Date().toISOString(),
        status: 'error',
        error_message: (e as Error).message,
      }).eq('id', syncLog.id);
    }
    return new Response(
      JSON.stringify({ error: { message: (e as Error).message } }),
      { status: 502, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
