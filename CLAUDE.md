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

### ⚠️ Sul telefono i caratteri di sistema sono molto grandi

Non è un caso limite da verificare alla fine: **è la condizione normale in cui queste app vengono
usate**. Salvatore tiene l'ingrandimento dei caratteri di Android su un valore alto, quindi ogni
scritta arriva a schermo parecchio più grande di come si vede in anteprima o sul desktop. Va
messo in conto **mentre** si scrive l'interfaccia, non dopo:

- **niente altezze fisse attorno al testo.** `height(56.dp)` su una barra che contiene una scritta
  è un ritaglio che aspetta il momento giusto per succedere — al primo ingrandimento il testo è più
  alto del contenitore e quel che avanza sparisce. Si usa `heightIn(min = …)` (Compose) o
  `min-height` (CSS), mai `height`;
- **una riga sola è un'ipotesi, non un dato.** Titoli, etichette dei pulsanti e voci di menù vanno
  a capo: o si lascia spazio per due righe, o si mette `maxLines` + ellissi decidendo *cosa* è più
  importante che resti leggibile;
- **le icone in `dp` non crescono col testo.** Accanto a una scritta ingrandita sembrano rimpicciolite:
  dove stanno in fila con del testo conviene scalarle con `fontScale` (con un tetto, vedi
  `GarsalTopBar`);
- **le griglie si sfilacciano**: celle affiancate con testi di lunghezza diversa vanno a capo un
  numero diverso di volte e perdono l'allineamento. Serve un'altezza minima comune, non una fissa.

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
| `cm_institutions` | Censimento di banche e broker. `connectable = false` per chi non è nel catalogo Enable Banking (broker senza PSD2): sta in anagrafica ma non si collega |
| `cm_bank_connections` | Un conto per riga, nato da un consenso. `uses text[]` dice a cosa serve |
| `cm_sync_log` | Storico delle sincronizzazioni bancarie |

Le due tabelle bancarie nascono come `ca_bank_connections` / `ca_sync_log` (Analisi Costi) e
sono state rinominate quando i conti sono diventati condivisi con i Fondi
(`20260803120000_cm_bank_connections_and_fondo_sync.sql`). Le viste di compatibilità con il
vecchio nome sono state eliminate da `20260807100000_cm_institutions_and_account_uses.sql`.

**Il collegamento di un conto è in tre momenti distinti** (tutti in `finanza.html` →
Configurazione → 🏦 Banche e Conti):

1. **censimento** — si aggiunge l'istituto a `cm_institutions`, cercandolo nel catalogo
   Enable Banking (o a mano, non collegabile);
2. **consenso** — `enable-banking-connect` riceve solo `{ institutionId, replaceConnectionId }`:
   nome ASPSP e paese vengono dall'anagrafica e non si compila nient'altro;
3. **battesimo** — i conti restituiti dalla banca nascono con `display_name NULL` e
   `uses '{}'`, e l'utente dà a ciascuno un nome e uno o più usi.

`cm_bank_connections.uses` ammette `'cost_analysis'` (Spese Famiglia), `'contribuzione'`,
`'spese_ada'`, `'spese_sal'` (Spese Personali), `'fondo'`, `'conto_risparmio'`, `'casa_rosa'` e
`'danaro_rosa'`: **un conto può
servire a più moduli** — il conto
delle spese comuni è insieme `cost_analysis` e `contribuzione` — e `uses` vuoto significa «scoperto
ma non ancora battezzato» — il conto esiste e si vede, ma nessun modulo lo elenca. Ha sostituito
la vecchia colonna `module`, che ne ammetteva uno solo e andava scelta *prima* del consenso.
`display_name` è **solo** il nome dato dall'utente: nessuna Edge Function lo riempie più
d'ufficio con l'IBAN, che ha la sua colonna `iban`.

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

### Danaro di Rosa (`fnz_other_assets` + `fnz_rosa_transactions`)
| Table | Purpose |
|---|---|
| `fnz_other_assets` con `asset_type = 'danaro_rosa'` | Le voci di Rosa (BTP, Conto Arancio, altro): `value` è il saldo/valore di mercato, `valuation_date` la data cui si riferisce |
| `fnz_rosa_transactions` | Movimenti del conto di Rosa letti dalla banca: `amount` **con segno**, `external_id` unico per `bank_connection_id` |

**Nessuna categoria, di proposito**: questa pagina tiene il registro del conto e il saldo, non
analizza la spesa come Spese Famiglia o Spese Ada. Una colonna categoria che nessuno riempie è
solo un invito a reimplementare la categorizzazione una quarta volta.

Il conto si collega come tutti gli altri (`finanza.html` → Configurazione → 🏦 Banche e Conti) e
si spunta come `'danaro_rosa'` in `cm_bank_connections.uses`. Da lì *🏦 Leggi dal conto* chiama
`enable-banking-transactions` — che legge e basta — e la stessa finestra fa due cose distinte:
archivia i movimenti scelti e, se lo si conferma, riscrive `value` e `valuation_date` di **una**
voce di Danaro di Rosa col saldo letto. La voce si sceglie nella tendina: il conto non è legato a
una riga di `fnz_other_assets`, perché accanto al Conto Arancio ci sono un BTP e altre voci che
con quel saldo non c'entrano.

Rosa vede le voci in `situazione-rosa.html` (via `cm_guest_access`), **non i movimenti**:
`fnz_rosa_transactions` ha la sola policy owner.

### Reddito (`fnz_income`)
| Table | Purpose |
|---|---|
| `fnz_income` | Reddito percepito, **una riga per anno e per tipo**: `year`, `kind`, `amount`, `notes` |

Si compila a mano da `finanza.html` → 💶 **Reddito** (voce di menù propria): è l'unico dato di
Finanza che non viene da nessuna banca. **Non entra in nessun totale del patrimonio** — il
patrimonio è quello che c'è, il reddito è quello che è passato — quindi né lo snapshot né la
dashboard lo guardano.

Una riga per anno *e* per tipo, non una riga per anno con colonne fisse: i tipi vivono in
`INCOME_SECTIONS` dentro `finanza.html` e aggiungerne uno è una riga di JavaScript, non una
migration. `kind` è testo libero di proposito; il vincolo che conta è
`UNIQUE (user_id, year, kind)`, su cui la pagina scrive in **upsert** — senza, ricompilare un
anno raddoppierebbe il totale in silenzio. Una casella lasciata vuota **cancella** la sua riga
invece di salvare zero: «dato assente» e «zero euro» sono due cose diverse, e un importo tolto
che restasse scritto continuerebbe a fare totale.

Nella sidebar è una **voce di menù a sé** (sottotitolo *Reddito*), non più insieme a 💎 Asset
sotto *Patrimonio*: quelli sono quello che c'è, il reddito è quello che è passato.

La pagina è in **due sezioni**, una tabella ciascuna: redditi (`lavoro`, `pensione`,
`altri_lordi`, `altri_netti`) e liquidazione della dichiarazione (`liq_imponibile`,
`liq_imposta_lorda`, `liq_imposta_netta`). Il ✎ e il ✕ agiscono **sulla singola sezione** di un
anno, non sull'anno intero: le due tabelle vengono da documenti diversi.

⚠️ **Due colonne della liquidazione sono calcolate** e vivono in `INCOME_CALC`, non in
`fnz_income`:

| Colonna | Formula |
|---|---|
| Detrazioni | `liq_imposta_lorda − liq_imposta_netta` |
| Reddito netto | `liq_imponibile − liq_imposta_netta` |

