# CLAUDE.md — garsal-apps

This file provides context for AI assistants working in this repository.

---

## Project Overview

**garsal-apps** is a collection of personal productivity web applications deployed to Netlify. Each app is a **single self-contained HTML file** with no build step, no package manager, and no external source files. All styling and JavaScript live inline within the HTML.

The suite is branded **AppSphere** and the UI language is **Italian**.

---

## Utenti — applicazione familiare

**garsal-apps non è un prodotto multi-tenant generico: è un'applicazione familiare**, pensata per e usata esclusivamente da un piccolo gruppo di persone reali. Informazione informale, utile per capire richieste che parlano di persone specifiche per nome (es. notifiche solo per una persona, dati condivisi solo con alcuni):

| Persona | Ruolo |
|---|---|
| **Salvatore** | Utente principale / maggiore utilizzatore (account `garsal1971@gmail.com`, quello con cui interagisce Claude in questa repo) |
| **Teresa** | Condivide con Salvatore i dati di Finanza (`ta-firi`/finanza) e ora anche di Analisi Costi |
| **Rosa** | Condivide con Salvatore parte dei dati di Finanza |
| **Ada** | Solo utente di Analisi Costi (non ha accesso a Finanza) |

Questo elenco può cambiare nel tempo — se una richiesta menziona una persona non elencata qui, chiedere chiarimenti invece di assumere un ruolo.

---

## Client supportati

Le app sono progettate per funzionare su:

- **App Android** (`android-app/`) — WebView nativa con `AndroidBridge` JavascriptInterface. Supporta OCR via ML Kit, biometria, camera, condivisione immagini da altre app.
- **Browser desktop** (Chrome/Firefox/Safari su PC/Mac) — funzionalità complete incluso OCR via Tesseract.js.

**Non supportato**: browser mobile (Chrome/Safari su smartphone/tablet). Tesseract.js causa problemi di rete su WebView mobile e browser mobile in generale. Su mobile usare esclusivamente l'app Android.

---

## Repository Structure

```
garsal-apps/
├── index.html           # AppSphere — main entry point / app launcher (served at "/")
├── tasks.html           # Tasks v19.17.12 — task management
├── habit-tracker.html   # Habit Stack Tracker — habit tracking with gamification
├── events-log.html      # Events Log v2.0 — event/activity logging
├── weight-quest.html    # Weight Quest v2.4.1 — weight tracking with charts
└── netlify.toml         # Netlify deployment config
```

There is **no** `package.json`, `node_modules`, `build/`, or `dist/` directory. Every app ships as-is.

**Nota storica**: esisteva anche un `app-launcher.html`, copia quasi identica del launcher usata come `start_url` della PWA installabile. La PWA non è più in uso: il file è stato rimosso e `manifest.json` punta ora a `/` come ogni altro client (browser, app Android).

---

## Technology Stack

