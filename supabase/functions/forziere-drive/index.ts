// Supabase Edge Function: il ponte fra `forziere.html` e la cartella del Forziere su
// Google Drive. Crea la cartella, apre i caricamenti, restituisce i file, li cancella.
//
// ⚠️ QUESTA FUNZIONE NON VEDE MAI NIENTE IN CHIARO. Tutto quello che le passa davanti è
// già cifrato con OpenPGP dalle 24 parole, che stanno solo nella testa di Salvatore e non
// arrivano qui né adesso né mai. È il motivo per cui il trasferimento può passare da un
// server senza che l'end-to-end si rompa.
//
// ⚠️ PERCHÉ NON DAL BROWSER, come per `backup-drive`: servirebbe un token Google con lo
// scope `drive` chiesto al login di ogni app. Qui la credenziale è un refresh token nei
// Secrets, e la pagina presenta il suo JWT Supabase.
//
// ⚠️ CHI PUÒ CHIAMARLA. Il JWT si verifica **contro Supabase**, non si decodifica e basta:
// un JWT si scrive a mano in dieci secondi. Passa il solo FORZIERE_EMAIL — Teresa, Rosa e
// Ada hanno un login valido e qui non c'entrano niente.
//
// ⚠️ IL CARICAMENTO NON PASSA DI QUI. `upload-url` chiede a Google un indirizzo di
// caricamento ripristinabile e lo restituisce: i byte vanno dal browser a Google diretti.
// Bufferare un video in una Edge Function vorrebbe dire un tetto di dimensione scritto
// da nessuna parte, che si scopre il giorno che si supera. Lo scaricamento invece passa
// di qui — Drive non ha indirizzi firmati — ma **in flusso**, senza mettersi il file in
// pancia: `new Response(risposta.body)` lo lascia scorrere.
//
// Secrets: GDRIVE_CLIENT_ID, GDRIVE_CLIENT_SECRET, GDRIVE_REFRESH_TOKEN,
//          e FORZIERE_EMAIL (facoltativo). ⚠️ NON usa GDRIVE_FOLDER_ID: la cartella del
//          forziere se la crea da sé e non è quella dei backup.
// v1 — 2026-09-05

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!;
const EMAIL_OK = (Deno.env.get('FORZIERE_EMAIL') ?? 'garsal1971@gmail.com').toLowerCase();

const DRIVE = 'https://www.googleapis.com/drive/v3';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3';
const NOME_CARTELLA = 'Forziere AppSphere';

function risposta(corpo: unknown, stato = 200) {
  return new Response(JSON.stringify(corpo), {
    status: stato,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  });
}

// Il token di Drive dura un'ora e si tiene in memoria: rifarlo a ogni chiamata sarebbe un
// giro su oauth2.googleapis.com per ogni file caricato. Stessa scelta di `backup-drive`.
let tokenDrive: { valore: string; scade: number } | null = null;

async function accessoDrive(): Promise<string> {
  if (tokenDrive && tokenDrive.scade > Date.now() + 60_000) return tokenDrive.valore;

  const mancanti = ['GDRIVE_CLIENT_ID', 'GDRIVE_CLIENT_SECRET', 'GDRIVE_REFRESH_TOKEN']
    .filter((k) => !Deno.env.get(k));
  if (mancanti.length) throw new Error(`mancano i secret: ${mancanti.join(', ')}`);

  const r = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: Deno.env.get('GDRIVE_CLIENT_ID')!,
      client_secret: Deno.env.get('GDRIVE_CLIENT_SECRET')!,
      refresh_token: Deno.env.get('GDRIVE_REFRESH_TOKEN')!,
      grant_type: 'refresh_token',
    }),
  });
  const dati = await r.json();
  if (!r.ok || !dati.access_token) {
    throw new Error(`refresh token rifiutato: ${JSON.stringify(dati).slice(0, 200)}`);
  }
  tokenDrive = { valore: dati.access_token, scade: Date.now() + (dati.expires_in ?? 3600) * 1000 };
  return tokenDrive.valore;
}

async function chiChiama(req: Request): Promise<string | null> {
  const header = req.headers.get('Authorization') ?? '';
  if (!header.toLowerCase().startsWith('bearer ')) return null;
  const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { Authorization: header, apikey: Deno.env.get('SUPABASE_ANON_KEY') ?? '' },
  });
  if (!r.ok) return null;
  const u = await r.json();
  return (u?.email ?? '').toLowerCase() || null;
}