Si riconoscono dal **«(calc.)» in intestazione**, non compaiono nel form e non si archiviano: una
grandezza calcolata *e* archiviata sono due verità sullo stesso numero, che divergono il giorno
che una delle due cambia. Tornano `null` — non zero — se manca un ingrediente, quindi il reddito
netto del 2017 resta un trattino (manca l'imponibile). Le detrazioni tornano **esatte su tutti e
sette gli anni**, verificate contro il vecchio `liq_detrazioni` archiviato.

⚠️ Il **reddito netto così calcolato non toglie le addizionali** regionale e comunale, che sul 730
stanno fuori dall'imposta netta: è il reddito al netto della sola IRPEF, non l'accredito in banca.

`liq_reddito_netto` **non è più un kind** — la colonna era stata aperta il 16 agosto e mai
compilata. Netto in busta (lordo − ritenute), totale lavoro dipendente (lordo + pensione) e totale
trattenuto erano calcolati pure loro e restano fuori: le formule sono nella storia di git
(`e78fd62` e precedenti).

⚠️ **I kind usciti dalle tabelle sono stati cancellati dal database**, su richiesta esplicita:
`20260816110000_fnz_income_cancella_kind_ritirati.sql` ha tolto 86 righe su 112 in 19 kind —
ritenute in busta paga (`rit_*`), contributi previdenziali (`prev_*`), premi/welfare/cedolare
(`prem_*`, `ced_*`), cinque voci della liquidazione (`liq_detrazioni`, `liq_imposta_netta`,
`liq_acconti`, `liq_esito`, `liq_reddito_rif`) e tre dei redditi (`fabbricati`,
`abitazione_principale`, `reddito_complessivo`). In `fnz_income` restano solo i kind mostrati.
**La via di recupero è la migration dei dati storici**, dove gli importi sono scritti in chiaro:
le migration girano in ordine anche su un database nuovo, quindi il risultato coincide con la
produzione. Togliere una voce da `INCOME_SECTIONS` resta reversibile in sé, ma **la colonna
riaperta è vuota** finché una migration non ne ricopia gli importi da lì: è quello che
`20260817100000_fnz_income_ripristina_imposta_netta.sql` fa per `liq_imposta_netta`, rimessa in
tabella coi suoi sette anni (2017, 2019-2022, 2024-2025). Le altre quattro voci della
liquidazione restano cancellate: `liq_detrazioni` è tornata a vedersi **come colonna calcolata**,
quindi senza rimettere in piedi la sua riga, e acconti, esito e reddito di riferimento restano
fuori del tutto.

I **due riquadri in cima** (UniCredit lordo, Reddito netto) passano dalla stessa `incomeCell()`
delle celle della tabella, calcolate comprese: il riquadro non può mostrare un numero diverso da
quello che si legge nella riga sotto. Mostrano l'ultimo anno che ha quella colonna, e la
variazione **solo se ce l'ha anche l'anno prima** — cioè fra due cifre ricavate allo stesso modo.

La tabella arriva **fino all'anno scorso** e non a quello in corso: un anno incompleto messo in
colonna accanto agli altri sembrerebbe un crollo del reddito. Gli anni senza dati restano
visibili come righe da riempire — un anno saltato deve vedersi, non sparire dall'elenco.

I dati storici 2017-2025 ricavati dai 730 e dalle CU sono in
`20260815160000_fnz_income_dati_storici.sql`: l'insert è idempotente (`ON CONFLICT … DO UPDATE`)
e intestato all'utente cercato per email, e salta senza fallire se quell'utente non esiste
(progetto dev).

### Memorandum (`mm_`)
| Table | Purpose |
|---|---|
| `mm_cards` | Le schede. `kind` vale `'nota'`, `'lista'` o `'diario'`; `riservato` le tiene fuori dagli elenchi |
| `mm_card_categories` | Associazione scheda ↔ `cm_categories` |
| `mm_images` | Metadati delle foto (i file stanno nel bucket `mm-images`) |
| `mm_list_items` | Voci di una lista: `text`, `done`, `position`, `done_at` |
| `mm_diary_metrics` | Le misure di un diario: `kind` `'scala'` (con `min_value`/`max_value`) \| `'numero'` (con `unit`) \| `'bool'` \| `'scelta'` (con `options`); `hint` spiega cosa vuol dire il punteggio |
| `mm_diary_entries` | Le registrazioni: `title` (obbligatorio lato app), `entry_date`, `note`, e `measures` jsonb |

**Una scheda è una riga sola in tutt'e tre i casi**: il tipo è una colonna, non una tabella a
parte, quindi ricerca, categorie, colore, 📌 e foto valgono uguale per note, liste e diari.
`kind` ha `DEFAULT 'nota'`: le schede nate prima della colonna restano quello che erano.

`mm_diary_entries.measures` è `{ "<id della misura>": numero|booleano }`. Le **misure sono righe
vere e i valori no**, ed è voluto: una misura deve sopravvivere alle registrazioni che la citano
(cambiarle nome non deve riscrivere lo storico), mentre i valori si leggono e si scrivono sempre
tutti insieme, quindi una tabella in più sarebbe solo una join in più. Per la stessa ragione le
misure si **aggiornano riga per riga e non si cancellano per ricrearle** (`syncDiaryMetrics`):
ricreandole cambierebbero id e tutte le registrazioni passate resterebbero senza nome.

⚠️ **Una misura non registrata non sta in `measures`**, non ci sta come zero: «non l'ho misurata»
e «vale zero» sono due cose diverse, e uno slider lasciato a metà scriverebbe la seconda al posto
della prima. Nella schermata di registrazione una misura non toccata mostra `—` e non finisce
nell'archivio; il pulsante *Non l'ho misurata* la toglie anche a posteriori. È la stessa scelta
della casella vuota in `fnz_income`.

Togliere una misura da un diario **non cancella le registrazioni**: i valori restano scritti in
`measures` con una chiave che non ha più una riga, e l'app li mostra come *misura tolta* invece di
farli sparire. L'avviso prima di togliere lo dice.

**Le misure `'scelta'` sono combo configurabili** (es. *origine*: sociale, autorità, sbagliare,
giudizio). Le opzioni stanno in `mm_diary_metrics.options` come `[{id, label}]` e la registrazione
archivia l'**id**, mai l'etichetta: rinominare un'opzione deve rileggere anche lo storico col nome
nuovo, mentre archiviando la parola una rinomina spaccherebbe la stessa origine in due categorie
che ai conteggi sembrano diverse. È la stessa ragione per cui le misure si aggiornano riga per
riga invece di essere ricreate, un livello più sotto. Un'opzione tolta segue la regola della
misura tolta: le registrazioni restano e mostrano *opzione tolta*.

Una combo **deve avere almeno un'opzione** (vincolo `mm_diary_metrics_scelta_check`): senza, la
misura non chiederebbe niente — è rotta, non vuota. Le opzioni lasciate in bianco si scartano da
sé al salvataggio.

⚠️ Una scelta **non ha un numero** e `numericValue()` torna `null`: l'ordine delle opzioni è un
elenco, non una scala, e farne una media o una spezzata vorrebbe dire trattare *giudizio* come il
doppio di *autorità*. Nel riepilogo al posto dello storico in miniatura c'è la **distribuzione** —
quante volte è uscita ciascuna opzione — che è la domanda che una combo pone davvero.

### Weight Quest (`ps_`)
| Table | Purpose |
|---|---|
| `ps_weight_tracking` | Weight measurement entries |

### Spese Ada (`ada_`)
| Table | Purpose |
|---|---|
| `ada_super_categories` | Super-categorie: **l'unico livello che porta il `color`** |
| `ada_categories` | Voci di spesa (nome unico per utente, `icon`, `super_id`); `color` non è più usato |
| `ada_transactions` | Movimenti importati dal conto: `amount` **con segno**, **una sola** `category_id`, `card_identification`, `external_id` (unico per `bank_connection_id`) |
| `ada_merchant_map` | Negozi imparati: `merchant_key` normalizzato → categoria |

Sono volutamente **separate dalle `ca_*`**: le spese di Ada non devono entrare nei totali di
Spese Famiglia, e una separazione per campo sarebbe retta solo finché ogni query si ricorda di
filtrarla.

### Spese Personali (`sal_`)
| Table | Purpose |
|---|---|
| `sal_super_categories` | Super-categorie: **l'unico livello che porta il `color`** |
| `sal_categories` | Voci di spesa (nome unico per utente, `icon`, `super_id`) |
| `sal_transactions` | Movimenti del conto personale: `amount` **con segno** (entrate comprese), **una sola** `category_id`, `card_identification`, `external_id` (unico per `bank_connection_id`) |
| `sal_merchant_map` | Negozi imparati: `merchant_key` normalizzato → categoria |

Stesso schema delle `ada_*` e stessa ragione per cui è separato dalle `ca_*`: le spese del conto
personale di Salvatore non devono entrare nei totali di Spese Famiglia. **L'unica differenza è
cosa ci finisce dentro**: qui si archiviano anche le entrate, perché la pagina tiene il saldo del
periodo — nessuna colonna in più, `amount` è già con segno.

### Spuntiamola (`sp_`)
| Table | Purpose |
|---|---|
| `sp_settings` | Una riga per utente: traguardo, emoji, periodo (`start_date` → `end_date`), `skip_weekend` |
| `sp_checks` | Una riga per giorno spuntato (`UNIQUE (user_id, day)`); `emoji` è quella pescata a caso alla spunta |
| `sp_key_days` | Giornate chiave (`UNIQUE (user_id, day)`); `label` è l'etichetta libera mostrata alla spunta |
| `sp_stecche` | Archivio delle stecche chiuse: traguardo e periodo com'erano, `total_days`/`done_days`, `satisfaction` (1-100), `note`, e la fotografia jsonb di `checks` e `key_days` |

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
| `get-prices` | orario | Aggiorna `fnz_price_cache` (un trigger propaga su `fnz_price_history`). Una fonte diversa per tipo: BTPi→SoldiOnline, BTP→rendimentibtp, ETF→JustETF (scheda HTML)/Yahoo/Investing, crypto→CoinGecko in batch + Coinbase come ripiego, azioni→TwelveData/GoogleFinance |
| `enable-banking-connect` / `-callback` / `-aspsps` / `-refresh-accounts` | — | Collegamento di un conto: catalogo banche, avvio del consenso (`connect` riceve solo `institutionId` e legge banca e paese da `cm_institutions`), redirect di ritorno e rilettura dei conti di una sessione già ottenuta. Il callback crea i conti anonimi e riporta sempre su `finanza.html`, con il `session_id` del consenso perché la pagina apra subito il battesimo |
| `enable-banking-sync` | manuale (da `cost-analysis.html`) | Importa le transazioni di un conto in `ca_transactions` (Spese Famiglia) |
| `enable-banking-fondo-sync` | manuale (da `finanza.html`, scheda fondo) | Importa i bonifici di un conto in `fnz_fund_contributions`: CRDT → versamento (controparte = debtor), DBIT → prelievo (controparte = creditor), match su IBAN e poi su nome; senza match la riga entra come `da_rivedere` |
| `enable-banking-transactions` | manuale (da `conto-risparmio-teresa.html`, `conto-spese-teresa.html`, `spese-ada.html`, `casarosa.html` e da `finanza.html` → 🌹 Danaro di Rosa) | **Legge e basta**: restituisce movimenti (importo con segno, `card` quando la banca espone la carta usata) e saldi normalizzati di un conto, senza scrivere niente. Destinazione, categorie e controllo dei doppioni restano al chiamante — Conto Risparmio, Contribuzione e Spese Ada hanno già i propri |
| `save-snapshot` | `fnz-save-snapshot`, 21:00 UTC | Chiama `get-prices`, poi calcola e salva lo snapshot del patrimonio in `fnz_dashboard_snapshots` per ogni utente che ha dati di Finanza |

### ⚠️ Un prezzo può essere insieme plausibile e sbagliato

Il modo peggiore in cui `get-prices` si rompe non è restare senza prezzo — quello si vede, la
riga in Finanza → Prezzi diventa arancione «Non oggi». È **scrivere in cache un numero che
sembra un prezzo e non lo è**: la riga resta verde «OK» e il patrimonio è falso senza che da
nessuna parte compaia un errore.

È successo il 13 agosto 2026 con **BTP10** (Amundi Ita BTP 10y, quota ≈157 €), finito in cache a
poche unità di euro. La causa: `/api/etfs/{ISIN}/performance` di JustETF veniva letto come un
listino, ma il suo `latestValue` — come i `positions[].value` — è una **performance in
percentuale**, e `valuation=NAV` non cambia la natura di quei numeri. Era la stessa trappola già
annotata nel codice per il `latestValue` della pagina HTML: **lo stesso nome di campo, sulla
stessa fonte, vuol dire due cose diverse dal prezzo.** L'endpoint è stato tolto (v5.18.0): per
gli ETF resta lo scraping della scheda HTML, poi Yahoo, Investing.com ed Euronext.

Due regole che ne discendono, entrambe già in codice:

- **ogni prezzo entra da un varco solo**, `pushPrice()`, che arrotonda, confronta con l'ultimo
  prezzo noto e scarta chi se ne discosta di oltre il 50 % (`MAX_PRICE_DEVIATION`; le crypto sono
  escluse, quel movimento lo fanno davvero). Una fonte scartata non ferma il giro: si passa alla
  successiva, e se non risponde nessuna resta il prezzo di ieri — un buco è visibile, un numero
  sbagliato no;
- **il confronto si disarma da sé** dopo tre giorni senza scritture (`PREV_PRICE_MAX_AGE_MS`),
  altrimenti si morde la coda: un valore sbagliato in cache (o uno split vero) farebbe scartare
  per sempre i prezzi giusti che arrivano dopo. Finché una fonte risponde `updated_at` si
  riscrive ogni ora anche a mercati chiusi, quindi tre giorni non sono un fine settimana.

Aggiungendo una fonte nuova: il prezzo va restituito e passato a `pushPrice()`, mai messo in
`rows` a mano — e prima di fidarsi di un campo JSON conviene guardare **cosa misura**, non come
si chiama.

### ⚠️ Header PSU obbligatori per alcune banche

Il catalogo `/aspsps` di Enable Banking dichiara per ogni banca un `required_psu_headers`:
**UniCredit (IT) richiede `psu-ip-address`**, Revolut no. Tutte le chiamate a Enable Banking
passano quindi gli header PSU ricavati da `x-forwarded-for` (`psuHeaders()`, duplicata in ogni
Edge Function). Un job schedulato non ha un utente davanti: in quel caso l'header non c'è e non
va inventato. Mandarlo è doveroso, ma **non è mai stato la causa del consenso vuoto**: quella è
la whitelist (sezione qui sotto).

Stessa scheda di catalogo: `maximum_consent_validity` (180 giorni per UniCredit) e
`auth_methods` per `psu_type`. Il pulsante *ℹ️ Cosa richiede questa banca* in `finanza.html`
la mostra per intero — è il primo posto da guardare quando un collegamento riesce a metà.

### ⚠️ Restricted mode: i conti vanno messi in whitelist, o la sessione torna vuota

**L'applicazione Enable Banking è in _restricted mode_** (attivata con *«Activate by linking
accounts»*, senza contratto di produzione piena). In quella modalità l'API restituisce
**soltanto i conti collegati nel Control Panel** con *«Link accounts»*: per un conto mai
collegato lì il consenso viene autorizzato, la banca mostra il conto spuntato, e la sessione
torna con `accounts: []` e `access.accounts: null` — **senza nessun errore**, perché per Enable
Banking non è un errore ma il filtro previsto.

#### Collegare un conto nuovo: la whitelist viene prima

Non è una procedura di emergenza, è **il primo passo di ogni collegamento**. Saltarlo costa un
giro di login e SCA in banca per niente.

1. Control Panel di Enable Banking → l'applicazione → **«Link accounts»**
2. autenticazione presso la banca e autorizzazione del conto
3. **solo adesso** il collegamento dall'app (Finanza → Configurazione → 🏦 Banche e Conti)

L'applicazione resta «Restricted» ma «Active»: è lo stato normale finché non c'è un contratto di
produzione piena.

#### Perché è costato tre giorni (agosto 2026)

Revolut funzionava perché quel conto era stato collegato all'attivazione dell'applicazione;
UniCredit no perché non lo era mai stato — e il sintomo era indistinguibile da un bug nostro.
**Prima di cercare la causa nel codice, verificare la whitelist.** Verificato funzionante il
6 agosto 2026: messo il conto UniCredit in whitelist, il consenso rifatto dall'app ha
restituito il conto al primo colpo.

Corollario: nessuna modifica a `enable-banking-connect` / `-callback` può aggirarlo — IBAN in
`access.accounts`, header PSU, `auth_method`, `psu_type` e durata del consenso sono tutti
irrilevanti se il conto non è collegato. Sono già stati provati tutti, uno per uno.