| Concern | Solution |
|---|---|
| Language | Vanilla HTML + CSS + JavaScript (ES2020+) |
| Backend / Auth | [Supabase](https://supabase.com) (PostgreSQL BaaS) |
| Deployment | Netlify (static hosting, no functions) |
| Charts | Chart.js v4.4.0 + chartjs-plugin-zoom (weight-quest only) |
| Touch gestures | Hammer.js (weight-quest only) |
| Fonts | Google Fonts — DM Sans / DM Mono (launcher), Space Mono / Darker Grotesque (other apps) |
| Supabase JS SDK | `@supabase/supabase-js@2` via jsDelivr CDN (most apps); weight-quest uses a custom minimal inline client |

---

## Architecture Patterns

### Single-file HTML apps
Each `.html` file is fully standalone: HTML structure, `<style>` CSS, and `<script>` JavaScript all in one file. There are no imports, no modules (except for the CDN scripts), and no transpilation.

### Supabase backend
All apps share **one Supabase project**:

```js
const SUPABASE_URL = 'https://jajlmmdsjlvzgcxiiypk.supabase.co';
const SUPABASE_KEY = '<anon public key>'; // safe to be public; RLS controls access
```

Apps use the Supabase JavaScript client initialised from the CDN:
```js
const sb = window.supabase.createClient(SUPABASE_URL, SUPABASE_KEY);
```

**weight-quest.html** is the exception — it ships its own minimal inline `SupabaseClient` class (no CDN dependency) and queries Google Fit directly via OAuth.

### Authentication flow
1. `index.html` handles Google OAuth via Supabase Auth
2. On successful login, tokens are stored in `sessionStorage`:
   - `sb_token` — Supabase JWT access token
   - `google_token` — raw Google OAuth provider token (for Google Fit API)
3. When the launcher opens a child app (`window.open`), it passes the Google token via `postMessage`:
   ```js
   win.postMessage({ type: 'GOOGLE_TOKEN', token: googleToken }, '*');
   ```
4. Child apps listen for `GOOGLE_TOKEN` messages and store the token in `localStorage` for persistence.

### Navigation pattern (Tasks, Habit Tracker, Events Log)
Apps use a **sidebar nav** with `data-section` buttons toggling `.page` sections:
```
sidebar nav-item (click) → showPage(sectionName) → hide all sections, show target
```
Layout: `grid-template-columns: 280px 1fr` — sidebar left, main content right. Responsive mobile menu via a hamburger toggle.

---

## Database Schema

Tables are namespaced by app prefix:

### Shared (`cm_`)
| Table | Purpose |
|---|---|
| `cm_apps` | App registry for the launcher (title, description, html_file, score_query, active) |
| `cm_categories` | Shared category taxonomy used by Tasks and Habit Tracker |
| `cm_bank_connections` | Conti collegati via Enable Banking (PSD2). `module` = `'cost_analysis'` (Spese Famiglia) o `'fondo'` (modulo Fondi) |
| `cm_sync_log` | Storico delle sincronizzazioni bancarie |

Le due tabelle bancarie nascono come `ca_bank_connections` / `ca_sync_log` (Analisi Costi) e
sono state rinominate quando i conti sono diventati condivisi con i Fondi
(`20260803120000_cm_bank_connections_and_fondo_sync.sql`). Restano due **viste di
compatibilità** con il vecchio nome, perché le Edge Function vengono ridistribuite solo quando
il commit le tocca: si possono eliminare quando nessun client usa più `ca_bank_connections`.

### Tasks (`ts_`)
| Table | Purpose |
|---|---|
| `ts_tasks` | All tasks |
| `ts_history` | Audit log of task state changes |
| `ts_priorities` | Configurable priority levels (read-only from tasks.html) |
| `ts_settings` | Key-value app settings |

**Task types** in `ts_tasks.type`:
- `single` — one-off task with a `start_date`
- `recurring` — repeats on a schedule with `next_occurrence_date`
- `simple_recurring` — simpler recurrence variant
- `multiple` — task with multiple scheduled dates
- `free_repeat` — repeatable without a fixed schedule
- `workflow` — multi-step workflow task

### Habit Tracker (`hb_`)
| Table | Purpose |
|---|---|
| `hb_habits` | Habit definitions |
| `hb_completions` | Daily completion records |
| `hb_user_points` | Gamification points balance |
| `hb_points_transactions` | Points ledger |
| `hb_archived_stacks` | Archived habit stacks |

### Events Log (`el_`)
| Table | Purpose |
|---|---|
| `el_groups` | Event category groups |
| `el_events` | Event definitions |
| `el_logs` | Event log entries |

### Fondi (`fnz_fund*`)
| Table | Purpose |
|---|---|
| `fnz_funds` | Fondi comuni; `bank_connection_id` = conto da cui importare i movimenti |
| `fnz_fund_participants` | Anagrafica partecipanti di un fondo: `name`, `iban` (chiave di match del sync), `active` |
| `fnz_fund_contributions` | Movimenti: importo **con segno** (`type` `'versamento'` > 0 / `'prelievo'` < 0), `status`, `participant_id`, `external_id` |
| `fnz_foi_index` | Indice ISTAT FOI (media annua) per la rivalutazione |

`fnz_fund_contributions.status` decide se il movimento conta: `auto` (abbinato dal sync) e
`confermato` entrano nelle quote, `da_rivedere` (controparte non riconosciuta) e `escluso` no.
`participant` (testo) resta popolato accanto a `participant_id`: i fondi nati prima
dell'anagrafica non hanno partecipanti anagrafici e continuano a funzionare così.

I movimenti del fondo possono arrivare da tre strade: inseriti a mano, importati dal conto
bancario via `enable-banking-fondo-sync`, oppure presi dai movimenti del Conto Risparmio già
in `cntrs_transactions_terr` (pulsante *⬇️ Importa dal Conto Risparmio*). In quest'ultimo caso
`external_id` vale `cntrs:<id della riga di origine>` e il partecipante si riconosce
verificando che il **suo IBAN compaia** nel testo del movimento (confronto senza spazi né
punteggiatura), col nome come ripiego — nessuna estrazione dell'IBAN con regex dalla causale.

`amount_adjusted` è una **cache** della rivalutazione, riempita da
`fnz_fund_recalc_adjusted(p_fund_id)`; il valore mostrato a schermo lo ricalcola comunque
`computeFundShares()` in `finanza.html`. Chiamare la RPC dopo ogni scrittura sui movimenti o
sull'indice FOI, altrimenti la colonna resta indietro rispetto a quello che si vede nell'app.

### Weight Quest (`ps_`)
| Table | Purpose |
|---|---|
| `ps_weight_tracking` | Weight measurement entries |

### Spuntiamola (`sp_`)
| Table | Purpose |
|---|---|
| `sp_settings` | Una riga per utente: traguardo, emoji, periodo (`start_date` → `end_date`), `skip_weekend` |
| `sp_checks` | Una riga per giorno spuntato (`UNIQUE (user_id, day)`); `emoji` è quella pescata a caso alla spunta |
| `sp_key_days` | Giornate chiave (`UNIQUE (user_id, day)`); `label` è l'etichetta libera mostrata alla spunta |

### Obiettivi (`ob_`)
| Table | Purpose |
|---|---|
| `ob_objectives` | Obiettivi, gerarchia a due livelli (`annual` → `quarterly`) via `parent_id` |
| `ob_metrics` | Metriche di un obiettivo (1..N) |
| `ob_rubric_criteria` | Criteri 1-5 delle metriche `kind='rubric'` |
| `ob_measurements` | Rilevazioni; `detail` jsonb contiene i punteggi per criterio |
| `ob_milestones` | Curva attesa (`expected_value` per data) — base del semaforo |
| `ob_task_links` | Collegamento a `ts_tasks` / `hb_habits` (le azioni restano nelle loro app) |

`ob_metrics.role` distingue **`primary`** (il risultato, alimenta la barra e il semaforo — **una sola per
obiettivo**, vincolo `uq_ob_metrics_one_primary`), **`control`** (secondo riscontro lagging) e **`leading`**
(lo sforzo). `ob_metrics.kind` vale `state` | `cumulative` | `checklist` | `rubric`.

`ob_metrics.protocol` descrive **come** si misura e viene riproposto a ogni rilevazione: se cambia il
protocollo la serie storica non è più confrontabile.

---

## Funzioni RPC Supabase — Task lifecycle

Le operazioni sul ciclo di vita dei task (complete, skip, fail) sono implementate come funzioni PostgreSQL `SECURITY DEFINER` nel DB. **Il client JavaScript deve sempre delegare a queste RPC e non reimplementare mai la logica lato client.**

### Regola fondamentale

> Tutta la logica di transizione di stato dei task (calcolo prossima occorrenza, aggiornamento `ts_tasks`, inserimento in `ts_history`, aggiornamento/eliminazione `cm_notification_rules`) vive esclusivamente nelle RPC server-side. Il JS chiama la RPC e poi ricarica i dati.

### Funzioni disponibili

| Funzione | File migration | Parametri | Descrizione |
|---|---|---|---|
| `task_complete` | `20260518100000_task_complete.sql` | `p_task_id uuid, p_today date` | Completa un task |
| `task_skip` | `20260619100000_task_skip.sql` | `p_task_id uuid, p_days integer DEFAULT 1` | Salta un task alla prossima occorrenza |
| `task_fail` | `20260619110000_task_fail.sql` | `p_task_id uuid` | Segna un task come fallito |
| `task_next_recurring_date` | `20260520110000_fix_task_next_recurring_date.sql` | `p_task ts_tasks, p_base date` | Calcola la prossima data per task `recurring` |

Stessa regola per **Obiettivi**: il calcolo del progresso e la media pesata della rubrica vivono nelle RPC
`ob_objective_progress` / `ob_record_measurement` (`20260727100000_ob_objectives_tables.sql`), non nel JS.
`ob_metric_current` è invece volutamente `SECURITY INVOKER`: riceve una riga `ob_metrics` dal chiamante,
quindi la RLS su `ob_measurements` deve restare attiva.

Tutte le funzioni restituiscono `jsonb` con la struttura:
```json
{ "ok": true, "action": "completed|skipped|failed", "points": 10, "type": "single", "next": "<timestamptz>" }
```
In caso di errore: `{ "ok": false, "error": "messaggio" }`.

### Comportamento per tipo di task

#### `task_complete`
| Tipo | Comportamento |
|---|---|
| `single` | status → `terminated`, inserisce record `terminated` in history, elimina notification rules |
| `simple_recurring` | next = current + `repeat_after_days`, status → `completed`, aggiorna notification |
| `recurring` | chiama `task_next_recurring_date()`; se null → `terminated`; altrimenti → `completed` + aggiorna notification |
| `multiple` | trova prossima data in `multiple_dates[]`; se esiste → `completed`; altrimenti → `terminated` + elimina notification |
| `workflow` | controlla tutti gli step; se tutti done → `terminated`; se parziale → risponde senza modificare status |
| `free_repeat` | status → `completed`, aggiorna `last_completed_date`, nessuna prossima occorrenza |

#### `task_skip`
`p_days` è usato solo per il tipo `single` (quanti giorni spostare). Per tutti gli altri tipi viene ignorato.

| Tipo | Comportamento |
|---|---|
| `single` | next = current + `p_days`, status → `skipped`, aggiorna notification |
| `simple_recurring` | next = current + `repeat_after_days`, status → `skipped`, aggiorna notification |
| `recurring` | chiama `task_next_recurring_date()`; se null → errore; altrimenti status → `skipped` + aggiorna notification |
| `multiple` | trova prossima data in `multiple_dates[]`; se esiste → `skipped`; se era l'ultima → `terminated` + elimina notification |
| `free_repeat` | restituisce errore (non supporta skip) |

#### `task_fail`
| Tipo | Comportamento |
|---|---|
| `single` | status → `terminated`, inserisce record `terminated` in history, elimina notification rules |
| `simple_recurring` | next = current + `repeat_after_days`, status → `failed`, aggiorna notification |
| `recurring` | chiama `task_next_recurring_date()`; se null → `terminated`; altrimenti → `failed` + aggiorna notification |
| `multiple` | trova prossima data in `multiple_dates[]`; se esiste → `failed`; se era l'ultima → `terminated` + elimina notification |

### Pattern JS corretto (tasks.html)

```js
// CORRETTO — delega tutto al server
const { data: result, error } = await sb.rpc('task_skip', { p_task_id: id, p_days: 3 });
if (error || !result?.ok) { alert('Errore: ' + (error?.message || result?.error)); return; }
await loadTasks();
await loadHistory();
if (result.next) await updateSmartBlockFireAt(id, new Date(result.next).toISOString());

// SBAGLIATO — non calcolare mai la prossima data lato JS
// const nextDate = new Date(task.next_occurrence_date);
// nextDate.setDate(nextDate.getDate() + task.repeat_after_days);  // ← da non fare
```

### Dettaglio tecnico: estrazione data da `next_occurrence_date` per tipo `multiple`

Nelle RPC che gestiscono il tipo `multiple`, la data corrente viene estratta così:

```sql
-- CORRETTO
v_cur_str := COALESCE(v_task.next_occurrence_date::date::text, '');
-- → '2026-06-19'  ✓ confrontabile con multiple_dates[]

-- SBAGLIATO (non usare)
v_cur_str := split_part(v_task.next_occurrence_date::text, 'T', 1);
-- PostgreSQL formatta timestamptz come '2026-06-19 08:00:00+00' (spazio, non 'T')
-- → split_part restituisce l'intera stringa → confronto con 'YYYY-MM-DD' fallisce sempre
-- → v_cur_idx rimane NULL → il task viene terminato alla prima occorrenza (bug critico)
```

### Dettaglio tecnico: `v_time_of_day`

Tutte le RPC preservano l'orario originale del task quando calcolano la prossima occorrenza:
```sql
v_time_of_day := COALESCE(v_task.start_date, now())
                 - date_trunc('day', COALESCE(v_task.start_date, now()));
-- poi: v_next_ts := v_next_date::timestamptz + v_time_of_day;
```
Questo garantisce che un task impostato alle 09:00 rimanga alle 09:00 su ogni occorrenza successiva.

### Aggiornamento migration

Le migration vengono applicate **automaticamente** al push su `claude/**` tramite `.github/workflows/deploy.yml` (step `Apply Supabase migrations` → `supabase db push`). Non è necessaria nessuna azione manuale.

---

## Edge Functions e job schedulati

Le Edge Functions stanno in `supabase/functions/<nome>/index.ts` (Deno + TypeScript) e vengono
deployate **automaticamente** al push su `claude/**`: il workflow deploya solo le funzioni toccate
dal commit. I job periodici sono `pg_cron` + `net.http_post`, creati da migration con la service
role key letta dal vault (vedi `20260724320000_ca_revolut_auto_categorize_cron.sql` come modello).

| Funzione | Job | Cosa fa |
|---|---|---|
| `get-prices` | orario | Aggiorna `fnz_price_cache` (un trigger propaga su `fnz_price_history`). Una fonte diversa per tipo: BTPi→SoldiOnline, BTP→rendimentibtp, ETF→JustETF/Yahoo/Investing, crypto→CoinGecko in batch + Coinbase come ripiego, azioni→TwelveData/GoogleFinance |
| `enable-banking-connect` / `-callback` / `-aspsps` / `-refresh-accounts` | — | Collegamento di un conto: catalogo banche, avvio del consenso, redirect di ritorno e rilettura dei conti di una sessione già ottenuta. Il `module` passato a `connect` viaggia nello state e decide dove riporta il callback |
| `enable-banking-sync` | manuale (da `cost-analysis.html`) | Importa le transazioni di un conto in `ca_transactions` (Spese Famiglia) |
| `enable-banking-fondo-sync` | manuale (da `finanza.html`, scheda fondo) | Importa i bonifici di un conto in `fnz_fund_contributions`: CRDT → versamento (controparte = debtor), DBIT → prelievo (controparte = creditor), match su IBAN e poi su nome; senza match la riga entra come `da_rivedere` |
| `save-snapshot` | `fnz-save-snapshot`, 21:00 UTC | Chiama `get-prices`, poi calcola e salva lo snapshot del patrimonio in `fnz_dashboard_snapshots` per ogni utente che ha dati di Finanza |

### ⚠️ Header PSU obbligatori per alcune banche

Il catalogo `/aspsps` di Enable Banking dichiara per ogni banca un `required_psu_headers`:
**UniCredit (IT) richiede `psu-ip-address`**, Revolut no. Mandarlo è comunque doveroso — è la
banca a dichiararlo obbligatorio — ma **non è la causa del consenso vuoto**: l'header viene
inviato e la sessione UniCredit torna lo stesso senza conti (vedi *Consenso vuoto* qui sotto).
Tutte le chiamate a Enable Banking passano
quindi gli header PSU ricavati da `x-forwarded-for` (`psuHeaders()`, duplicata in ogni Edge
Function). Un job schedulato non ha un utente davanti: in quel caso l'header non c'è e non va
inventato.

Stessa scheda di catalogo: `maximum_consent_validity` (180 giorni per UniCredit) e
`auth_methods` per `psu_type`. Il pulsante *ℹ️ Cosa richiede questa banca* in `finanza.html`
la mostra per intero — è il primo posto da guardare quando un collegamento riesce a metà.

### Consenso vuoto: cosa è stato chiesto vs cosa è tornato

Una sessione `AUTHORIZED` con `access.accounts: null` e zero conti ha **due cause diverse** che
si assomigliano: l'IBAN non è stato spedito (casella *«la mia banca non lo richiede»*), oppure
è stato spedito e la banca non l'ha applicato. La sessione mostra solo il risultato, non la
richiesta, quindi dedurlo da lì è tirare a indovinare.

⚠️ **Con UniCredit (IT) l'elenco in `access.accounts` non viene applicato.** Le richieste del
5 e del 6 agosto 2026 sono partite con `accounts: [{iban}]`, `psu-ip-address` inviato e client
aggiornato, e la sessione è tornata `AUTHORIZED` con `access.accounts: null`, `accounts: []`,
`accounts_data: []`. Quindi **né l'IBAN mancante né l'header PSU spiegano il consenso vuoto**:
sono due ipotesi già bruciate, non ripercorrerle. Restano da verificare il **tipo di conto**
(PSD2 copre i conti di pagamento — un conto risparmio o un deposito la banca può non esporlo
affatto) e la **selezione dei conti sulla pagina della banca**. Finché la questione è aperta, i
movimenti del fondo si prendono dal Conto Risparmio già in `cntrs_transactions_terr`.

Per questo `enable-banking-connect` scrive in `cm_sync_log` una riga `consent_request` **prima**
di chiamare `/auth` (IBAN mascherato, `access.accounts`, presenza di `psu-ip-address`, versione
del client) e ne passa l'id nello `state`; `enable-banking-callback` ci scrive sopra il
`bank_connection_id` appena creato. È l'unico aggancio tra richiesta e collegamento: senza,
la riga resta orfana e la diagnosi torna a essere una supposizione.

`state.replaceConnectionId` (pulsante *🔁 Rifai il consenso*) dice che il nuovo consenso
sostituisce un collegamento rimasto senza `account_id`: il callback sposta i `fnz_funds` sul
nuovo e cancella il vecchio. Il passaggio che conta è **spostare i fondi**: farlo a mano si
dimentica, e il fondo resta agganciato a una riga che non sincronizzerà mai.

### ⚠️ Ora legale nei cron

`pg_cron` lavora in **UTC e non conosce il cambio ora**. Gli schedule sono scritti per l'ora legale
(CEST, UTC+2): a fine ottobre, con il ritorno all'ora solare, vanno spostati avanti di un'ora o i
job scatteranno un'ora prima del previsto. Riguarda `fnz-save-snapshot` (`0 21 * * *` = 23:00 CEST)
e `revolut-auto-categorize`.

### ⚠️ Logica dello snapshot duplicata

Il calcolo del patrimonio esiste in **due copie che devono restare allineate**:

| Dove | Perché |
|---|---|
| `finanza.html` — `buildSnapshotPayload`, `portfolioStats`, `computeHoldings`, `computeLoanValue`, `computePricesFromHistory` | Il browser deve calcolare gli stessi numeri per disegnare la dashboard |
| `supabase/functions/save-snapshot/index.ts` — stesse funzioni riscritte in TypeScript | Il job delle 23:00 deve salvare lo snapshot anche se nessuno apre l'app |

**Se cambi una di quelle funzioni in `finanza.html`, cambiala anche nella Edge Function**, altrimenti
lo snapshot notturno diverge da quello che l'app mostra a schermo. La duplicazione è voluta — il
client non può delegare tutto al server perché gli servono comunque i valori live per la dashboard —
ma non è gratis. Il modo per accorgersene: far girare le due implementazioni sugli stessi dati e
confrontare i quattro totali e il JSON `details`, devono coincidere esattamente.

Una terza copia, ridotta al solo valore dei portafogli, sta in `index.html`
(`fetchPortfolioLiveValue`) per l'avviso "Totale portafogli" della home.

---

## Smart Blocker — due profili, un solo codice

`android-app/smartblocker/` produce **due APK** tramite product flavor Gradle (dimensione `profilo`):

| Flavor | applicationId | APK pubblicato | A chi |
|---|---|---|---|
| `salvatore` | `com.garsal.smartblocker` | `releases/SmartBlocker-latest.apk` | App completa: task, sfide Ta Firi?, Analisi Costi |
| `teresa` | `com.garsal.smartblocker.teresa` | `releases/SmartBlockerTeresa-latest.apk` | Solo le transazioni Revolut fatte con la carta di Teresa |

Le differenze passano **tutte** da tre `buildConfigField` letti in `Config.kt` — niente sorgenti
duplicati, niente `if (nomeUtente == …)` sparsi nel codice:

| Campo | `salvatore` | `teresa` | Effetto |
|---|---|---|---|
| `PROFILE` | `salvatore` | `teresa` | Etichette in `MainActivity` |
| `FIXED_DEVICE_TOKEN` | vuoto | `teresa-smartblock` | Se valorizzato, `BlockerService` lo scrive nelle prefs ad ogni avvio invece di chiamare `get_smart_block_token()` |
| `ONLY_APP` | vuoto | `cost_analysis` | `SupabaseApi.queryQueue` scarta ogni riga con `app` diverso |

### Come viene indirizzata la notifica

`revolut-auto-categorize` accoda **una riga per destinatario** in `cm_notification_queue`, distinte
solo da `metadata.device_token`:

- **Salvatore** — token da `cm_user_notification_settings.smart_block_device_token`, tutte le
  transazioni senza categoria (comportamento storico, invariato);
- **Teresa** — token = costante `TERESA_DEVICE_TOKEN`, solo le transazioni con
  `spender_person_id` = riga `ca_people` di nome *Teresa* (l'attribuzione carta → persona la fa già
  `enable-banking-sync` via `ca_card_person_map`).

⚠️ **`TERESA_DEVICE_TOKEN` è duplicato**: `supabase/functions/revolut-auto-categorize/index.ts` e
`build.gradle` (`FIXED_DEVICE_TOKEN`) devono avere lo stesso valore. Se divergono il telefono di
Teresa smette di ricevere notifiche **senza nessun errore** — semplicemente nessuna riga risulta
"sua". Non è un segreto: la policy `smart_block_anon_select` lascia leggere tutte le righe
`smart_block` a chiunque abbia la anon key, il token serve solo a indirizzare.

Il controllo "esiste già una notifica pending" è **per destinatario**, non per regola: le due righe
condividono lo stesso `rule_id` (l'unica riga ancora di `cm_notification_rules`), quindi un controllo
per regola farebbe sparire la notifica di uno ogni volta che l'altro non ha ancora smaltito la sua.

Sul profilo `teresa` nessuna schermata chiede mai il PIN (`Prefs.isInfoOnlyBlock` restituisce sempre
`true`): il PIN è di Salvatore e su quel telefono un blocco rosso sarebbe insbloccabile.

---

## App Details

### `index.html` — AppSphere
- Draggable **bubble/circle UI** — each app is a coloured circle sized proportionally to its `score`
- Score is computed at load time by calling the Supabase RPC `run_score_query` with the SQL stored in `cm_apps.score_query`
- Circle placement uses an iterative collision-resolution algorithm (no overlap, viewport-clamped)
- Tap = launch app; drag = reposition circle
- Color palette: Olympic rings colors (`#0081C8`, `#FCB131`, `#1A1A1A`, `#00A651`, `#EE334E`)

### `tasks.html` — Tasks
- Full task lifecycle: create, edit, complete, skip, fail, clone, delete
- Calendar/planner view with recurring task support
- European date format display (`dd/mm/yyyy`) with ISO storage
- Sidebar sections: Dashboard, Gestione (tasks only), Planner, Reminder, Impostazioni
- FAB `+` apre direttamente la creazione task
- `cm_priorities` e `cm_categories` sono **sola lettura** in tasks.html — la gestione CRUD è in AppSphere → Dati Comuni
- Significant file (~8 500 lines); sections delineated by `// ========================================` comments

### `habit-tracker.html` — Habit Stack Tracker
- Stack-based habits with daily completion tracking
- Gamification: points, multipliers, streaks
- Imports/exports via JSON backup

### `events-log.html` — Events Log
- Groups → Events → Logs hierarchy
- Quick-log UI: select event, tap to log with timestamp

### `obiettivi.html` — Obiettivi
- Obiettivi annuali con sotto-obiettivi trimestrali (`parent_id`, due soli livelli)
- **Due barre affiancate, mai fuse in una media**: *risultato* (metrica `primary` dell'obiettivo) e
  *esecuzione* (% figli + milestone completati). Il progresso del padre **non** è la media dei figli:
  quando le due barre divergono di ≥ 25 punti l'app mostra un avviso esplicito, perché è il segnale
  che il piano viene eseguito ma il metodo non funziona.
- Semaforo (`on_track` / `at_risk` / `off_track`) confrontando il risultato con l'ultima milestone scaduta
- Rubrica multi-criterio per gli obiettivi senza numeri ovvi: slider 1-5, media pesata calcolata
  **server-side** da `ob_record_measurement` (il client non invia mai il valore per `kind='rubric'`)
- Formula unica di avanzamento per tutti i `kind`, regge entrambe le `direction`:
  `(corrente − baseline) / (target − baseline)` — es. pause 14 → 3, corrente 6 ⇒ 73 %

### `weight-quest.html` — Weight Quest
- Chart.js weight graph centred on today (30-day window, scrollable)
- Google Fit integration via OAuth token
- Minimal inline Supabase client (no CDN); milestone and objective tracking

### `cost-analysis.html` — Analisi Costi
- **Non compare più tra le bolle della home**: `cm_apps.active = false` (migration
  `20260802180000_ca_readonly_teresa_and_hide_app.sql`). Resta l'unica app dove si importa, si
  categorizza e si configura (categorie, persone, regole, conti collegati, viaggi) — si apre dal
  collegamento *🛠️ Spese Famiglia — gestione* nella sidebar di `finanza.html` o dalla notifica
  Smart Block.
- **Non configura più i conti bancari**: collegamento, consenso ed eliminazione stanno in
  `finanza.html` → Configurazione → 🏦 Conti Collegati, perché gli stessi conti servono anche
  ai Fondi. In `cost-analysis.html` resta la pagina *Sincronizza e Carte*: il sync di Spese
  Famiglia non si può spostare perché subito dopo l'import fa girare merchant appresi, regole
  e attribuzione per carta, che vivono solo lì.
- La **consultazione** è stata spostata dentro le due app dove serve, come vista in sola lettura:
  `finanza.html` (Salvatore) e `situazione-teresa.html` (Teresa) — vedi sotto.

### Vista "Spese Famiglia" in sola lettura — blocco duplicato
*(la vista si chiamava "Analisi Costi"; nelle due pagine è etichettata **Spese Famiglia**, mentre
l'app di gestione `cost-analysis.html` conserva il nome storico)*
`finanza.html` e `situazione-teresa.html` contengono lo **stesso** blocco CSS + JS (prefisso `ca-` /
`caXxx()`, oggetto di stato `CA`): dashboard (spesa per categoria, andamento mensile, spesa per
persona) e transazioni (filtri, elenco raggruppato per titolo, riepilogo voci entrate/spese).
Nessuna scrittura sul DB, niente import Revolut, categorie, persone, regole o conti collegati.

⚠️ **Le due copie devono restare identiche**: il blocco è delimitato dal commento
`SPESE FAMIGLIA — vista in sola lettura`, dipende solo da `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`tok()` e Chart.js, ed è quindi copiabile pari pari da un file all'altro. Se lo modifichi in uno,
riportalo nell'altro.

Teresa legge i dati via RLS con lo stesso meccanismo del resto della sua pagina
(`cm_guest_access` + `has_page_access('situazione-teresa.html')`). Le policy di lettura sulle
tabelle `ca_*` sono ristrette alle righe di Salvatore con `garsal_user_id()`: quelle tabelle **non
sono monoutente** — Ada ha i propri dati di Analisi Costi e non devono comparire.

### `spuntiamola.html` — Spuntiamola
- Conto alla rovescia "a spunte": si imposta un periodo **dal giorno X al giorno Y** e si spunta
  un giorno alla volta fino al traguardo
- Griglia dei giorni raggruppata per mese; ogni cella è cliccabile (spunta / de-spunta)
- Stati della cella: *fatto* (verde), *oggi* (bordo viola), *saltato* (rosso, giorno passato non
  spuntato), *da fare* (grigio)
- **Ogni spunta assegna un'emoji casuale al giorno** (resta lì, salvata) e mostra un **toast di
  ~2,5 s** con una frase simpatica + confetti; a 25/50/75/100 % scattano messaggi di traguardo
  dedicati (toast più grande, 4,5 s)
- Opzione "salta sabato e domenica" per contare solo i giorni feriali
- **Giornate chiave** (`sp_key_days`): giorni "che contano" segnati dalle impostazioni, con
  etichetta libera. Nella griglia sono dorate con una ★; alla spunta parte `fuochiArtificio()`
  — sei scoppi scaglionati, ognuno con lampo centrale e raggiera di scintille/stelle con
  gravità — più un toast dorato da 5 s. Le giornate chiave si modificano su una copia
  (`tmpKeyDays`) e si applicano solo con "Salva", così "Annulla" butta via tutto davvero.
- **Avviso "oggi non spuntato"** in due punti: banner giallo dentro l'app e sezione dedicata nel
  fumetto avvisi di AppSphere (`loadHomeAlertSpuntiamola` in `index.html`). Se oggi è una
  giornata chiave entrambi gli avvisi lo dicono esplicitamente.
- **Top bar standard** `#garsal-top-bar` come le altre app (barra fissa blu `#0081C8` alta 56 px,
  logo a cerchi olimpici + "Garsal Apps" con `href="/"`); a destra restano badge di sincronizzazione,
  versione e il pulsante ⚙️ Impostazioni
- **Dati su Supabase** (`sp_settings` + `sp_checks`, migration `20260728100000_sp_spuntiamola_tables.sql`):
  il DB è la fonte di verità, `localStorage` (chiavi `sp_*`) resta come cache offline così la
  griglia compare subito all'apertura. Al primo avvio dopo l'aggiornamento le spunte già presenti
  in locale vengono caricate sul DB una volta sola.
- Le scritture sono **ottimistiche con rollback**: se la chiamata al DB fallisce la spunta viene
  tolta e compare un toast di errore, così non resta una spunta finta che sparisce al reload
- Registrata in `cm_apps` da `20260727230000_spuntiamola_app.sql`; la `score_query` (aggiornata
  dalla migration `sp_`) conta i **giorni che mancano**, quindi la bolla nel launcher è proporzionata

---

## Development Workflow

### No build step
Edit the HTML file directly. Refresh the browser. Done.

```bash
# Open a file locally — no server required for most features
open tasks.html

# Or use a local HTTP server for auth redirect flows
python3 -m http.server 8080
```

### Ambiente di sviluppo (dev environment)

Il repository include un ambiente dev separato dalla produzione:

**Server locale:**
```bash
bash server.sh        # avvia su http://localhost:8080
bash server.sh 3000   # porta personalizzata
```
Quando si accede da `localhost`, le app rilevano automaticamente `_IS_DEV = true` e usano il progetto Supabase dev.

**Credenziali dev nei file HTML:**
Ogni file HTML contiene un blocco `_IS_DEV` che switcha le credenziali Supabase in base all'hostname:
```js
const _IS_DEV = ['localhost', '127.0.0.1', '0.0.0.0'].includes(window.location.hostname)
             || (window.location.hostname.endsWith('.netlify.app')
                && window.location.hostname.startsWith('dev--'));
const SUPABASE_URL = _IS_DEV ? 'https://DEV_SUPABASE_PROJECT_REF.supabase.co' : 'https://jajlmmdsjlvzgcxiiypk.supabase.co';
const SUPABASE_KEY = _IS_DEV ? 'DEV_SUPABASE_ANON_KEY' : '<PROD_KEY>';
```
**I placeholder `DEV_SUPABASE_PROJECT_REF` e `DEV_SUPABASE_ANON_KEY` devono essere sostituiti** con le credenziali reali del progetto Supabase dev creato su [supabase.com](https://supabase.com).

**Setup iniziale del progetto Supabase dev (una tantum):**
1. Creare un nuovo progetto su [supabase.com](https://supabase.com) (piano gratuito va bene)
2. Replicare lo schema: `supabase db push --project-ref <DEV_PROJECT_REF>`
3. In Auth → URL Configuration → Redirect URLs, aggiungere: `http://localhost:8080`
4. Aggiungere il secret `SUPABASE_DEV_PROJECT_REF` in GitHub → Settings → Secrets (usato da `deploy-dev.yml`)
5. In Netlify → Site configuration → Build & deploy → Branch deploys → aggiungere pattern `dev/*`

**Branch naming:**
- `dev/<descrizione>` — sviluppo/test, preview su Netlify, **non va in produzione**
- `claude/<descrizione>-<id>` — produzione, auto-merge su master

**Preview URL per branch dev:**
```
dev--<nome-branch>--<sitename>.netlify.app
```
Usa automaticamente Supabase dev (rilevamento hostname).

### Deployment
Netlify auto-deploys on push to `master`. Configuration in `netlify.toml`:
```toml
[build]
  publish = "."
  base = "."
```
The root `/` is served directly by `index.html` (Netlify serves an existing physical file at a path before applying any redirect rule for that path — a redirect from `/` to another file would never actually fire).

Push to `master` → Netlify picks it up → live within seconds.

### Git workflow
- `master` — production branch (auto-deployed to Netlify)
- `claude/<description>-<id>` — feature branch → auto-merge to master → produzione
- `dev/<description>` — development/staging branch → preview URL Netlify, **non va in produzione**
- Commit message prefixes used in this repo:
  - `feat:` — new feature
  - `fix:` — bug fix
  - `ui:` — visual / layout change
  - `refactor:` — code restructure without behaviour change
  - `chore:` — tooling, config, or non-functional change

### Deploy automatico
Pushing to a `claude/**` branch triggers `.github/workflows/deploy.yml` which:
1. Merges the branch into `master` automatically (no PR needed)
2. Netlify picks up the master push and deploys within seconds

Pushing to a `dev/**` branch triggers `.github/workflows/deploy-dev.yml` which:
1. **Does NOT merge to master**
2. Netlify creates a branch preview deploy at `dev--<branch>--<sitename>.netlify.app`
3. Optionally applies Supabase migrations/functions to the dev project

**Claude cannot push directly to `master`** (HTTP 403 — server-side branch protection).
The only path to production is: push to `claude/**` → GitHub Actions merges → Netlify deploys.

### Versioning — regola obbligatoria
**Ad ogni modifica a qualsiasi file** (HTML o Android), Claude deve aggiornare la versione **nello stesso commit** delle modifiche, non dopo.

#### File HTML
1. **Incrementare il patch version** (`APP_VERSION`) — es. `v3.1.1` → `v3.1.2`
2. **Aggiornare `BUILD_TIME`** con il timestamp UTC corrente — es. `'2026-02-24T20:00:00Z'`
3. **Verificare che la versione compaia in**:
   - `<title>` tag della pagina
   - `var APP_VERSION` nello script
   - `var BUILD_TIME` nello script
   - `console.log` stilizzato visibile nei DevTools del browser
   - Log dell'app (funzione `log()`)

#### App Android (`android-app/smartblocker/`)
1. **Incrementare `versionName`** in `build.gradle` — es. `"1.2.3"` → `"1.2.4"`
2. **Incrementare `versionCode`** di 1 — es. `14` → `15`
3. **Aggiornare la stringa versione in `MainActivity.kt`** — es. `"v1.2.3 · PIN: …"` → `"v1.2.4 · PIN: …"`

Struttura versioning in `weight-quest.html` (righe ~782–787):
```js
var APP_VERSION = 'v3.1.2';
var BUILD_TIME  = '2026-02-24T20:00:00Z';
console.log('%c WEIGHT QUEST ' + APP_VERSION + ' %c build: ' + BUILD_TIME,
    'background:#4caf50;color:#fff;font-weight:bold;padding:2px 6px;border-radius:3px 0 0 3px',
    'background:#222;color:#aaa;padding:2px 6px;border-radius:0 3px 3px 0');
```

E nel blocco START (righe ~3280):
```js
console.log('%c⚖ Weight Quest ' + APP_VERSION, 'color:#00B894;font-size:16px;font-weight:bold;');
console.log('%cbuild: ' + BUILD_TIME, 'color:#888;font-size:11px;');
log('===========================================');
log('WEIGHT QUEST ' + APP_VERSION + ' — build: ' + BUILD_TIME);
log('===========================================');
```
---

## Key Conventions

### CSS variables (Tasks, Habit Tracker, Events Log)
All three share an identical CSS custom property palette:
```css
:root {
  --primary: #FF3366;
  --secondary: #6C5CE7;
  --success: #00B894;
  --warning: #F39C12;
  --danger: #E74C3C;
  --dark: #1F2937;
  --light: #FFFFFF;
  --muted: #6B7280;
  --accent: #2563EB;
  --border: #E5E7EB;
  --card-bg: #FFFFFF;
  --input-bg: #F9FAFB;
}
```

### Date handling
- Dates are stored as ISO strings (`YYYY-MM-DD`) in Supabase
- Displayed in European format (`dd/mm/yyyy`) in the UI
- **Critical**: avoid UTC conversion when extracting local dates — use `new Date(str)` carefully or split the ISO string directly to prevent off-by-one day bugs

### Supabase error handling pattern
```js
const { data, error } = await sb.from('table').select('*');
if (error) {
    console.error('Error:', error);
    alert('Errore: ' + (error.message || 'Unknown error'));
} else {
    // use data
}
```

### Version in title
App versions are tracked in the `<title>` tag and displayed in the sidebar (e.g. `Tasks v19.17.12`). Increment the patch version on meaningful changes.

### No TypeScript / no linting
There is no TypeScript, ESLint, Prettier, or any linting/formatting tool configured. Code style follows existing patterns in each file.

### Italian language
All user-facing strings, comments, and variable names (where contextual) are in Italian. Commit messages are also often in Italian. Match the existing language when adding code.

---

## Common Pitfalls

1. **CDN dependency**: Apps require internet access to load Supabase JS, Chart.js, Google Fonts, etc. They will not work fully offline.
2. **Auth token scope**: The Supabase anon key is public but Row Level Security (RLS) on Supabase controls access. Do not assume tables are publicly writable — the user must be authenticated.
3. **weight-quest auth**: Unlike other apps, weight-quest does NOT use the Supabase JS SDK for auth; it uses raw Google OAuth + its own minimal client. Token is received via `postMessage` from the launcher or retrieved from `localStorage`.
4. **Large file sizes**: `tasks.html` is ~445 KB and ~8 900 lines. When editing, use search to navigate to the relevant section. Sections are marked with `// ========================================` banners.
5. **No hot reload**: There is no dev server. After editing, hard-refresh the browser (`Cmd/Ctrl+Shift+R`).
6. **Duplicate `renderTaskCard`**: `tasks.html` defines `renderTaskCard` in two places (dashboard view and categories/management view). Both must be kept in sync when changing card rendering logic.
7. **Calcolo patrimonio duplicato**: la logica dello snapshot vive sia in `finanza.html` sia in `supabase/functions/save-snapshot/index.ts`, più una versione ridotta in `index.html` (`fetchPortfolioLiveValue`). Modificarne una sola fa divergere in silenzio lo snapshot notturno o l'avviso in home — dettagli in *Edge Functions e job schedulati*.
8. **Vista Spese Famiglia duplicata**: lo stesso blocco in sola lettura vive in `finanza.html` e in `situazione-teresa.html`. Modificarne uno solo fa divergere in silenzio le due pagine — dettagli in *App Details → Vista "Spese Famiglia" in sola lettura*.
9. **Snapshot solo all'apertura di Finanza**: `fnz_dashboard_snapshots` viene scritto da `autoSaveSnapshot` quando si apre l'app, e dal job delle 23:00. Chi legge lo snapshot come "valore attuale" durante il giorno ottiene un dato fermo alla notte precedente: per il valore aggiornato bisogna ricalcolarlo sui prezzi correnti.

---

## Regola obbligatoria — Modifiche a tabelle o campi JSON

**PRIMA di qualsiasi modifica** a:
- struttura di una tabella Supabase (aggiunta/rimozione/rinomina colonne)
- struttura di un campo JSON/JSONB esistente (aggiunta/rimozione/rinomina chiavi)

Claude **deve avvisare esplicitamente** l'utente e attendere conferma. Non procedere mai in autonomia con queste modifiche.

Esempi che richiedono avviso preventivo:
- aggiungere un campo `smart_block_fire_at` dentro `reminder_presets`
- rinominare una colonna `due_at` → `fire_at`
- aggiungere una colonna `notification_spec` a `cm_notification_rules`

Se il codice necessita di un campo che non esiste ancora nel DB, proporre la migration SQL all'utente e **non inventare campi nuovi senza chiedere**.