// La cartella se la crea da sé. Con lo scope `drive.file` l'applicazione vede soltanto ciò
// che ha creato lei, quindi cercarla per nome non può pescare una cartella altrui.
// ⚠️ Non è la cartella dei backup: quella contiene dump del database in chiaro-gzip, e
// mescolare le due cose vorrebbe dire che una rotazione sbagliata cancella il forziere.
async function cartella(token: string): Promise<string> {
  const q = new URLSearchParams({
    q: `name = '${NOME_CARTELLA}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false`,
    fields: 'files(id,name)',
    pageSize: '10',
  });
  const r = await fetch(`${DRIVE}/files?${q}`, { headers: { Authorization: `Bearer ${token}` } });
  if (r.ok) {
    const d = await r.json();
    if (d.files?.length) return d.files[0].id;
  }
  const c = await fetch(`${DRIVE}/files?fields=id`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: NOME_CARTELLA, mimeType: 'application/vnd.google-apps.folder' }),
  });
  if (!c.ok) throw new Error(`Drive mkdir: HTTP ${c.status} — ${(await c.text()).slice(0, 200)}`);
  return (await c.json()).id;
}

// ⚠️ Il file si tocca solo se sta in QUELLA cartella. Senza il controllo sul padre, un id
// qualsiasi raggiungerebbe qualunque file creato dall'applicazione — i backup compresi.
async function dentroLaCartella(token: string, id: string, padre: string) {
  const r = await fetch(`${DRIVE}/files/${encodeURIComponent(id)}?fields=id,name,parents,size`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!r.ok) throw new Error(`Drive get: HTTP ${r.status}`);
  const m = await r.json();
  if (!Array.isArray(m.parents) || !m.parents.includes(padre)) {
    throw new Error('quel file non sta nella cartella del forziere');
  }
  return m;
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });

  try {
    const email = await chiChiama(req);
    if (!email) return risposta({ ok: false, error: 'serve un login valido' }, 401);
    if (email !== EMAIL_OK) return risposta({ ok: false, error: 'non autorizzato' }, 403);

    const body = await req.json().catch(() => ({}));
    const azione = String(body.azione ?? '');
    const token = await accessoDrive();
    const padre = await cartella(token);

    // Dove sta il forziere. La pagina se lo scrive in `frz_vault.drive_folder_id`.
    if (azione === 'init') {
      return risposta({ ok: true, cartella: padre });
    }

    // Apre un caricamento: i byte poi vanno dal browser a Google, non da qui.
    if (azione === 'upload-url') {
      const nome = String(body.nome ?? '').trim();
      if (!nome) return risposta({ ok: false, error: "serve il nome del file" }, 400);
      const r = await fetch(`${UPLOAD}/files?uploadType=resumable&fields=id`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json; charset=UTF-8',
          // Google vuole sapere quanto arriverà, per poter riprendere un caricamento
          // interrotto invece di ricominciarlo.
          ...(body.bytes ? { 'X-Upload-Content-Length': String(body.bytes) } : {}),
          'X-Upload-Content-Type': 'application/octet-stream',
        },
        body: JSON.stringify({ name: nome, parents: [padre] }),
      });
      if (!r.ok) {
        return risposta({ ok: false, error: `Drive upload-url: HTTP ${r.status} — ${(await r.text()).slice(0, 200)}` }, 502);
      }
      const url = r.headers.get('Location');
      if (!url) return risposta({ ok: false, error: 'Google non ha dato l\'indirizzo di caricamento' }, 502);
      return risposta({ ok: true, url });
    }

    // Gli oggetti di servizio (indice.gpg, scorciatoia.gpg) sono poche centinaia di byte:
    // per loro un caricamento ripristinabile sarebbe tre viaggi di rete per niente.
    // Fa anche da RIPIEGO per i file veri quando il caricamento diretto a Google non
    // passa (una policy del browser, un proxy aziendale che rompe la richiesta PUT fra
    // domini): meglio un file che entra passando di qui che un forziere in cui non si
    // riesce a mettere niente.
    // ⚠️ Il tetto è scritto qui e non lasciato al caso: questi byte stanno tutti nella
    // memoria della funzione, e senza un limite esplicito il punto di rottura si
    // scoprirebbe il giorno che si carica il file sbagliato.
    if (azione === 'put') {
      const nome = String(body.nome ?? '').trim();
      const dati = String(body.base64 ?? '');
      if (!nome || !dati) return risposta({ ok: false, error: 'servono nome e contenuto' }, 400);
      if (dati.length > 12_000_000) {
        return risposta({ ok: false, error: 'troppo grande per «put»: serve «upload-url»' }, 400);
      }
      const bin = Uint8Array.from(atob(dati), (c) => c.charCodeAt(0));

      // Se esiste già lo si SOVRASCRIVE invece di crearne un secondo: due `indice.gpg`
      // nella stessa cartella e non si saprebbe più quale è quello buono.
      let id = String(body.id ?? '');
      if (id) { try { await dentroLaCartella(token, id, padre); } catch { id = ''; } }

      const meta = id ? {} : { name: nome, parents: [padre] };
      const confine = 'frz' + crypto.randomUUID().replace(/-/g, '');
      const testa = `--${confine}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(meta)}\r\n--${confine}\r\nContent-Type: application/octet-stream\r\n\r\n`;
      const coda = `\r\n--${confine}--\r\n`;
      const enc = new TextEncoder();
      const t = enc.encode(testa), c = enc.encode(coda);
      // ⚠️ Non `[...t, ...bin, ...c]`: lo spread di centinaia di migliaia di elementi in un
      // letterale d'array sfonda il limite degli argomenti e fallisce solo sui file grandi.
      const corpo = new Uint8Array(t.length + bin.length + c.length);
      corpo.set(t, 0); corpo.set(bin, t.length); corpo.set(c, t.length + bin.length);

      const r = await fetch(
        id ? `${UPLOAD}/files/${encodeURIComponent(id)}?uploadType=multipart&fields=id`
           : `${UPLOAD}/files?uploadType=multipart&fields=id`,
        {
          method: id ? 'PATCH' : 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': `multipart/related; boundary=${confine}` },
          body: corpo,
        },
      );
      if (!r.ok) return risposta({ ok: false, error: `Drive put: HTTP ${r.status} — ${(await r.text()).slice(0, 200)}` }, 502);
      return risposta({ ok: true, id: (await r.json()).id });
    }

    // Scaricamento IN FLUSSO: i byte attraversano la funzione senza fermarcisi dentro.
    if (azione === 'get') {
      const id = String(body.id ?? '');
      if (!id) return risposta({ ok: false, error: "serve l'id del file" }, 400);
      const m = await dentroLaCartella(token, id, padre);
      const bin = await fetch(`${DRIVE}/files/${encodeURIComponent(id)}?alt=media`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!bin.ok || !bin.body) {
        return risposta({ ok: false, error: `Drive download: HTTP ${bin.status}` }, 502);
      }
      return new Response(bin.body, {
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/octet-stream',
          ...(m.size ? { 'Content-Length': String(m.size) } : {}),
        },
      });
    }

    if (azione === 'delete') {
      const id = String(body.id ?? '');
      if (!id) return risposta({ ok: false, error: "serve l'id del file" }, 400);
      await dentroLaCartella(token, id, padre);
      const r = await fetch(`${DRIVE}/files/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!r.ok && r.status !== 404) {
        return risposta({ ok: false, error: `Drive delete: HTTP ${r.status}` }, 502);
      }
      return risposta({ ok: true });
    }

    // Serve a ritrovare gli oggetti di servizio quando la riga di `frz_vault` si è persa:
    // il forziere deve poter ripartire dal solo Drive.
    if (azione === 'list') {
      const file: Array<Record<string, unknown>> = [];
      let pagina: string | undefined;
      do {
        const q = new URLSearchParams({
          q: `'${padre}' in parents and trashed = false`,
          fields: 'nextPageToken, files(id,name,size,modifiedTime)',
          pageSize: '500',
        });
        if (pagina) q.set('pageToken', pagina);
        const r = await fetch(`${DRIVE}/files?${q}`, { headers: { Authorization: `Bearer ${token}` } });
        if (!r.ok) throw new Error(`Drive list: HTTP ${r.status}`);
        const d = await r.json();
        file.push(...(d.files ?? []));
        pagina = d.nextPageToken;
      } while (pagina);
      return risposta({ ok: true, cartella: padre, file });
    }

    return risposta({ ok: false, error: `azione sconosciuta: ${azione}` }, 400);
  } catch (e) {
    console.error('forziere-drive:', e);
    return risposta({ ok: false, error: String((e as Error)?.message ?? e) }, 500);
  }
});