Nota di metodo: la risposta era nella documentazione di Enable Banking
(*Whitelisting own accounts for restricted API usage*). Il primo tentativo di leggerla è
fallito con un 403 del proxy e l'indagine è proseguita per ipotesi sul codice: quando un
sintomo non torna, **cercare nella documentazione del fornitore prima di dedurre dal
comportamento** — e insistere da un'altra fonte se la prima non risponde.

### Consenso vuoto: la richiesta non c'entra

Una sessione `AUTHORIZED` con `access.accounts: null` e zero conti **ha una causa sola, ed è la
whitelist** (sezione qui sopra). Per tre giorni si è cercata nella richiesta: IBAN in
`access.accounts`, header PSU, `auth_method`, `psu_type`, durata del consenso. Le richieste del
5 e del 6 agosto 2026 sono partite con `accounts: [{iban}]` e `psu-ip-address` inviato, e sono
tornate identiche a quelle senza — `access.accounts: null`, `accounts: []`, `accounts_data: []`.
**Sono ipotesi bruciate: non ripercorrerle.**

Di conseguenza `enable-banking-connect` non manda più nessun `access.accounts`, l'IBAN non si
chiede più da nessuna parte e la riga `consent_request` in `cm_sync_log` — che esisteva solo per
distinguere «IBAN spedito» da «IBAN non spedito» — è stata ritirata insieme al resto della
diagnostica (dump della risposta di Enable Banking compreso). `cm_sync_log` è tornato a essere
il registro delle sincronizzazioni.

Se un conto in whitelist continua a non comparire, restano due cose da guardare, entrambe fuori
dal nostro codice: la **selezione dei conti sulla pagina della banca** (confermare senza
spuntare niente dà lo stesso esito) e il **tipo di conto** — PSD2 copre i conti di pagamento, e
un deposito o un libretto vincolato la banca può non esporlo affatto.

`state.replaceConnectionId` (pulsante *🔁 Rifai il consenso*) dice che il nuovo consenso
sostituisce un collegamento rimasto senza `account_id`: il callback sposta i `fnz_funds` sul
nuovo, gli **eredita `display_name`, `uses` e `owner_person_id`** del vecchio e poi lo cancella.
I due passaggi che contano sono spostare i fondi ed ereditare gli usi: a mano ci si dimentica, e
il fondo resta agganciato a una riga che non sincronizzerà mai, oppure il conto rifatto torna
«da battezzare» e sparisce dagli elenchi dei moduli che lo stavano già usando.

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

Nel valore di un portafoglio rientra anche la **liquidità**: i soldi versati nel fondo
collegato (`fnz_funds.linked_portfolio_id`) e non ancora usati per comprare titoli. Il
portafoglio non ha un conto di cassa — esistono solo `BUY` e `SELL` — quindi si ricava per
differenza: versamenti nominali che fanno quota (`auto`/`confermato`) meno acquisti con
commissioni, più il netto delle vendite, mai sotto zero. **Anche questa formula vive in tutte
e tre le copie** (`computePortfolioCash`, `portfolioCash`, `cashOf`). Un portafoglio può
essere **tutta liquidità e nessun titolo** (il Conto Risparmio): chi somma i portafogli deve
partire dall'elenco dei portafogli, non da quello delle transazioni, o quel valore sparisce
in silenzio da un totale solo. P&L e variazione
giornaliera restano invece sui soli titoli: la liquidità non guadagna né perde, e sommarla al
valore senza sommarla al costo la farebbe comparire come utile il giorno che entra sul conto.

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
Ed è **a scadenza**: una pending di più di 6 ore non è una notifica in viaggio, è una riga che
nessuno sta consumando (telefono spento, APK non installato, overlay negato) — e finché resta lì
zittisce ogni notifica successiva di quel destinatario, per sempre. Passate le 6 ore si butta e se
ne accoda una aggiornata: il contenuto è comunque una fotografia rifatta da capo a ogni run.

⚠️ **Il conto da cui parte tutto è quello spuntato `'cost_analysis'` in `cm_bank_connections.uses`**,
non "quello di Revolut". Fino alla v1.5 la Edge Function lo cercava per `aspsp_name = 'Revolut'`,
cioè per nome della banca: da quando il collegamento parte dal censimento degli istituti quel nome
è quello del catalogo Enable Banking, e rifare il consenso bastava a far sparire il conto dalla
query — con lui il sync e tutte le notifiche, senza una riga da nessuna parte. Ora, quando non si
accoda niente, il perché è scritto: in `cm_sync_log` se il sync non parte proprio, nella risposta
JSON (`notifications: [{who, outcome}]`) e nei log della function per ogni destinatario.

Sul profilo `teresa` nessuna schermata chiede mai il PIN (`Prefs.isInfoOnlyBlock` restituisce sempre
`true`): il PIN è di Salvatore e su quel telefono un blocco rosso sarebbe insbloccabile.

---

## AppSphere nativa — l'unico modulo Android che non è un WebView

`android-app/appsphere-native/` è un progetto Gradle standalone (come `situazione-rosa/` e
`pressure-tracker/`) e **l'unica app Android della repo scritta davvero in nativo**: schermate in
Kotlin/Compose, dati da PostgREST via `supabase-kt`. Tutti gli altri moduli caricano una pagina
Netlify dentro una `WebView`.

**Non sostituisce l'APK WebView, gli si affianca.** `applicationId` è `com.garsal.appsphere`
(contro `com.garsalapps`), quindi i due si installano insieme sullo stesso telefono e leggono lo
stesso database. Per tutto ciò che non è ancora nativo si continua ad aprire quello WebView.

| Cosa | Dove |
|---|---|
| Home a bolle, avvisi, riquadro del totale, login, biometria | `home/`, `MainActivity.kt`, `core/` |
| Catalogo premi (riscossione, gestione, cronologia) | `premi/` |
| App portate | `spuntiamola/`, `eventslog/`, `tasks/`, `tafiri/`, `peso/`, `memo/`, `abituati/` — più `obiettivi/`, **sospesa in home** (riga commentata in `PortedApps.kt`, schermate intatte) |

### ⚠️ Tasks nativo: le RPC valgono anche qui, e i workflow no

`tasks/` porta in nativo **quattro schede**: *Panoramica*, *Gestione*, *Mese*, *Settimana* — le tre
del planner del web più la pagina ✏️ Gestione. Si apre sul Mese. Il **+ galleggiante** apre
`TaskForm`, che crea un task (tutti i tipi tranne i `workflow`, i cui step hanno dipendenze fra
loro e non si riducono a un elenco); da Gestione lo stesso form **modifica** un task che esiste
già, e ne fa una **copia**. Il tipo di un task esistente non si cambia: decide quali colonne quel
task ha, e cambiarlo lascerebbe dietro quelle del tipo di prima — `multiple_dates` su un
ricorrente — che nessuno ripulisce. Scrivere un task è un `insert`/`update` diretto e non una RPC,
e non è un'eccezione alla regola: le RPC governano il ciclo di vita, cioè dove va la prossima
occorrenza, non com'è fatto il task — `saveTask()` nel web fa lo stesso. Restano su `tasks.html`
reminder, storico, impostazioni e l'import di un backup JSON.

La **Gestione** (`tasks/Gestione.kt`) è l'elenco completo con i due riquadri richiudibili del web —
📊 Vista (*espandi tutti*, *raggruppa per* categoria/priorità/tipo) e 🔎 Cerca e filtra (testo,
stato, priorità, categoria, tipo) — il conteggio *Trovati N task* e, su ogni scheda, *Vedi*,
*Modifica*, *Clona*, *Cancella*, e *Riattiva* su terminati e archiviati. ⚠️ Filtri e ordinamento
sono quelli di `applyTaskFilters()` ricalcati in `TasksState.gestione()`: stessi quattro stati vivi
sotto «Attivi», ricerca su titolo **e** descrizione, ordine per prossima occorrenza con chi non ce
l'ha in fondo. Per via di questa scheda `TasksRepository.task()` carica **anche archiviati e
terminati** (prima li scartava): le due voci della tendina che li chiedono sarebbero altrimenti
vuote per sempre.

La **Panoramica** è invece la copia di `renderDashboard()`, sezione per sezione
(`tasks/Panoramica.kt`): ⚠️ SCADUTI, 🎯 OGGI, 📅 PROSSIMI, 🔄 A LIBERA RIPETIZIONE e
👁️ NON IN PANORAMICA, ciascuna nel suo riquadro con la striscia colorata a sinistra, e la scheda
con segno del tipo, data e ora, etichette delle categorie (le prime due, poi «+N»), titolo e i
pulsanti che agiscono subito. **Quali pulsanti compaiono è la regola del web**: *Completa* sempre,
*Fallisci* su tutto tranne i `free_repeat`, *Salta* solo su `recurring`, `simple_recurring` e
`multiple`, che sono gli unici con una prossima occorrenza. Tre cose vanno lette dallo stesso posto
del web, o le due panoramiche divergono in silenzio: `show_in_panoramica` (falso = ultima sezione,
non sparito), `cm_categories.show_in_dashboard` (decide quali gruppi di `free_repeat` si vedono) e
`ts_settings.dashboard_upcoming_days` (quanti giorni guarda *PROSSIMI*, 10 se manca). Un task con
due categorie compare in tutt'e due i gruppi, come là, ma il conteggio della sezione resta quello
dei task.

Le file della scheda — segno e data, etichette, titolo, pulsanti — stanno **ognuna su una
riga sola che scorre col dito** (`RigaScorrevole`), e non vanno a capo. È l'eccezione voluta alla
regola sui caratteri di sistema grandi: andando a capo, con l'ingrandimento alto tre pulsanti
diventano tre righe e il titolo altre tre, e in uno schermo ci sta un task e mezzo. Niente è
nascosto — il testo tagliato dal bordo si trascina — **a patto che quel che conta di più stia a
sinistra**: la data prima delle etichette, *Completa* prima di *Salta*. Lo scorrimento è
orizzontale e quello della lista verticale, quindi i due gesti non si contendono niente e il tocco
sulla scheda apre comunque il dialogo col dettaglio, i punti di ogni azione e l'eliminazione.
I tre pulsanti sono **larghi uguali**, e quella larghezza si **misura** (`larghezzaPulsanti()`,
`rememberTextMeasurer`) invece di essere una costante in `dp`: coi caratteri di sistema grandi una
costante o taglia «Completa», o lascia «Salta» in un pulsante largo il doppio del necessario.

Una differenza di sostanza col web: un **ricorrente senza `next_occurrence_date`** ripiega su
`start_date`, quindi finisce fra gli scaduti; là quel ripiego sta solo in `isTaskDueToday`, e un
task così non compare in nessuna sezione.

Le due viste calendario non sono la copia pixel per pixel di `renderMonthView` e `renderWeekView`,
e non possono esserlo: quelle scrivono i titoli dentro le celle di una griglia 7×6 e in sette
colonne da 150 px con scorrimento orizzontale, che coi caratteri di sistema grandi sono illeggibili
prima ancora di essere tagliati. Nel **mese** la cella porta il numero del giorno e dei pallini
colorati, uno per task, e il titolo si legge toccando il giorno; la **settimana** è un elenco
verticale di sette giorni invece di sette colonne. **Quello che resta identico è la regola**:
`TsTask.cadeIl()` ricalca `getTasksForDate()` colonna per colonna, la settimana parte di lunedì,
il mese ha sempre sei righe e le fasce orarie sono le stesse quattro.

Due differenze di sostanza, volute:

- i task **`multiple` compaiono**. Nel web passano da `JSON.parse(task.multiple_dates)` dentro un
  try/catch: quando la colonna è già una lista quella chiamata solleva, il catch la scarta, e quei
  task non si vedono mai in calendario;
- **niente doppioni**: il web concatena task vivi e storico senza guardarli, quindi un task
  completato oggi che ha ancora oggi come prossima occorrenza compare due volte.

Il calendario legge anche `ts_history`: senza, un mese indietro sarebbe vuoto, perché la prossima
occorrenza di un ricorrente si sposta in avanti e le volte già fatte non stanno più in `ts_tasks`.

- **Il ciclo di vita passa solo dalle RPC**, come sul web: `TasksViewModel` chiama
  `task_complete` / `task_skip` / `task_fail` e poi rilegge. Nessun calcolo di prossima
  occorrenza in Kotlin — sarebbe una seconda regola per lo stesso task, diversa a seconda
  dell'app da cui lo tocchi.
