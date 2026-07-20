// Supabase Edge Function: scarica tutte le transazioni (con paginazione) per un conto
// collegato via Enable Banking (GET /accounts/{id}/transactions), le deduplica per
// external_id, applica la categorizzazione MCC (deterministica — merchant appreso/regole/AI
// restano al client, applicati dopo ogni sync) e le inserisce con spender_person_id attribuito
// in base alla carta usata (ca_card_person_map), con fallback alla persona "NUCLEO" per le
// transazioni senza carta riconosciuta o con carta non ancora assegnata.
//
// NOTA: i nomi esatti dei campi nella risposta di /transactions non sono stati verificati con
// una chiamata reale prima del primo sync effettivo — l'estrazione prova più varianti di nome
// campo comuni allo standard Berlin Group NextGenPSD2 su cui si basa Enable Banking, e la
// riga JSON originale viene comunque salvata per intero in ca_transactions.raw così nulla va
// perso anche se un campo non viene riconosciuto correttamente al primo giro.
//
// Richiede i Supabase Secrets: ENABLE_BANKING_APP_ID, ENABLE_BANKING_PRIVATE_KEY.
// v1 — 2026-07-18 · v2 — 2026-07-19: paginazione completa + attribuzione spender per carta

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

    // Recupera TUTTO lo storico disponibile seguendo la paginazione (continuation_key) —
    // senza questo la sync prendeva solo la prima pagina di risultati, perdendo silenziosamente
    // le transazioni più vecchie quando il conto ne ha più di quante ne stiano in una pagina.
    // MAX_PAGES è un tetto di sicurezza contro loop infiniti in caso di risposta anomala.
    const MAX_PAGES = 50;
    const rawTransactions: unknown[] = [];
    let continuationKey: string | null = null;
    let pageCount = 0;
    do {
      const url = new URL(`${ENABLE_BANKING_API_BASE}/accounts/${connection.account_id}/transactions`);
      if (continuationKey) url.searchParams.set('continuation_key', continuationKey);
      const txRes = await fetch(url.toString(), {
        headers: { Authorization: 'Bearer ' + jwt },
      });
      const txData = await txRes.json();
      if (!txRes.ok) {
        throw new Error('Errore Enable Banking: ' + (txData?.message || txRes.status));
      }
      const page: unknown[] = Array.isArray(txData.transactions) ? txData.transactions : [];
      rawTransactions.push(...page);
      continuationKey = txData.continuation_key || null;
      pageCount++;
    } while (continuationKey && pageCount < MAX_PAGES);

    const { data: mccMap } = await supabase
      .from('ca_mcc_category_map')
      .select('mcc, category_id')
      .eq('user_id', userId);

    const rows = rawTransactions.map((tx: any) => {
      const amountRaw = pick(tx, 'transaction_amount', 'transactionAmount') as any;
      const absAmount = Math.abs(parseFloat(pick(amountRaw, 'amount') as string) || 0);
      // Enable Banking restituisce sempre l'importo assoluto, con la direzione a parte in
      // credit_debit_indicator (DBIT = uscita, CRDT = entrata). Il resto dell'app usa il segno
      // per distinguere spese/entrate (amount < 0 = spesa), quindi va applicato qui.
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
      const remittanceText = Array.isArray(remittanceInfo)
        ? remittanceInfo.join(' ')
        : ((remittanceInfo as string) || '');
      const creditor = pick(tx, 'creditor') as any;
      const debtor = pick(tx, 'debtor') as any;
      const creditorName = (creditor && (pick(creditor, 'name') as string)) || '';
      const debtorName = (debtor && (pick(debtor, 'name') as string)) || '';
      const description = remittanceText || creditorName || debtorName || '';
      // Per confrontare con la descrizione del CSV Revolut (colonna "Description"): corrisponde
      // al secondo elemento di remittance_information quando ce ne sono almeno due (es. bonifici/
      // ricariche: ["nota utente","Payment from ..."]), altrimenti al primo (es. pagamenti carta,
      // dove c'è un solo elemento con il nome del merchant).
      const matchDescription = Array.isArray(remittanceInfo) && remittanceInfo.length > 1
        ? String(remittanceInfo[1] || '')
        : (Array.isArray(remittanceInfo) && remittanceInfo.length ? String(remittanceInfo[0] || '') : description);
      const date = (pick(tx, 'booking_date', 'bookingDate', 'value_date', 'valueDate') as string) || null;
      const externalId = (pick(tx, 'entry_reference', 'entryReference', 'transaction_id', 'transactionId') as string) || null;
      const mcc = (pick(tx, 'merchant_category_code', 'merchantCategoryCode', 'mcc') as string) || null;
      const bankTxCode = pick(tx, 'bank_transaction_code', 'bankTransactionCode') as any;
      const type = (bankTxCode && (pick(bankTxCode, 'code') as string)) || null;
      // Carta usata (issuer + ultime cifre), quando presente — usata per attribuire "chi ha
      // speso" in automatico (vedi ca_card_person_map), utile sui conti condivisi dove ogni
      // intestatario ha la propria carta.
      const cardIdList = pick(tx, 'debtor_account_additional_identification', 'debtorAccountAdditionalIdentification') as any[];
      const card = Array.isArray(cardIdList) && cardIdList.length ? cardIdList[0] : null;
      const cardIssuer = (card && (pick(card, 'issuer') as string)) || null;
      const cardIdentification = (card && (pick(card, 'identification') as string)) || null;
      return { date, amount, currency, description, matchDescription, externalId, mcc, type, cardIssuer, cardIdentification, raw: tx };
    }).filter((t) => t.date && t.externalId);

    if (!rows.length) {
      await supabase.from('ca_sync_log').update({ finished_at: new Date().toISOString(), status: 'success', imported_count: 0 }).eq('id', syncLog?.id);
      return new Response(JSON.stringify({ imported: 0 }), { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
    }

    const { data: cardMap } = await supabase
      .from('ca_card_person_map')
      .select('card_issuer, card_identification, person_id')
      .eq('user_id', userId);

    // "Chi ha speso" è attribuito in base alla carta usata (ca_card_person_map). Le
    // transazioni senza carta riconosciuta o con una carta non ancora assegnata a nessuno
    // vanno alla persona "NUCLEO" (creata al bisogno) invece che al proprietario fisso del
    // conto — utile sui conti condivisi dove più persone usano carte diverse sullo stesso IBAN.
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
    const nucleoId = nucleo?.id || connection.owner_person_id || null;

    const spenderFor = (t: (typeof rows)[number]) => {
      const mapping = t.cardIssuer && t.cardIdentification
        ? (cardMap || []).find((m) => m.card_issuer === t.cardIssuer && m.card_identification === t.cardIdentification)
        : null;
      return mapping ? mapping.person_id : nucleoId;
    };

    // Evita duplicati quando la stessa transazione è già presente perché importata a mano da
    // CSV prima del collegamento bancario: cerca tra le transazioni non ancora collegate a un
    // conto (bank_connection_id nullo) una corrispondenza su data+importo (con tolleranza sui
    // decimali, per evitare mismatch da arrotondamento float/numeric). Se ambigua (più di una
    // corrispondenza), si prova a stringere sulla descrizione (colonna CSV "Description" contro
    // il secondo elemento di remittance_information). Se trovata un'unica corrispondenza,
    // AGGIORNA quella riga esistente (aggiunge conto/MCC/carta/dato grezzo) invece di inserirne
    // una nuova, senza toccare categorie o persona già assegnate a mano. Corrispondenze multiple
    // o assenti finiscono nel normale inserimento, per non rischiare un collegamento sbagliato.
    const AMOUNT_EPSILON = 0.01;
    const { data: unlinked } = await supabase
      .from('ca_transactions')
      .select('id, date, amount, mcc, type, spender_person_id, description')
      .eq('user_id', userId)
      .is('bank_connection_id', null);

    const candidatePool = [...(unlinked || [])];
    const toInsert: typeof rows = [];
    const toMerge: { existingId: string; row: (typeof rows)[number] }[] = [];
    for (const row of rows) {
      let matches = candidatePool.filter((c) => c.date === row.date && Math.abs(Number(c.amount) - row.amount) < AMOUNT_EPSILON);
      if (matches.length > 1) {
        const desc = (row.matchDescription || '').trim().toLowerCase();
        const narrowed = matches.filter((c) => (c.description || '').trim().toLowerCase() === desc);
        if (narrowed.length === 1) matches = narrowed;
      }
      if (matches.length === 1) {
        toMerge.push({ existingId: matches[0].id, row });
        candidatePool.splice(candidatePool.indexOf(matches[0]), 1);
      } else {
        toInsert.push(row);
      }
    }

    const categoryRows: { transaction_id: string; category_id: string; source: string }[] = [];

    let mergedCount = 0;
    if (toMerge.length) {
      const existingById = new Map((unlinked || []).map((c) => [c.id, c]));
      const mergedIds: string[] = [];
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
        if (!updErr) { mergedIds.push(existingId); mergedCount++; }
      }
      if (mergedIds.length) {
        const { data: existingCats } = await supabase
          .from('ca_transaction_categories')
          .select('transaction_id')
          .in('transaction_id', mergedIds);
        const alreadyCategorized = new Set((existingCats || []).map((c) => c.transaction_id));
        for (const { existingId, row } of toMerge) {
          if (alreadyCategorized.has(existingId)) continue;
          const match = (mccMap || []).find((m) => m.mcc === row.mcc);
          if (match) categoryRows.push({ transaction_id: existingId, category_id: match.category_id, source: 'mcc' });
        }
      }
    }

    let inserted: { id: string; mcc: string | null }[] = [];
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
        .select('id, mcc');
      if (insertError) throw new Error(insertError.message);
      inserted = insertedRows || [];

      // Categorizzazione MCC (deterministica) per le sole transazioni appena inserite
      for (const t of inserted) {
        const match = (mccMap || []).find((m) => m.mcc === t.mcc);
        if (match) categoryRows.push({ transaction_id: t.id, category_id: match.category_id, source: 'mcc' });
      }
    }

    if (categoryRows.length) {
      await supabase.from('ca_transaction_categories').insert(categoryRows);
    }

    const oldestDate = rows.reduce((min, t) => (!min || t.date < min ? t.date : min), null as string | null);

    await supabase.from('ca_sync_log').update({
      finished_at: new Date().toISOString(),
      status: 'success',
      imported_count: inserted.length,
    }).eq('id', syncLog?.id);

    return new Response(JSON.stringify({
      imported: inserted.length,
      merged: mergedCount,
      totalFetched: rawTransactions.length,
      pages: pageCount,
      oldestDate,
    }), {
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