- **I `workflow` si vedono e si completano**, come tutti gli altri: quella parte la fa la RPC,
  che sa già come trattare i loro step.
- **`ts_tasks` si legge come `JsonObject`, non come `data class` serializzata.** La tabella non
  sta in nessuna migration e alcune colonne sono ambigue di natura — `recurring_days_of_week` è
  `text[]` *o* `integer[]`, `recurring_day_of_month` può essere una lista o un numero solo. Con
  una data class, una colonna del tipo sbagliato non darebbe un task storto: farebbe fallire la
  decodifica dell'intera lista, cioè la schermata vuota.
- **I giorni della settimana sono numerati come `extract(dow)` di Postgres** (0 = domenica), che
  è quello che `task_next_recurring_date` confronta. Il form li mostra da lunedì e li salva con
  quella numerazione: se si toccasse una delle due parti senza l'altra, le ricorrenze
  settimanali scatterebbero il giorno sbagliato senza nessun errore.
- I task **riservati non compaiono**: la modalità nascosta del web qui non c'è, e una schermata
  che si apre senza chiedere niente è il posto sbagliato per mostrarli.
| Build APK | `.github/workflows/build-appsphere-native.yml` → `releases/AppSphereNative-latest.apk` |

### ⚠️ Il pulsante di download deve dire quale versione scarica

Il nome del file è fisso (`-latest.apk`), quindi da fuori una build vale l'altra. L'11 agosto 2026
la 1.0.4 è stata scaricata e installata **al posto della 1.0.5 pubblicata pochi minuti dopo**, e il
sintomo — login fermo dopo la scelta dell'account Google — era identico al difetto che la 1.0.5
aveva appena chiuso: dal solo comportamento non c'era modo di distinguere «la correzione non
funziona» da «la correzione non è a bordo».

Il workflow scrive quindi accanto all'APK una `releases/AppSphereNative-latest.json` (versione,
`versionCode`, data di build, commit, peso, SHA-256) e il pannello di `comandi.html` la mostra sul
pulsante, aggiungendo `?v=<versione>` al link perché il browser non riproponga il pacchetto già
scaricato. La scheda si aggiorna **solo insieme all'APK** — `builtAt` cambia a ogni run, e
pubblicarla da sola annuncerebbe una build nuova per un pacchetto identico — e `netlify.toml` la
tiene fuori dalla cache, altrimenti la pagina annuncerebbe una versione e ne farebbe scaricare
un'altra.

Dal telefono la controprova è la schermata di login, che stampa `Versione nativa · v…`, e da
dentro l'app il ⚙️ in home: `DialogoAggiornamento` legge la stessa scheda, confronta il suo
`versionCode` con `BuildConfig.VERSION_CODE` e apre il download nel browser. È l'unico modo per
sapere **prima** di scaricare se c'è davvero qualcosa di nuovo — la domanda che l'11 agosto non
aveva risposta da nessuna parte.

### ⚠️ Ta Firi? nativo: il punteggio sta nella RPC, e il promemoria si scrive da qui

`tafiri/` porta in nativo le due voci della sidebar del web, che qui sono due schede —
**Dashboard** (banner del check-in di oggi + sfide attive) e **Storico** (sfide concluse, con
badge, giorni fatti e punteggio finale) — più il **+ galleggiante** che apre `TaFiriForm`, lo
stesso modale di `ta-firi.html`: titolo, obiettivo, inizio, durata, orario di check-in, punti in
palio e schema di punteggio. I punti guadagnati stanno nella top bar invece che nel riquadro
della sidebar.

Le tre regole che **sono** la funzionalità, e che vanno cambiate nelle due implementazioni
insieme:

- **il punteggio finale non si calcola nel client, mai.** *Tutto o niente* e *proporzionale*
  vivono in `sf_finalize_challenge`, che il ViewModel chiama all'apertura su ogni sfida con
  l'ultimo giorno passato e poi rilegge l'elenco. È la stessa regola delle RPC dei task, per la
  stessa ragione: due implementazioni dello stesso punteggio sono due punteggi diversi il giorno
  che una delle due cambia;
- **il check-in di oggi e la correzione di un giorno passato sono due cose diverse.** Il banner
  passa da `sf_checkin_set`, che oltre a scrivere il giorno **sposta il promemoria al giorno
  dopo** (o lo cancella se la sfida è finita); toccare una pallina della griglia gira fra i tre
  stati con un `update` diretto e **non tocca il promemoria** — spostarlo indietro perché si è
  corretto un giorno di tre giorni fa farebbe suonare la sveglia nel passato. È la stessa
  divisione fra `submitTodayCheckin` e `cycleCheckin` nel web;
- **la regola Smart Block la scrive anche il nativo.** Alla creazione, alla modifica (riagganciata
  al prossimo giorno ancora da segnare, non alla data di partenza, che di solito è passata) e alla
  cancellazione, più la rete di sicurezza all'avvio per le sfide attive che ne sono prive —
  `upsertSmartBlockRule` / `ensureSmartBlockRules` riga per riga. Senza questa parte una sfida
  creata dal telefono esisterebbe e non chiederebbe mai niente. Subito dopo si chiama
  `fill-notification-queue` (è l'unica Edge Function invocata dall'APK, e la sola ragione per cui
  `functions-kt` è fra le dipendenze): il cron gira ogni sei ore, e una sfida che parte stasera
  alle 20 non può aspettarle.

`checkin_time` è un orario di Roma e `due_at` un istante UTC: la conversione qui la fa `ZoneId`,
mentre il web se la calcola a mano in `dateTimeToRomeUTC` con le ultime domeniche di marzo e
ottobre scritte in JavaScript. Stesso risultato, e da questa parte il cambio d'ora non è una cosa
da ricordarsi.

Due differenze di forma, volute, entrambe per i caratteri di sistema grandi: sulla scheda ogni
cosa sta **su una riga sua** (nel web titolo, badge e icone condividono una riga che
all'ingrandimento si sfalda da sé, con le icone spinte a capo per prime — cioè proprio quelle che
servono per toccare), e le palline della griglia hanno un diametro che segue `fontScale` invece
dei 32 px fissi del CSS, altrimenti il numero dentro verrebbe tagliato.

### ⚠️ «Ti pisasti?» nativo: la pesata sì, l'obiettivo no

`peso/` porta in nativo **le tre cose che si fanno col telefono in mano**:
segnare la pesata (il pulsante ⚖️, che scrive in `ps_weight_tracking`), la **Tabella**
giorno per giorno e il **Grafico** dell'andamento. La scheda *Oggi* apre con la domanda che dà
il nome all'app — *oggi ti sei pesato?* — e sotto i **sei riquadri della pagina**, nello stesso
ordine: Minimo oggi, Target oggi, Mancano al target, Kg alla fine, Punteggio, Punti oggi.

**Restano su `weight-quest.html`**, che è dove si fanno da seduti: creare e modificare obiettivi e
traguardi, le statistiche, *genera dieta*, la sincronizzazione con Google Fit e con la bilancia, e
il gratta e vinci dei premi — che vive in `localStorage` ed è **per dispositivo**, quindi da qui
non avrebbe niente da mostrare. Da nativo l'obiettivo si **sceglie** (per guardarne un altro) ma
non si tocca.

Le regole di calcolo stanno tutte in `peso/PesoRegole.kt`, ricalcate una per una dalla pagina, e
**vanno cambiate nelle due implementazioni insieme**:

- **il target di un giorno è interpolato fra i traguardi** (`getInterpolatedTarget`): con meno di
  due traguardi non esiste e diventa un trattino, prima del primo e dopo l'ultimo vale il valore
  estremo, in mezzo è lineare arrotondata a due decimali;
- **i giorni senza pesata contano lo stesso.** Vengono ricostruiti interpolando fra la pesata prima
  e quella dopo (`getInterpolatedWeightFromSeries`) e prendono punti come gli altri: toglierli
  cambierebbe il punteggio. Sono marcati «giorno ricostruito» invece di sembrare una pesata vera;
- **il confronto peso/target si fa a un decimale** (`Math.round(x*10)/10`): 74,04 contro un target
  di 74,0 è una giornata vinta, e a piena precisione sarebbe persa;
- **il target si congela nella riga** al momento della pesata (`target_weight`) e non si ricalcola
  mai: spostare i traguardi domani non deve riscrivere il giudizio sui giorni già passati;
- **`timestamp` (millisecondi di giorno + ora) è la chiave** della pesata: l'upsert ci si appoggia,
  quindi ripesarsi alla stessa ora riscrive la riga di prima invece di aggiungerne una.

Due differenze di forma. La **tabella non è una tabella**: sei colonne coi caratteri di sistema
grandi si tagliano o vanno a capo ognuna per conto suo, quindi ogni giornata è una scheda con la
data e il peso in cima. E il **grafico è disegnato a mano su un `Canvas`** invece che con Chart.js:
restano le due curve che si guardano davvero — peso e target — perché un grafico fitto di etichette
su uno schermo di telefono è illeggibile prima ancora di essere utile.

`ps_weight_tracking` e `ps_objectives` non stanno in nessuna migration e si leggono come
`JsonObject`, non come `data class` serializzate: è la stessa scelta di `ts_tasks` e per la stessa
ragione — `id` può essere un numero o un uuid e `weight` un intero o un decimale, e con una data
class una colonna del tipo inatteso non darebbe un campo storto ma la schermata vuota.

### ⚠️ Memo nativo: il contenuto è HTML, e il giro deve chiudersi

`memo/` porta in nativo le schede di `memo.html`: elenco con **ricerca su titolo e testo**, filtro
per categoria, ordinamento (ultime modificate / ultime create / titolo), il filtro 📌 *In evidenza*
che sul web è una pagina a sé, il **dettaglio in lettura** e la **modifica** con foto e OCR. Le
tabelle sono le stesse (`mm_cards`, `mm_card_categories`, `mm_images`) più le categorie condivise
`cm_categories`, e si leggono come `JsonObject`: non stanno in nessuna migration — nascono dal SQL
che la pagina mostra in Impostazioni — quindi vale la stessa scelta di `ts_tasks`.

**Il punto delicato è `content`, che è HTML** scritto da un `contenteditable` con `execCommand`. Qui
un contenteditable non c'è, e il giro è in due tempi (`MemoHtml`): l'HTML diventa **testo con
marcatori** (`**grassetto**`, `*corsivo*`, `__sottolineato__`, `~~barrato~~`, `#`/`##` per i
titoli, `- ` e `1. ` per gli elenchi, `---` per la linea, `[testo](url)`, `![](url)` per le
immagini incorporate), si modifica quello, e al salvataggio **si ritorna in HTML**. I pulsanti
della barra infilano i marcatori attorno a quel che è selezionato e l'occhio 👁 mostra il
risultato vero; in lettura si rende l'HTML com'è (`AnnotatedString.fromHtml`), non i marcatori.

⚠️ **Ogni voce della barra del web deve avere il suo marcatore qui.** Aggiungendone una là senza
aggiungerla qui, una scheda modificata dal telefono perde quella formattazione **in silenzio** —
il giro non si chiude più. Un tag sconosciuto invece non fa danni: si scarta il tag e si tiene il
testo, come `stripHtml()`.

Le altre regole copiate riga per riga, da cambiare nelle due implementazioni insieme:

- **le categorie si riscrivono da capo a ogni salvataggio** (`delete` di tutte le righe della
  scheda e `insert` di quelle scelte, come `saveCard()`): senza la cancellazione una categoria
  tolta resterebbe attaccata per sempre;
- **cancellando si tolgono prima i file dal bucket, poi la riga**: `mm_images` sparisce da sé per
  cascata, ma il bucket quel vincolo non lo conosce, e nell'ordine inverso i file resterebbero
  senza più nessuna riga che dica dove sono;
- **una scheda vale se ha il titolo *oppure* il contenuto**, non per forza tutt'e due;
- **le foto si caricano dopo la scheda**, perché il percorso nel bucket è
  `utente/scheda/file` e per una scheda nuova l'id non esiste prima; il tetto è 5 MB a foto come
  nel web.

L'OCR è **ML Kit**, come nell'APK WebView (là passa da `AndroidBridge.performOcr`, qui si chiama
direttamente), ma nella variante **non incorporata**
(`com.google.android.gms:play-services-mlkit-text-recognition`): il modello lo tiene Google Play
Services e lo scarica all'installazione, grazie al `meta-data com.google.mlkit.vision.DEPENDENCIES`
nel manifest. ⚠️ La variante `com.google.mlkit:text-recognition`, che il modello se lo porta dentro
per tutte e quattro le ABI, ha portato l'APK da 30 a **73 MB** in un colpo solo (v1.0.18, ritirata
subito): è lo stesso difetto di `material-icons-extended` con un'altra faccia, e con l'APK
committato a ogni build ci va di mezzo anche il peso della repo. Il codice è identico nelle due
varianti — cambia solo dove sta il modello. Il ripiego su Tesseract non c'è — serve al
browser desktop, che ML Kit non ce l'ha. Il testo estratto si accoda dopo una linea `---`, come sul
web. Fissare una scheda dall'elenco (📌) **non passa dalla conversione**: riscrive `content` com'è,
o un giro andata-e-ritorno si porterebbe via la formattazione senza che nessuno abbia toccato il
testo.

Restano su `memo.html` la tavolozza del colore personalizzato (qui ci sono i sette campioni) e la
scala dei caratteri delle schede, che su Android la decide già il sistema.

#### ⚠️ Liste e diari: il nativo non li conosce ancora

`mm_cards.kind`, `mm_list_items`, `mm_diary_metrics` e `mm_diary_entries` esistono **solo nel web**.
Sul nativo una lista e un diario compaiono nell'elenco come **note qualunque**: si vede il titolo e
il contenuto — che per un diario è lo scopo — ma non le voci, non le misure e non le registrazioni.

Il dato però **non si rovina**, ed è la ragione per cui la cosa si può lasciare così finché serve:
`MemoRepository.salva()` fa un `update` dei soli campi che conosce (`title`, `content`, `pinned`,
`color`, `updated_at`, `user_id`), quindi `kind` sopravvive a una modifica fatta dal telefono, e le
righe figlie non le tocca nessuno. Le due cose da non fare senza portare anche il nativo:

- **non far scrivere `kind` al nativo** con un `update` di riga intera: azzererebbe il tipo di una
  scheda e lista e diario diventerebbero note, con le loro righe figlie ancora lì e più nessuno che
  le mostri;
- **una scheda creata dal nativo nasce `'nota'`** per via del `DEFAULT`, che è il comportamento
  giusto — ma vuol dire che da lì una lista non si può creare, non che il tipo sia opzionale.

Portarle vorrebbe dire tre schermate (voci spuntabili, misure, raccolta delle misure) e le stesse
regole di qua: la spunta ottimistica con rollback, le misure aggiornate riga per riga e mai
ricreate, e la misura non toccata che **non** finisce in `measures`.

### ⚠️ Abituati nativo: le regole stanno nel database, non in Kotlin

`abituati/` porta in nativo le abitudini di `habit-tracker.html`: la scheda
🎯 **Oggi** con le spunte (fatto / fallito / saltato, e una riga per orario sulle abitudini a più
slot), 📋 **Tutte** con creazione, modifica ed eliminazione, 📦 **Archivio** degli stack finiti, più
le due cerimonie del web — lo stack vinto e il game over, ciascuna con *Ricomincia da una data*
oppure basta. Restano di là statistiche, categorie, promemoria e impostazioni.

**Qui non c'è nessuna regola.** Streak, jolly, giorni mancati e chiusura degli stack vivevano solo
nel JavaScript della pagina; riscriverli in Kotlin avrebbe voluto dire due copie della stessa
formula, e in ballo ci sono punti e archivi. Sono invece scesi nel database
(`20260815120000_hb_regole_rpc.sql`), che è la stessa scelta di `task_complete` / `task_skip`:

| Funzione | Cosa fa |
|---|---|
| `hb_streak` / `hb_giorni_fatti` / `hb_fallimenti` | Le tre misure. Sola lettura, `SECURITY INVOKER` perché la RLS resti in mezzo |
| `hb_set_completion` | **L'unica strada per segnare un periodo**: scrive la riga e ricalcola i jolly |
| `hb_reconcile` | Il giro che il web fa a ogni disegno della dashboard: periodi passati senza riga → `missed`, jolly riallineati, stack completati archiviati, stack scaduti chiusi. Torna cosa è successo, perché il client mostri le sue cerimonie |
| `hb_clona` / `hb_chiudi_stack` | Le due uscite del game over: *Ricomincia* e *Interrompi* |

Tre cose che *sono* la funzionalità:

- **la fonte di verità sono i completamenti.** `current_failures` non si incrementa e non si
  decrementa: si **ricalcola** da `hb_completions` a ogni scrittura. È quello che il JS già faceva
  in `checkMissedDays`, ed è ciò che permette a due client di segnare lo stesso giorno senza
  contare due volte lo stesso jolly;
- **«oggi» lo passa il client** (`p_oggi`), come `p_today` in `task_complete`: il database sta in
  UTC e fra mezzanotte e le due sarebbe ancora ieri;
- **lo stack che ha esaurito i jolly si segnala e basta.** La chiusura è una cerimonia con una
  scelta dentro, e la scelta la fa l'utente: `hb_reconcile` la annuncia, `hb_chiudi_stack` la
  esegue dopo.

⚠️ **Il giro non è ancora chiuso da tutt'e due le parti**: `habit-tracker.html` continua a usare la
sua copia in JavaScript (`checkMissedDays`, `checkCompletedStacks`, `checkExpiredStacks`,
`updateStreak`, `handleFailure`, `restoreJolly`). Oggi le due strade dicono la stessa cosa — il SQL
è stato ricalcato da quel JavaScript riga per riga — ma finché la pagina non chiama le RPC, **una
modifica alle regole va fatta in tutt'e due**. Il passaggio del web è il pezzo che manca, ed è
quello che rende vera la frase «una regola sola».

### Cosa compare in home: il registro `PortedApps`

Il web mostra tutte le righe attive di `cm_apps`; qui si mostrano **solo le app che esistono in
nativo**, decise da `home/PortedApps.kt`. Titolo, descrizione, colore e punteggio continuano ad
arrivare dal database (`cm_apps` + RPC `run_score_query`): il registro decide soltanto *se* la
bolla si disegna e *dove* porta il tap. **Portare una quarta app = una riga lì più le sue
schermate.** I valori di ripiego servono perché non tutte le righe di `cm_apps` nascono da una
migration — `events-log.html` non compare in nessun file SQL — e senza quelli la bolla sparirebbe
in silenzio.

Le bolle sono disegnate come sul web: **cerchio col bordo nero** (`border: 4px solid #111`) e
dentro il nome col **punteggio** sotto, che a zero non si scrive — una bolla al minimo con uno «0»
sotto sembra rotta, non vuota.

### ⚠️ Il totale in home e i premi: la stessa cifra da tutt'e due le parti

In basso a sinistra c'è il **riquadro rosso del totale** (`PannelloTotale`, il `#score-panel` del
web), e toccarlo è l'unico modo per arrivare al **catalogo premi** — di qua come di là. Il numero
è **guadagnati meno spesi**, mai sotto zero: `HomeState.totaleNetto` qui, `updateScorePanel()` sul
web. Se le due formule divergono, un premio comprabile da una parte non lo è dall'altra.

⚠️ **Il lordo è la somma dei punteggi di *tutte* le app attive, non delle sole app portate.**
`HomeRepository.carica()` fa girare la `score_query` di ogni riga di `cm_apps`, comprese quelle che
qui non hanno una bolla: quei punti sono guadagnati lo stesso, e un premio costa uguale da tutt'e
due gli APK. Sommare le sole bolle darebbe un saldo diverso a seconda di che app si è aperta.

Le bolle **scansano il riquadro** (`BubbleLayout.Pannello`, portato da `pushFromPanel`), e
l'ingombro si **misura** (`onSizeChanged`) invece di essere un rettangolo scritto nel codice: coi
caratteri di sistema grandi quel riquadro è alto il doppio, e una misura fissa sarebbe sbagliata
proprio dove serve.

Il catalogo (`premi/`) ha le tre viste del modale web — premi da ritirare, 🛠 gestione,
📋 cronologia — sulle stesse tabelle `cm_rewards` e `cm_rewards_log`, lette come `JsonObject` e non
come `data class` serializzate (non stanno in nessuna migration: stessa scelta di `ts_tasks`). Le
due regole che **sono** la funzionalità, da cambiare nelle due implementazioni insieme:

- **il riscatto è in due passi, in quest'ordine**: prima la riga in `cm_rewards_log` — che è quella
  che scala i punti — poi l'aggiornamento del premio. Al contrario, una rete che cade lascerebbe un
  premio segnato come ritirato senza che nessun punto sia stato speso;
- **una tantum e ripetibile finiscono diversamente**: il primo esce dal catalogo (`is_redeemed`),
  il secondo resta e **rincara** di `points_per_use` a ogni uso, contandoli in `use_count`.

Gli avvisi in home hanno per ora **due fonti, Spuntiamola e Ta Firi?**: le altre quattro del web
(decisioni, task urgenti, totale portafogli, abitudini) porterebbero a schermate che qui non
esistono, e un avviso che non apre niente è peggio di nessun avviso. Quello di Ta Firi? elenca le
sfide in corso oggi con il loro orario di check-in, ed è la copia di `loadHomeAlertChallenges` —
compreso il fatto che **compare anche se la sfida di oggi è già spuntata**: lì è un promemoria di
cosa c'è in ballo, e la domanda vera la fa il banner dentro l'app.

### ⚠️ Deep link: schema proprio, pagina-ponte https, browser completo

Il login usa **`garsalnative://oauth`**, non `garsalapps://oauth`: con lo stesso schema Android
chiederebbe a ogni login quale delle due app aprire.

Ma **quello schema non è il `redirect_to`**, e non va messo in whitelist: Supabase non lo vede
mai. Il giro che funziona ha tre pezzi, tutti e tre già pagati una volta nell'APK WebView e
reimparati dal nativo l'11 agosto 2026 (v1.0.6):

1. **`redirect_to` è una pagina https**, `oauth-callback-native.html` — è lei che va fra i
   Redirect URLs. Chrome **blocca il salto automatico** da una pagina di login a uno schema
   custom: dando `garsalnative://oauth` come `redirect_to`, il login finisce e resta lì nel
   browser, senza tornare nell'app e senza un errore da nessuna parte.
2. **La pagina-ponte non fa auto-redirect**: rilancia l'app solo col **tap sull'ultimo pulsante**.
   L'auto-navigazione verso lo schema custom viene rimbalzata da Chrome e produce lo stesso
   sintomo; un gesto dell'utente passa sempre. Sta scritto anche in `oauth-callback.html`, che
   ci era già arrivata.
3. **Il login si apre nel browser completo** (`Intent.ACTION_VIEW` + `CATEGORY_BROWSABLE` +
   `FLAG_ACTIVITY_NEW_TASK`), **non in una Custom Tab**: da una Custom Tab aperta dall'app quel
   tap non rilancia l'app. Per questo `AuthRepo.loginConGoogle` costruisce l'URL da sé invece di
   chiamare `signInWith(Google)`, che aprirebbe una Custom Tab.

`oauth-callback-native.html` e `oauth-callback.html` sono **gemelle a meno dello schema**
(`garsalnative://` contro `garsalapps://`). Due pagine e non una con un parametro perché ciascuna
va in whitelist esattamente com'è, e una query string nella whitelist di Supabase è un modo in più
di sbagliare. Se modifichi una delle due, guarda anche l'altra.

⚠️ Nota storica: fino alla 1.0.5 un commento in `Supabase.kt` diceva che la Custom Tab era
«l'unico modo per cui Google non rifiuti il login come user agent non sicuro», attribuendola
all'APK WebView. Leggeva male quel file: l'APK WebView usa il **browser completo** e tiene la
Custom Tab solo come ripiego. Quello che Google rifiuta è il WebView incorporato, non il browser
di sistema.

### ⚠️ Il rientro dal login non passa da `parseFragmentAndImportSession`

Quella funzione di supabase-kt fa il lavoro dentro `authScope`, lo scope interno della libreria,
che è un `CoroutineScope(dispatcher)` — quindi con un `Job()` normale, **non** un `SupervisorJob`.
La prima cosa che fa lì dentro è una chiamata di rete (`retrieveUser`), nell'istante esatto in cui
l'app sta rientrando in primo piano dal browser: se quella tira un'eccezione, l'eccezione risale al
Job e **cancella `authScope` per tutta la vita del processo**. Da lì in poi nessun import di
sessione, nessun rinnovo del JWT e nessuna rilettura dall'archivio vanno più a segno, senza che
venga stampato niente — l'app resta sul pulsante di login e ripremerlo non cambia nulla, perché il
token torna e non lo raccoglie più nessuno.

`AuthRepo.completaConFragment` legge quindi il fragment con `parseSessionFromFragment` (pubblica e
senza rete) e chiama `importSession` **dallo scope di `AuthRepo`**, dentro un try/catch.
`retrieveUserForCurrentSession` si chiama dopo e il suo esito non conta: id ed email si leggono dal
JWT (`Jwt.claim`, claim `sub`), che è lo stesso valore che le RLS vedono come `auth.uid()`.

Due corollari, entrambi già in codice e da non disfare:

- **`enableLifecycleCallbacks = false`** in `Supabase.kt`. L'osservatore che supabase-kt installa da
  sé riporta `sessionStatus` a `Initializing` a ogni `onStop` — e aprire la Custom Tab del login *è*
  andare in background, come lo è il PIN chiesto dalla biometria — e al rientro rilegge la sessione
  dall'archivio **in parallelo** all'import del token appena ricevuto. Il rinnovo del JWT non ne ha
  bisogno: il job parte da `importSession` e dorme fino all'80 % della scadenza, come
  `startTokenRefresh()` sul web.
- **Il deep link si consuma una volta sola.** `getIntent()` continua a restituire quello di partenza
  per tutta la vita dell'Activity: a ogni ricreazione `onCreate` si ritroverebbe lo stesso
  `access_token`, ormai scaduto o già speso, e lo rimetterebbe al posto di una sessione buona.
  `gestisciDeepLink` azzera `intent.data` appena l'ha letto.

### ⚠️ `material-icons-extended` non va reintrodotto

Quel pacchetto contiene **migliaia di icone compilate come codice Kotlin**: senza
minificazione finiscono tutte nel DEX, e da solo portava l'APK a **51 MB** (~50 dei quali di
bytecode) contro i ~10 di adesso. Un APK così si installa male — la verifica della firma e
l'ottimizzazione costano un multiplo del pacchetto, e su un telefono pieno o in risparmio
energetico l'installazione fallisce senza dire perché.

Le icone in uso sono quattro (`ArrowBack`, `Close`, `Refresh`, `Settings`) e stanno tutte in
`material-icons-core`, che arriva già con `material3`. Se ne serve una che lì non c'è, si
disegna a mano come `CerchiOlimpici` in `core/Logo.kt`. Il workflow avvisa se l'APK supera i
25 MB, così la cosa non può ripresentarsi in silenzio.

### ⚠️ Kotlin 2.1.20, diverso dagli altri moduli

`supabase-kt 3.1.4` è compilata con stdlib 2.1.20 e i suoi metadata non si leggono con il 2.0.21
usato dagli altri progetti Android. **Le due versioni sono agganciate**: aggiornando supabase-kt va
guardata la sua `kotlin-stdlib` e allineato il `build.gradle` di root.

### ⚠️ Sette app ora esistono in due implementazioni

`spuntiamola.html`, `obiettivi.html`, `events-log.html`, `ta-firi.html`, `weight-quest.html`,
`memo.html` e `habit-tracker.html` hanno un gemello Kotlin che lavora sulle
**stesse tabelle e sugli stessi campi**. È voluto — si spunta un giorno dal nativo e lo si ritrova
sul web con la sua emoji — ma non è gratis: **cambiare le regole di una senza l'altra le fa
divergere in silenzio**, esattamente come per lo snapshot del patrimonio e la vista Spese Famiglia.
I punti dove la regola *è* la funzionalità, e non un dettaglio:

- **Spuntiamola** — la chiusura della stecca scrive **prima** in `sp_stecche` e cancella **dopo**
  (`SpuntiamolaRepository.chiudiStecca`, come `dbCloseStecca()`); le spunte sono ottimistiche con
  rollback; frasi, emoji e messaggi dei traguardi sono copiati parola per parola.
- **Obiettivi** — progresso e media pesata della rubrica restano nelle RPC
  (`ob_objective_progress`, `ob_record_measurement`); per `kind='rubric'` il client **non calcola e
  non invia mai il valore**. Le due barre non si fondono mai in una media.
- **Events Log** — le tabelle `el_*` non sono in nessuna migration: le colonne dei `data class`
  sono ricavate da come `events-log.html` le scrive. Gli eventi `DA_SELECT` registrano **solo se il
  conteggio è cresciuto** rispetto all'ultimo `count:N`. Il **registro mostra solo il gruppo
  scelto** in tutt'e due: `el_logs` non porta il gruppo, quindi il filtro passa per gli id degli
  eventi di quel gruppo (`logDelGruppo` nel nativo, le stesse due righe in `renderLogPage()`).
  Conseguenza in entrambe: la riga di un evento cancellato non sta in nessun gruppo e non si vede
  più da nessuna parte, pur restando sul database e nel totale dei punti. Differenza voluta: il
  web si ferma agli 8 più recenti, il nativo li elenca tutti perché lì la lista scorre.
- **Ta Firi?** — il punteggio finale resta in `sf_finalize_challenge`; il check-in di oggi passa
  da `sf_checkin_set` e la correzione di un giorno passato no; la regola Smart Block si scrive da
  tutt'e due. Dettagli nella sezione qui sopra.
- **Ti pisasti?** (`weight-quest.html`) — target interpolato fra i traguardi, giorni senza pesata
  ricostruiti e contati lo stesso, confronto peso/target **a un decimale**, `target_weight`
  congelato nella riga. Il nativo porta solo pesata, tabella e grafico: obiettivi, statistiche,
  dieta, sync e premi restano di là. Dettagli nella sezione qui sopra.
- **Memo** — ⚠️ **liste e diari esistono solo nel web**: sul nativo compaiono come note qualunque,
  senza voci né misure né registrazioni. Il tipo sopravvive comunque a una modifica dal telefono,
  perché il nativo aggiorna i soli campi che conosce. Il contenuto è **HTML**: il nativo lo
  converte in marcatori per modificarlo e lo
  riconverte salvando (`MemoHtml`), quindi ogni voce della barra del web deve avere il suo
  marcatore di qua. Categorie riscritte da capo a ogni salvataggio, file del bucket cancellati
  prima della riga. Dettagli nella sezione qui sopra.
- **Abituati** — è l'unica dove le regole **non** sono duplicate: streak, jolly, giorni mancati e
  chiusura degli stack stanno nelle RPC `hb_*`, che il nativo chiama già e la pagina web deve
  ancora cominciare a chiamare. Dettagli nella sezione qui sopra.

(`tasks.html` è l'ottava, ma ha una sezione tutta sua: le RPC del ciclo di vita.)

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
- **Premio cibo alle soglie di peso**: ogni stellina accesa (`.ms-th.reached`) dà, oltre ai punti,
  un premio da scoprire **grattando un gratta e vinci** — 🍰 Torta Savoia, 🥐 Cannolo, 🍕 Pizza,
  🍫 Tavoletta di cioccolata, 🍪 Tazza di biscotti, fotografie in `risorse/premi/*.jpg`
  (quadrate, 460 px). Sotto la patina d'argento (un `<canvas>` disegnato da `buildScratchFoil()`
  e consumato in `destination-out`) c'è **già** la foto del premio: il sorteggio avviene
  all'apertura del biglietto, grattare scopre e basta. Il canvas si ridimensiona **ad ogni
  apertura e solo a overlay visibile** — prima il biglietto è 0×0 e la patina verrebbe stesa su
  niente. Si scopre da sé oltre il 55 % grattato (42 % se si alza il dito), e il pulsante
  *👆 Scopri tutto* fa la stessa cosa: serve da scorciatoia e da ripiego se il tocco non passa.
  Finché non la si tocca la stellina resta **evidenziata in
  rosso col 🎁 che pulsa**; dopo l'estrazione mostra l'emoji del premio vinto e ritoccandola lo
  rimostra già scoperto. Il biglietto di prova non ha più un pulsante: la modalità resta in
  `openPrizeDraw(null, true)`, richiamabile dalla console, e non salva niente.
  I premi stanno in **localStorage** (`wq_prizes_<objective id>`), come i punti dei traguardi
  (`wq_mpts_<id>`): nessuna tabella nuova, quindi il premio è **per dispositivo** — chi apre
  l'app da un altro telefono si ritrova la stellina di nuovo da estrarre. Spostarlo sul DB
  vorrebbe dire una colonna/tabella nuova, e quelle si chiedono prima.

### `memo.html` — Memorandum
- Schede con testo formattato, foto con OCR, categorie condivise, colore e 📌 in evidenza.
- **Tre tipi di scheda** (`mm_cards.kind`), scelti col + galleggiante, che apre un popup invece di
  creare subito una nota:
  - **📄 Nota** — quello che c'era prima, e resta il default;
  - **☑️ Lista** — la nota diventa facoltativa e la scheda porta delle voci spuntabili
    (`mm_list_items`);
  - **📊 Diario** — il contenuto è lo **scopo** («perché sto misurando»), e la scheda porta le
    **misure** da raccogliere (`mm_diary_metrics`) e le **registrazioni** (`mm_diary_entries`).
    Una misura è una **scala** (minimo e massimo), un **numero** libero con unità, un **sì/no** o
    una **scelta** fra opzioni configurabili.
- **Una nota si apre in modifica, una lista e un diario no**: hanno una vista propria
  (`openListView` / `openDiaryView`), perché la cosa che si fa più spesso su di loro — spuntare una
  voce, aggiungere una registrazione — non è modificare la scheda. All'editor si arriva da lì con
  *✏️ Modifica scheda*. `openCard()` è il bivio.
- **Tre Tab nella barra laterale, una per tipo** — 📄 Note, ☑️ Liste, 📊 Diari — accanto a
  📌 Fissa e ⚙️ Impostazioni. Non esiste una vista che li mescola: si apre sulle Note, ogni Tab ha
  la sua ricerca, il suo ordinamento e il suo filtro categoria (i conteggi delle categorie seguono
  il tipo aperto), e il **+ crea una scheda del tipo della Tab** invece di chiedere quale.
  **📌 Fissa è l'unica che attraversa i tre tipi**, ed è la ragione per cui il badge del tipo resta
  sulla scheda anche ora che ogni Tab ne mostra uno solo.
- **Un diario può essere 🙈 riservato** (`mm_cards.riservato`), con la spunta nella sua
  definizione. È la stessa modalità nascosta di `events-log.html` e del launcher, tre stati:
  spenta si vedono solo le schede normali, accesa si vede tutto, accesa col 👁 si vedono **solo**
  le riservate. ⚠️ **Il filtro sta nella query, non nel rendering**: fuori dalla modalità nascosta
  la riga non viene proprio letta — nasconderla a schermo la lascerebbe in chiaro a chiunque apra
  gli strumenti del browser. Vale anche per 📌 Fissa, che quindi non la mostra.
- La modalità la accende **AppSphere, non questa pagina**: arriva da `sessionStorage.hidden_mode`
  (il launcher naviga con `window.location.href`), da `postMessage` e dal `BroadcastChannel`
  `appsphere_auth`. Il pulsante 🙈/👁 compare solo quando è già accesa e alza o abbassa il solo
  filtro; `toggleHiddenMode()` a modalità spenta non fa niente.
- ⚠️ La spunta si legge **solo dove si vede**: `saveCard()` la considera solo per i diari, perché
  su una nota il campo non c'è e leggerlo scriverebbe il valore rimasto dall'ultima volta. La
  colonna però è su `mm_cards` e il filtro vale per tutti i tipi, quindi estenderla a note e liste
  è una riga di HTML.
- ⚠️ Il filtro categoria **non sopravvive al cambio di Tab**: le categorie di un tipo non sono
  quelle di un altro, e una Tab che si aprisse già filtrata su una categoria che lì non esiste
  sembrerebbe vuota.
- **Le spunte di una lista sono ottimistiche con rollback** (come Spuntiamola): la voce cambia
  subito e torna indietro se il DB rifiuta, così non resta a schermo una spunta finta che sparisce
  al ricarico.
- **La registrazione di un diario rimostra lo scopo della scheda** prima di chiedere le misure: è
  il promemoria di come vanno lette, e senza si finisce a dare voti a caso dopo un mese. Una scala
  si dà con lo slider o con la casella, un numero con la casella e la sua unità, un sì/no con due
  pulsanti che si ripremono per annullare, una scelta con una combo la cui prima voce
  (*— non l'ho misurata*) toglie il valore invece di archiviarne uno finto.
- **Ogni misura porta una nota che spiega il punteggio** (`hint`, es. *1 = per niente, 20 = non
  riesco a pensare ad altro*): si scrive nella definizione e ricompare **sotto il nome della
  misura al momento di registrare**, che è l'unico momento in cui serve. È facoltativa, e si
  cambia senza toccare lo storico — vive sulla riga della misura, che non viene mai ricreata.
- **La registrazione vuole un titolo breve**, obbligatorio: l'elenco delle registrazioni è fatto
  di date tutte uguali, e senza una riga di testo non si ritrova più il giorno che si cerca.
  ⚠️ L'obbligo è **nell'app, non nel database**: `title` è `NOT NULL DEFAULT ''` perché le
  registrazioni fatte prima della colonna un titolo non ce l'hanno, e un vincolo che le rifiutasse
  renderebbe impossibile perfino aprirle per correggerle.
- Il riepilogo per misura mostra l'ultimo valore e le **ultime 12 registrazioni in miniatura**
  (`.spark`, div e non Chart.js — questa pagina non lo carica). Il fondo scala è quello della
  misura per le scale, quello osservato per i numeri liberi. **Per una scelta al posto della
  miniatura c'è la distribuzione** (`.dist`): un numero non ce l'ha, e quello che si vuole sapere
  è quante volte è uscita ciascuna opzione.

### `casarosa.html` — Cassa Casa Rosa
- Movimenti e saldo della cassa di Casa Rosa (`cntrs_transactions`, `cntrs_categories`,
  `cntrs_saldi`). Si apre dal collegamento *🏠 Casa* nella sidebar di `finanza.html`.
- **Gemello di `conto-risparmio-teresa.html`**, che lavora sullo stesso schema con le tabelle
  `_terr`: import da Excel/CSV, `guessCategory()` sulle causali numeriche UniCredit
  (048, 008, 219, 034, 018) e revisione dei conflitti prima di scrivere.
- L'import dalla banca passa da `enable-banking-transactions` sul conto spuntato come
  `'casa_rosa'` in `cm_bank_connections.uses` — uso a sé e non `'conto_risparmio'` riusato,
  altrimenti le due pagine si troverebbero ciascuna il conto dell'altra nella tendina.
  La causale numerica non passa dall'API PSD2: `guessCategoryFromBank()` riconosce le stesse
  categorie dal testo, e ciò che non riconosce va in *da attribuire*.
- ⚠️ **Il blocco di import dalla banca è lo stesso di `conto-risparmio-teresa.html`**, a meno dei
  nomi delle tabelle, dell'uso in `uses` e di una categoria (`AFFITTO CONTANTE ROSA` invece di
  `AFFITTO CONTANTE TERRASINI`). Se lo modifichi in uno, guarda anche l'altro.

### `spese-ada.html` — Spese Ada
- Import dal conto, categorie e dashboard per le spese di Ada. Si apre dal collegamento
  *🧾 Spese Ada* nella sidebar di `finanza.html`.
- **Tabelle proprie `ada_*`, non le `ca_*`** (`20260808130000_ada_spese_tables.sql`):
  `ada_categories`, `ada_transactions` (una sola categoria per movimento), `ada_merchant_map`.
  La separazione è per tabella e non per campo perché le `ca_*` sono lette da quattro punti
  diversi (`cost-analysis.html`, la vista in `finanza.html` e in `situazione-teresa.html`,
  `revolut-auto-categorize`): un campo da filtrare avrebbe mescolato le spese di Ada nei totali
  di famiglia il giorno che una query se ne fosse dimenticata.
- **Due livelli di categoria** (`20260808140000_ada_super_categories.sql`): super-categoria → voce.
  Il **colore vive solo sulla super-categoria** e la voce lo eredita; nella ciambella per voce le
  voci della stessa super si distinguono per sfumatura (`shade()`), così le due ciambelle
  affiancate si leggono come una cosa sola. Tabella separata e non un `parent_id`: una voce senza
  super deve poter esistere (sono le sei di partenza) e con l'autoreferenza sarebbe
  indistinguibile da una super-categoria. `super_id` è `ON DELETE SET NULL` — cancellare una
  super non porta via le voci, e quindi nessun movimento perde la categoria.
- Il conto è quello spuntato come `'spese_ada'` in `cm_bank_connections.uses` — lo stesso conto
  UniCredit può servire anche ad altri moduli. I movimenti arrivano da
  `enable-banking-transactions`: solo le **uscite**, con un **filtro per carta** (la funzione
  restituisce `card.identification` quando la banca la espone) per isolare le spese di Ada su un
  conto con più carte. La carta scelta resta in `localStorage` (`ada_card_filter`).
- Non c'è una schermata dove si scrivono regole: dare una categoria a un movimento **impara la
  descrizione** in `ada_merchant_map`. La regola vale in tre momenti — sulle righe proposte in
  anteprima, sugli altri movimenti già in archivio (`applyLearnedMerchants`, subito dopo
  l'assegnazione manuale e dopo ogni import) e col pulsante *✨ Applica i negozi imparati*.
  **Tocca solo i movimenti senza categoria**: quello che è già classificato non si sovrascrive.
- Quando la voce giusta non c'è ancora, la tendina del movimento chiude con **«➕ Nuova voce…»**:
  si crea la voce e **nella stessa finestra si scrive la regola**, con il conteggio dal vivo di
  quanti movimenti aggancerebbe. Le due cose nascono insieme perché è lì che si sa qual è il
  negozio — la chiave proposta è quella che si imparerebbe da sola (`normalizeMerchant`), ma
  accorciarla è il punto dell'operazione. Togliendo la spunta la voce si crea senza regola.
  In quel caso `setTxCategory(tx, id, { learn: false })`: la regola l'ha appena scritta l'utente,
  e impararne una seconda con la descrizione intera ne aggiungerebbe una che nessuno ha chiesto.
  Vale sia sui movimenti in archivio sia sull'anteprima di import; lì il form **prende il posto
  dell'elenco** invece di aprire un secondo modale (`#modal-body` è uno solo) e le spunte già
  fatte vengono prima messe al sicuro in `_import.items` da `syncImportSelections()`.
- Il confronto è **«la descrizione contiene la chiave»**, non un'uguaglianza, e la chiave si
  ricava da **quello che UniCredit scrive dopo l'importo**, che è l'esercente:
  `PAGAMENTO POS … del 15/07/2026 CARTA 31342819 DI EUR 1,50 SAN DONATO ALIMENTARI. BOLOGNA`
  → chiave `SAN DONATO ALIMENTARI. BOLOGNA` (`POS_MERCHANT_RE` in `merchantText()`). Tutto quello
  che precede l'importo — modalità di pagamento, data, numero di carta, importo — cambia a ogni
  movimento. Se il pezzo non c'è (addebiti, bonifici, altre banche) si tiene la descrizione
  intera e la regola si **accorcia a mano** da Categorie → Negozi imparati, dove il campo mostra
  in tempo reale quanti movimenti aggancerebbe.
  Fra più regole che agganciano **vince la più lunga**, cioè la più specifica: senza quella
  precedenza una regola corta imparata prima si prenderebbe i movimenti di una più specifica.
- La stessa causale porta il numero di carta (`CARTA 31342819`): `cardFromDescription()` lo usa
  come ripiego quando l'API non espone la carta nei campi strutturati, altrimenti il filtro per
  carta — l'unico modo per isolare le spese di Ada su un conto condiviso — resterebbe vuoto.
- **Nessuna categorizzazione da MCC**: `enable-banking-transactions` non restituisce il
  `merchant_category_code` (lo estrae solo `enable-banking-sync`, per Spese Famiglia). Qui
  l'automatismo è tutto nei negozi imparati.

### `spese-personali.html` — Spese Personali
- Il conto personale di Salvatore: import dal conto, categorie a due livelli, negozi imparati e
  dashboard. Si apre dal collegamento *💳 Spese Personali* nella sidebar di `finanza.html`
  (sezione **Salvatore**).
- **Gemello di `spese-ada.html`**, a meno di quattro cose — se modifichi una delle due, guarda anche
  l'altra:
  1. **tabelle `sal_*`** invece di `ada_*` (`20260809120000_sal_spese_personali_tables.sql`),
     separate dalle `ca_*` per la stessa ragione: le spese personali non devono entrare nei
     totali di Spese Famiglia;
  2. **entrate comprese**, non solo le uscite. `amount` è già con segno, quindi lo schema non
     cambia: cambia cosa la pagina propone all'import (una casella *Solo le uscite*, spenta di
     default) e cosa somma — KPI uscite / entrate / **saldo del periodo**, andamento mensile a
     due serie, e una tabella *Dettaglio entrate* a parte. Le ciambelle restano sulle **sole
     uscite**: mescolare entrate e uscite in una torta dà percentuali che non vogliono dire
     niente;
  3. il filtro per carta lascia comunque passare i **movimenti senza carta** (bonifici,
     accrediti, addebiti). Su Ada si cerca una carta e basta; qui quei movimenti sono
     esattamente le entrate che servono per il saldo, e nasconderli col filtro attivo
     falserebbe il totale;
  4. **c'è anche l'import da file** (📊 Importa da Excel), che su Ada non c'è: la banca via PSD2
     espone solo gli ultimi mesi, e lo storico più vecchio sta solo nell'estratto conto
     scaricato dal sito.
- L'import da file **sbocca nella stessa anteprima** dell'import dal conto — spunte, doppioni,
  categorie, «➕ Nuova voce…», filtro per carta — perché quel che si fa dopo aver letto le righe
  non cambia a seconda di dove sono nate. `_import.source` (`'bank'` \| `'excel'`) decide solo la
  riga di intestazione dell'anteprima e cosa finisce in `sal_transactions.import_source`;
  `bank_connection_id` ed `external_id` restano **NULL** per le righe da file.
  Tre cose che sono la ragione per cui il codice è fatto così:
  - ⚠️ **un foglio non porta l'external_id**, quindi l'indice unico
    `(bank_connection_id, external_id)` non protegge — in Postgres due NULL sono distinti — e il
    controllo dei doppioni sta tutto nella pagina (`indiceDoppioni()`, vedi sotto). Rileggere due
    volte lo stesso file è la normalità, e le righe già in archivio arrivano in anteprima
    **deselezionate**;
  - ⚠️ **un CSV si legge diversamente da un xlsx**: leggendo un CSV SheetJS interpreta i numeri
    all'americana e «-12,90» diventa **-1290**, «1.850,00» diventa **1,85** — importi plausibili
    e falsi, che in anteprima non si notano. `raw: true` glielo impedisce e a leggerli è
    `parseImporto`, che riconosce la notazione dall'ultimo separatore invece di dare per scontata
    la lingua. Il testo passa da `testoDelFile()`, che prova UTF-8 in modo severo e ripiega su
    **Windows-1252** (in cui UniCredit scrive i suoi CSV) invece di storpiare gli accenti — e con
    loro la chiave del negozio;
  - la **descrizione si archivia com'è**, senza cucirci davanti la causale: è quella che i negozi
    imparati leggono, e cambiandola `normalizeMerchant` darebbe una chiave diversa da quella
    delle righe entrate dalla banca — lo stesso negozio verrebbe imparato due volte.
  Le intestazioni non sono nella prima riga (preambolo con intestatario, IBAN e saldi):
  `trovaIntestazioni()` cerca nei primi 60 righe di ogni foglio una riga che porti insieme data,
  descrizione e importo — o la coppia entrate/uscite — **su tre colonne diverse**, perché un CSV
  spezzato male finisce tutto in una cella sola che quelle tre parole le contiene per forza.
  SheetJS si carica dal CDN **al primo uso** e non all'apertura della pagina.
- ⚠️ **Lo stesso movimento può entrare da due strade, e le due strade non lo scrivono uguale**: la
  banca gli mette accanto l'`external_id` e cuce nella descrizione il nome della controparte, il
  foglio nessuna delle due cose. `indiceDoppioni()` è quindi **una sola rete a tre maglie**, usata
  da entrambi gli import: `external_id` (certezza), `data + importo + descrizione` (stessa riga
  dall'altra strada, stesso testo) e — quella che prende davvero il caso incrociato —
  **`importo` esatto + data entro un margine**. ⚠️ In quest'ultima **il testo non entra affatto**,
  ed è la scelta che regge tutto: la descrizione è proprio ciò che le due strade scrivono in modo
  diverso, quindi confrontarla non aggiunge nulla e toglie aggancio. La **data invece balla** —
  il foglio porta la registrazione, la banca la contabile, e su una carta si scostano di qualche
  giorno — da cui il margine (`dupMargine()`, 3 giorni di default, si cambia in Impostazioni e
  vive in `localStorage` come il filtro per carta). Non essendo una certezza, la riga si **segnala
  come sospetta** mostrando accanto il movimento più vicino e di quanti giorni si scosta: il
  confronto delle descrizioni lo fa l'occhio, che è l'unico che può farlo. Tutti e tre i casi
  arrivano **deselezionati**. Fino alla v1.0.3 la rete era una sola per fonte e un movimento
  entrato da Excel tornava dal sync col suo `external_id`, che nessuno aveva mai visto: la spesa
  entrava **due volte**, e in un totale non si vedeva.
- La pagina **🧠 Attribuzione** (`renderRegole`) è dove le regole si leggono e si tarano: la
  spiegazione in cinque passi di come si sceglie la categoria, un **banco di prova** che su una
  descrizione incollata mostra chiave, regole che agganciano e quale vince senza toccare niente, e
  l'elenco delle regole con quello che ciascuna fa davvero all'archivio. Due colonne che non vanno
  confuse: **aggancia** sono i movimenti la cui descrizione contiene la chiave, **vince** quelli
  che la regola si prende davvero — una regola che aggancia e non vince mai è *coperta* da una più
  specifica, e senza la distinzione sembrerebbe funzionante. **In conflitto** sono i movimenti che
  la regola vince ma a cui è stata data un'altra categoria a mano: restano come sono — le regole
  non sovrascrivono mai — e ⚖️ li riallinea **solo su richiesta esplicita**, con la conferma che
  dice quanti e verso quale voce.
- La **zona pericolosa** in Impostazioni cancella *tutti* i movimenti (categorie, super-categorie e
  regole restano). Si fa scrivere `CANCELLA` invece di un `confirm()` con l'OK a portata di clic:
  di qui non si torna indietro, e la banca ripropone solo il periodo che espone ancora.
- Il conto è quello spuntato come `'spese_sal'` in `cm_bank_connections.uses` — uso a sé e non
  `'spese_ada'` riusato, altrimenti le due pagine si troverebbero ciascuna il conto dell'altra
  nella tendina dell'import. Il conto UniCredit personale è già collegato: basta spuntare
  l'uso da Finanza → Configurazione → 🏦 Banche e Conti.

### `conto-spese-teresa.html` — Contribuzione
- Chi ha versato quanto sul conto delle spese comuni, e quanto dovrebbe aver versato: quote 2/5–3/5
  fino al 2023, 1/3–2/3 dal 2024. Dati in `acct_transactions` (`persona` `TERESA`|`SALVATORE`,
  `tipo` `BONIFICO`|`ALTRO`|`MENSA`, `importo` sempre positivo).
- Si apre dal collegamento *💰 contribuzione* nella sidebar di `finanza.html`.
- **Si alimenta dallo stesso conto che alimenta Spese Famiglia**, che va spuntato anche come
  `'contribuzione'` in `cm_bank_connections.uses` (gli usi si sommano sullo stesso conto: il CHECK
  è stato allargato da `20260808120000_cm_bank_connections_uses_contribuzione.sql`) — via
  `enable-banking-transactions`, che legge e basta: filtro
  (solo **entrate** riconosciute come bonifici), attribuzione a Teresa o Salvatore e controllo dei
  doppioni stanno nella pagina. L'import da CSV Revolut resta come ripiego per lo storico più
  vecchio di quanto la banca espone.
- L'attribuzione scende per gradini — IBAN della controparte, poi nome della controparte, poi testo
  della causale — e quello che non torna resta *da attribuire*: si sceglie a mano nell'anteprima,
  non si indovina. Gli indizi (IBAN o pezzi di nome) stanno in `localStorage` (`cst_person_hints`),
  modificabili da Impostazioni: sono una preferenza di lettura, non un dato della contabilità.
- I doppioni si riconoscono su data + importo: identici anche nella descrizione sono la stessa riga,
  stessa data e stesso importo con descrizione diversa è quasi sempre la stessa contribuzione già
  entrata da CSV. Entrambi i casi partono **deselezionati** — qui un doppione raddoppia una quota.

### `cost-analysis.html` — Analisi Costi
- **Non compare più tra le bolle della home**: `cm_apps.active = false` (migration
  `20260802180000_ca_readonly_teresa_and_hide_app.sql`). Resta l'unica app dove si importa, si
  categorizza e si configura (categorie, persone, regole, viaggi) — si apre dal
  collegamento *🛠️ Spese Famiglia — gestione* nella sidebar di `finanza.html` o dalla notifica
  Smart Block.
- **Non configura più i conti bancari**: censimento degli istituti, collegamento, consenso,
  battesimo dei conti ed eliminazione stanno in `finanza.html` → Configurazione →
  🏦 Banche e Conti, perché gli stessi conti servono anche ai Fondi e al Conto Risparmio. Qui
  compaiono in sola lettura i conti con `'cost_analysis'` in `uses`, nella pagina
  *Sincronizza e Carte*: il sync di Spese Famiglia non si può spostare perché subito dopo
  l'import fa girare merchant appresi, regole e attribuzione per carta, che vivono solo lì.
- La **consultazione** è stata spostata dentro le due app dove serve, come vista in sola lettura:
  `finanza.html` (Salvatore) e `situazione-teresa.html` (Teresa) — vedi sotto.
- **Negozi imparati** (pagina *Regole*, in cima): l'elenco di `ca_merchant_map` +
  `ca_merchant_map_categories`, che fino alla v1.0.71 si scriveva da solo e **non si vedeva da
  nessuna parte**. Ogni riga porta la chiave, la categoria, quante transazioni in archivio
  aggancia e quante di quelle hanno una categoria *diversa*; da lì si modifica, si dimentica e si
  propaga allo storico (🔁, che riusa il modale di `previewMerchantCategoryPropagation`).
  Filtri: *Da allineare*, *Senza categoria*, *Mai usati*.
  ⚠️ Tre cose che sono la ragione per cui la pagina esiste:
  - **il confronto è per uguaglianza sull'intera descrizione normalizzata**, non «contiene» come
    in `spese-ada.html` / `spese-personali.html`. Accorciare una chiave non allarga l'aggancio:
    la restringe a zero. Per allargare servono le **Regole**, che cercano il testo *dentro* la
    descrizione. Il campo del modale mostra dal vivo la chiave che verrà salvata e quante
    transazioni aggancia, perché una chiave «quasi giusta» altrimenti si scopre inerte solo dopo;
  - **le righe orfane** (negozio in `ca_merchant_map` senza figlia in
    `ca_merchant_map_categories`, che `learnMerchantCategories(desc, null)` lascia dietro) non
    categorizzano niente e prima erano invisibili: ora si vedono col filtro *Senza categoria*;
  - **imparare non è propagare.** Salvare un negozio vale per le transazioni future; quelle già in
    archivio si toccano solo confermando l'anteprima — la stessa regola per cui
    `ca_smart_block_set_category` impara e non propaga.
- **Navigazione con hamburger** come le altre app del conto familiare: su desktop la sidebar da
  280 px resta fissa, sotto i 768 px diventa un cassetto a scomparsa aperto dal ⬛ nella top bar
  (prima era una striscia di sole icone incollata sotto la barra blu, senza etichette). Il menù
  elenca le pagine dell'app e, dopo un separatore, i collegamenti alle altre pagine del conto
  familiare (Finanza, contribuzione, conto risparmio, Spese Ada, Casa Rosa): da qui non si
  tornava a nessun'altra pagina senza passare da `finanza.html`.

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
- **Memoria delle stecche** (`sp_stecche`, migration `20260810150000_sp_stecche.sql`): una stecca
  finita non sparisce più. Quando non c'è più niente da spuntare — tutti i giorni fatti *oppure*
  l'ultimo giorno passato — il pulsante della hero diventa *🏁 Chiudi la stecca* e parte una
  cerimonia in tre passi: **l'ultima spunta** (un bersaglio grosso che si preme una volta sola e
  lascia la sua emoji come sigillo), la **barra della soddisfazione da 1 a 100**, la **nota**.
  Solo allora la stecca finisce in archivio e il messaggio finale — pescato per fascia da
  `MSG_CHIUSURA`, dal consolatorio (`.toast.dolce`) al complimento vivo — arriva con i fuochi
  d'artificio di `fuochiFinali()`, da 8 a 24 scoppi più il gran finale a seconda di quanto si è
  soddisfatti. Le stecche chiuse si rileggono nella card *🏅 Le stecche chiuse*.
- **La chiusura scrive prima e cancella dopo**: `dbCloseStecca()` inserisce in `sp_stecche` e solo
  se l'insert riesce svuota `sp_checks`, `sp_key_days` e `sp_settings`. Non è l'ottimismo con
  rollback usato per le spunte, di proposito: nell'ordine inverso una rete che cade cancellerebbe
  il periodo senza averne salvato la memoria, cioè il difetto che la funzione esiste per togliere.
  Finita la chiusura non c'è più nessun periodo (`S.configured = false`) e `salvaCache()` **svuota
  le chiavi `sp_*` di localStorage**, altrimenti al riavvio `dbLoad()` scambierebbe la cache per
  dati locali da ricaricare e resusciterebbe la stecca appena archiviata.
- **Cancellare un traguardo è l'opposto di chiuderlo**, e sono due comandi diversi:
  *🗑️ Cancella il traguardo* (Impostazioni → zona rossa, `dbDeleteGoal()`) butta via periodo,
  spunte e giornate chiave **senza scrivere niente in `sp_stecche`** — è l'uscita per un traguardo
  sbagliato o abbandonato; il cestino su ogni scheda di *🏅 Le stecche chiuse* (`dbDeleteStecca()`)
  toglie invece una stecca già archiviata. Tutt'e due cancellano **prima sul DB e poi in locale**,
  al contrario dell'ottimismo con rollback delle spunte: sparita solo dall'app, la riga tornerebbe
  da sé al primo `dbLoad()` e sembrerebbe che la cancellazione non funzioni. Cancellato il
  traguardo, `S` torna ai valori di partenza (`goal`, `emoji`, date) e non solo a
  `configured = false`: le impostazioni si riaprono su un foglio bianco invece di riproporre
  quello appena buttato. ⚠️ Il gemello nativo non ha né l'uno né l'altro comando.
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

⚠️ **Il workflow non usa `supabase link`.** Fra le altre cose `link` chiama
`GET /v1/projects/{ref}/api-keys`, che dal 7 agosto 2026 risponde con un errore di validazione
del suo stesso schema (`SchemaError` su `inserted_at`): l'intero deploy moriva lì, portandosi
dietro migration ed Edge Function, che con le API keys non c'entrano niente. Lo step
*Prepare Supabase connection* scrive a mano i due file che `link` metteva in `supabase/.temp`
(`project-ref` e `pooler-url`, quest'ultimo letto dall'API del pooler e forzato in session
mode), poi `db push --linked` e `functions deploy --project-ref` funzionano da soli. La
password del database non serve: senza `DB_PASSWORD` la CLI si crea da sola un ruolo di login
temporaneo. **Se un giorno l'endpoint torna sano, `link` resta comunque superfluo.**

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
10. **Sette app esistono anche in Kotlin**: Spuntiamola, Obiettivi, Events Log, Ta Firi?, Ti pisasti? (Weight Quest), Memo e Abituati hanno un gemello nativo in `android-app/appsphere-native/` che scrive sulle stesse tabelle (Tasks pure, con la sua sezione a parte). Cambiare le regole in uno solo dei due li fa divergere in silenzio — dettagli in *AppSphere nativa*.

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
