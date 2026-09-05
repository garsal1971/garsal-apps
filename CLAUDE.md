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
| `cm_profile` | La scheda personale, **una riga per utente**: `nome`, `cognome`, `data_nascita`, `sesso` (`M`\|`F`\|`Altro`), `altezza_cm`, più patologie, farmaci e note mediche. Si compila da AppSphere → ☰ → 👤 Profilo, o direttamente da **`/#profilo`** |
| `cm_institutions` | Censimento di banche e broker. `connectable = false` per chi non è nel catalogo Enable Banking (broker senza PSD2): sta in anagrafica ma non si collega |
| `cm_bank_connections` | Un conto per riga, nato da un consenso. `uses text[]` dice a cosa serve |
| `cm_sync_log` | Storico delle sincronizzazioni bancarie |
| `cm_push_devices` | I telefoni a cui mandare le notifiche push. Una riga per **installazione**: `token` è la chiave, e l'app lo riscrive a ogni avvio |

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
| `el_groups` | Event category groups; `riservato` li tiene fuori dagli elenchi |
| `el_events` | Event definitions |
| `el_logs` | Event log entries |

Il **riservato sta sul gruppo e non sull'evento**: eventi e registrazioni seguono il loro gruppo,
e nascondere un evento lasciando visibile il gruppo che lo contiene direbbe comunque di cosa si
sta parlando. ⚠️ La colonna è arrivata **dopo** le righe (`el_*` non sta in nessuna migration):
i gruppi di prima ce l'hanno **NULL**, quindi il filtro va sempre scritto
`riservato.eq.false,riservato.is.null` — con la sola uguaglianza sparirebbero tutti.

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

### Possibili soluzioni (`fnz_coverage_items`)
| Table | Purpose |
|---|---|
| `fnz_coverage_items` | Le voci di copertura, **una riga per voce**: `side` (`fabbisogno`\|`dotazione`), `item_key`, `amount`, `periodicity`, `revaluation_pct`, `linked_other_asset_id`, `excluded`, `note` |

Si compila da `finanza.html` → Piano pensione → 🛡️ **Possibili soluzioni**, la voce di menù
sotto 🏛️ Simulazione INPS. Quello che servirà, quello con cui ci si arriva, e quanto manca.
(La sezione della barra si chiamava *Previdenza* e la voce *Coperture per esigenze future*: sono
solo etichette, `data-view` resta `coperture` e la tabella `fnz_coverage_items`.)

⚠️ **Il fabbisogno non è un numero solo: si conta fino a una data, e le date sono quattro.**
«Costo della vita» e «contribuzione alla pensione» sono flussi che durano finché non si smette
di lavorare, quindi valgono di più uscendo a vecchiaia che cinque anni prima — ed è esattamente
la differenza che la pagina esiste per mostrare.

| Scenario | Data |
|---|---|
| 🌿 Uscita naturale | Anticipata **− `USCITA_NATURALE_ANNI`, costante fissa = 5 anni** |
| 🧭 Uscita concordata | Anticipata **− la somma di `uscita()`** (di partenza 5 + 1 + 2 = 8 anni) |
| 🚪 Pensione anticipata | `fnz_pension_forecast.pension_date`, scenario `anticipata` |
| 🏛️ Pensione di vecchiaia | `fnz_pension_forecast.pension_date`, scenario `vecchiaia` |

⚠️ **La prima colonna è il METRO e non un piano**, ed è l'unica col fondo azzurro (`.cov-rif`,
sulle celle `.cov-val` e sulla colonna del riepilogo): risponde a «uscendo cinque anni prima
dell'anticipata, senza aver trattato niente con nessuno, quanto servirebbe?». I suoi 5 anni sono
una **costante** e non la somma di `uscita()` — legarla alle durate della trattativa la farebbe
muovere insieme a quello che dovrebbe misurare, e cambiando 5+1+2 dal ✎ si sposterebbero tutt'e
due le colonne invece della sola concordata. ⚠️ Sta **per prima** anche se la sua data cade
**dopo** quella dell'uscita concordata (−5 contro −8): l'ordine delle colonne è quello di
lettura — prima il riferimento, poi i piani — non quello del calendario.

⚠️ **🧭 Uscita concordata è l'unica delle quattro che si decide, e non è un numero solo.** Fino alla
v1.7.0 era la costante `COVERAGE_ANNI_ACCOMPAGNAMENTO = 5`; ora sono **tre durate che si
sommano** — accompagnamento dell'azienda, garden leave che l'azienda concede **in più**, e
aspettativa non retribuita che ci si prende da sé — perché sono tre cose diverse, si trattano
con interlocutori diversi e schiacciarle in un numero solo le rendeva impossibili da negoziare
una per una. Le tre durate si cambiano dal ✎ **sotto il titolo della sua colonna** nel
riepilogo (`renderUscitaNota`), etichettato *setta parametri*. ⚠️ Sta lì e non sopra la tabella
(dov'era fino alla v1.14.0): è una proprietà di quella colonna, e in cima parlava di una colonna
che il lettore doveva ancora trovare. Nella cella ci sono quindi **due ✎**, uno per cosa: prima
le durate, poi la descrizione scritta a mano (`renderScenarioNota`). ⚠️ L'etichetta è due parole
e **non l'addizione per esteso** (v1.15.1): «5 anni di accompagnamento + 1 di garden leave + 2 di
aspettativa = 8 anni prima dell'anticipata» nell'intestazione di una colonna stretta andava a capo
tre volte e spingeva giù i nomi delle altre colonne. Gli addendi restano **dentro la finestra**,
che è il posto dove si cambiano; la data risultante si legge nella riga *Data*, un rigo sotto.

⚠️ **I tre numeri vivono in `cm_settings`, chiave `fnz_uscita_anticipata`** (JSON
`{accompagnamento, garden_leave, aspettativa}`), non in una costante e **non in
`localStorage`**: sono un'ipotesi di trattativa — cioè esattamente il dato che si vuole cambiare
senza toccare il codice — e la stessa domanda ci si fa dal PC e dal telefono. Chiave assente o
casella vuota valgono `USCITA_DEFAULT` (5 + 1 + 2) e **non zero**: zero direbbe che non si esce
affatto prima, ed è la stessa scelta di `amount` NULL.

⚠️ **Le tre durate si sommano e non si sovrappongono**, ed è la sostanza del piano: l'azienda
accompagna, il garden leave viene in più, l'aspettativa si aggiunge ancora. Per la stessa
ragione `spostaAnni()` sposta di **mesi** e non di anni interi — le durate si scrivono a mano e
niente vieta mezzo anno di preavviso, che con `setFullYear` darebbe una data invalida (NaN)
invece di sei mesi.

⚠️ **Ogni colonna porta un sottotitolo che si scrive a mano** (`renderScenarioNota`, il ✎
nell'intestazione del riepilogo): una riga per ricordarsi *cos'è* quel piano, sotto il nome breve.
Vive in `cm_settings`, chiave **`fnz_scenari_note`** (JSON `{naturale, accompagnamento,
anticipata, vecchiaia}`), letta insieme alle altre due da `loadUscita()` — non in `localStorage`,
perché è un pezzo del piano e la stessa frase si rilegge dal telefono. Compare **solo nel
riepilogo**: negli elenchi di Fabbisogno e Dotazioni la stessa scritta si ripeterebbe su ogni
voce. ⚠️ Vuoto **non sparisce**, resta un «+ descrizione» sbiadito: il ✎ da solo sarebbe un
pulsante senza niente accanto. E svuotarla **toglie la chiave** invece di salvare la stringa
vuota, come la casella vuota di `fnz_income`.

### ⏳ La macchina del tempo

In cima alla pagina una barra sposta il **punto di osservazione** di mese in mese, da oggi fino
alla più lontana delle quattro date: `⏮ Oggi · ‹ · settembre 2026 · ›`, un cursore, e la
**percentuale di rivalutazione**. Tutta la pagina risponde allora alla domanda «e se me lo
chiedessi da lì?».

⚠️ **Le quattro date NON si spostano.** Sono la pensione che dice l'INPS e le durate della
trattativa, e non dipendono da quando ci si fa la domanda. A spostarsi è **da dove si contano
gli anni** (`anniFinoA` parte da `coverageOggi()`), e da lì si accorciano da soli tutti i flussi
del fabbisogno.

⚠️ **A «oggi» la pagina mostra ESATTAMENTE i numeri di prima**: `coverageMesi()` vale 0, la data
di osservazione è oggi e `coverageFattore()` torna `1` dal ramo corto — non `Math.pow(x, 0)`. È
la garanzia che la barra non sposti nessun conto finché non la si muove; senza, ogni apertura
della pagina direbbe qualcosa di diverso dal giorno prima senza che nessuno l'abbia deciso.

| Cosa | Alla data scelta |
|---|---|
| 🏠 Estinzione mutuo | **si ricalcola** col piano di ammortamento (`computeLoanValue(l, data)`) |
| Tutto il resto — fabbisogno **e** dotazioni | si **rivaluta** di `(1+r)^anni` |

⚠️ **Il mutuo si ricalcola e non si rivaluta**, ed è l'unica eccezione: il piano di ammortamento
sa già quanto sarà il residuo quel mese, e moltiplicarlo anche per l'inflazione lo conterebbe due
volte in senso opposto — un debito che scende non è un costo che sale. Lo dice `allaData: true`
sulla voce in `COVERAGE_ITEMS`, ed è l'unica che ce l'ha.

⚠️ **La rivalutazione passa da UN varco solo, `coverageBase()`**, e da nessun'altra parte: è la
ragione per cui totale, scopertura, capitale da assicurare e il badge «al posto di X» dicono
tutti la stessa cifra — sono lo stesso `value`. L'unica voce fuori è 💼 Redditi da lavoro perso,
che non passa di lì e si rivaluta in `renderCoverageRedditoLavoro`.

⚠️ **Il form dell'importo legge `coverageAuto`, non `coverageBase`**: il database parla in euro
di **oggi**, e un «Finanza dice X» col numero già proiettato inviterebbe a ricopiarlo a mano —
per poi rivalutarlo una seconda volta. Per la stessa ragione la casella mostra `row.amount`
grezzo.

⚠️ **Il mese scelto vive in memoria (`S.covMesi`) e non in `cm_settings`**: è una lente, non un
pezzo del piano. Ritrovarla spostata di tre anni riaprendo la pagina vorrebbe dire leggere numeri
proiettati credendoli quelli di oggi, che è il modo peggiore di sbagliarsi. La **percentuale**
invece è un'ipotesi come le durate dell'uscita, e sta in `cm_settings` chiave
**`fnz_rivalutazione`** (JSON `{pct}`, di partenza 2), letta da `loadUscita()` con le altre tre.
⚠️ **Non è la colonna `revaluation_pct`** di `fnz_coverage_items`, che resta inutilizzata: quella
era una percentuale per voce, questa è una sola per tutta la pagina.

⚠️ **Una colonna la cui data è già passata resta a schermo, sbiadita** (`.cov-past`, badge *già
passata*): i suoi flussi valgono zero — zero anni davanti, per il `Math.max(0, …)` di
`anniFinoA` — e i capitali restano scritti. Sparire sarebbe il modo peggiore di dirlo: un piano
in meno senza niente che spieghi perché.

⚠️ **Il cursore ridisegna la pagina su `change` e non su `input`**: ridisegnando a ogni `input`
l'elemento verrebbe sostituito sotto le dita e il trascinamento si fermerebbe al primo mese.
Trascinando si aggiorna il **solo mese scritto**; le frecce ‹ › danno la precisione del mese, che
un cursore su un telefono non dà.

⚠️ **La data di osservazione si scrive coi campi locali** (`isoLocale`) e non con `toISOString()`,
che converte in UTC — una mezzanotte italiana lì dentro è il giorno prima. E il giorno si limita
all'ultimo del mese di arrivo: `setMonth` da solo porta il 31 gennaio + 1 mese al **3 marzo**,
cioè sposta di un mese e due giorni.

⚠️ **Le quattro date si leggono da `coverageDateScenari()`, un posto solo**: le usa
`coverageScenarios` per disegnare le colonne e `coverageMesiMax` per sapere fin dove arriva il
cursore. Due copie sarebbero due elenchi che divergono il giorno che se ne aggiunge una.

⚠️ **Una data che manca non si inventa**: la colonna resta vuota e lo dice. Un fabbisogno contato
su un orizzonte immaginario è peggio di un fabbisogno che non c'è, perché sembra un numero.
Per la stessa ragione `coverageValoreA` torna `null` — non zero — su un flusso senza orizzonte.

⚠️ **Le voci non stanno nel database**: vivono in `COVERAGE_ITEMS` dentro `finanza.html`, come
`INCOME_SECTIONS` per il reddito, e aggiungerne una è una riga di JavaScript, non una migration.
`item_key` è testo libero di proposito; il vincolo che conta è `UNIQUE (user_id, side, item_key)`,
su cui la pagina scrive in **upsert** — senza, ricompilare una voce raddoppierebbe il totale in
silenzio. È la stessa scelta di `fnz_income`.

⚠️ **Quattro fonti diverse, e la differenza è la funzionalità**:

| `fonte` | Da dove viene il numero |
|---|---|
| `manuale` | Lo scrive l'utente e basta (università, costo della vita, rendita per Ada, varie ed eventuali) |
| `auto` | Si legge **dal vivo** dai dati di Finanza: debito residuo dei mutui (`computeLoanValue`), valore quota dei portafogli **al netto delle tasse** (`portfolioStats`), importo lordo dell'ultima simulazione INPS, Pensione INPS e UniCredit lordo da 💶 Reddito |
| `asset` | Come `auto`, ma la riga di `fnz_other_assets` la sceglie l'utente in tendina (TFR, Casa Rosa, Casa Mia) |
| `calcolata` | Non si scrive e non si archivia: è la **scopertura** dello scenario. Vale per la sola assicurazione |

⚠️ **«Pensione Ada» è `auto` e non `asset`**: si legge da 💶 Reddito, riquadro *Redditi*, riga
**Pensione INPS** dell'anno più recente **che ce l'ha** — non dell'ultimo anno in tabella, o un
anno appena aperto e ancora da compilare azzererebbe la voce in silenzio.

⚠️ **È un importo ANNUO, e la dotazione è quanto se ne incasserà in tutto**: si moltiplica per gli
anni che mancano alla fine del **corso regolare** di Ada, l'ultimo in cui quella pensione spetta a
un figlio studente. Sono due durate diverse in `ADA_SCUOLA` e non è una svista: `anniUniversita`
(7) è il mantenimento agli studi che decide il **fabbisogno** — laurea più specializzazione —
mentre `anniCorsoRegolare` (3+2) è la durata legale del corso, che decide fino a quando la
pensione si incassa (inizio 2029 + 5 = **2034**). Usarne una sola sbaglierebbe per eccesso l'una o
per difetto l'altra. La fine si **ricava** e non si scrive: un 2034 messo a mano fra due anni
direbbe ancora 2034. Il conto è in anni interi (`2034 − anno in corso`) così si rifà a mente, e a
corso finito la voce vale zero — che è quello che sarà.

⚠️ **«Accordo con azienda» è l'unica voce che vale DIVERSO da una colonna all'altra**, ed è la
ragione per cui anche le dotazioni ne hanno quattro (prima ne avevano una sola, *Valore oggi*):
l'accordo esiste nel piano dell'uscita concordata e non negli altri, dove si lavora fino alla
pensione oppure si esce senza aver trattato niente. Il conto è `coverageAccordoAzienda(sc)`:

| Ingrediente | Da dove |
|---|---|
| Retribuzione di un anno | 💶 Reddito, riquadro *Redditi*, riga **UniCredit lordo** dell'anno più recente **che ce l'ha** — stessa regola di «Pensione Ada» |
| Netto | − `TAX_TFR_SEPARATA` (27 %): l'incentivo all'esodo è tassato a tassazione **separata**, non con l'IRPEF della busta |
| Anni | `accordoAnni()`, **uno per scenario** (di partenza 0 · 3 · 0 · 0), in `cm_settings` chiave `fnz_accordo_azienda_anni` |

⚠️ **Gli anni si scrivono per colonna e non si ricavano dalle durate di `uscita()`**: legarli
alla loro somma scriverebbe lo stesso importo in tutte le colonne, che è l'opposto di quel che
la tabella mostra. **Zero è una risposta buona**, non un dato mancante: nelle due colonne della
pensione — e nella 🌿 uscita naturale, dove non si è trattato niente — dice «qui l'azienda non
mette niente». Resta però **modificabile dal ✎** anche lì: la chiave `naturale` sta in
`ACCORDO_DEFAULT` come le altre tre, e il form ne disegna una casella per colonna. Non stanno in
`fnz_coverage_items` perché lì c'è **una riga per voce**, non una per voce e scenario.

⚠️ Il 27 % è la **stessa stima dichiarata** del TFR (vedi il regime fiscale degli asset), con lo
stesso caveat: l'aliquota vera dipende dal reddito di riferimento degli ultimi cinque anni.
L'etichetta della riga la scrive accanto al numero, insieme all'anno del reddito da cui viene.

⚠️ Il form di una dotazione **non chiede la periodicità**: una dotazione è quello che c'è oggi e
la sua colonna non ha un orizzonte su cui moltiplicare, quindi `annuo` lì darebbe `null` e basta.
Un importo **scritto a mano** su questa voce resta un override come su tutte le altre, ma vale
**uguale su tutte le date** — e il badge lo dice invece di mostrare «al posto di X», che
sarebbe la cifra di una colonna sola.

⚠️ **L'asset si collega per id e non si indovina dal titolo**: una voce agganciata al nome
smetterebbe di leggere il giorno che qualcuno rinomina «Casa Rosa» in «Casa di Rosa», e lo
farebbe **in silenzio** — il totale scenderebbe senza che niente lo dica. `linked_other_asset_id`
è `ON DELETE SET NULL`: cancellato l'asset la voce resta e torna a chiedere un importo, invece di
sparire dal totale.

⚠️ **`periodicity` decide se l'importo è un capitale o un flusso**, e le colonne del database sono
**nullable di proposito**: `NULL` significa «vale quello che dice `COVERAGE_ITEMS`». Un DEFAULT
scritto nel database congelerebbe lì una scelta che vive nella pagina, e cambiarla nella pagina
non avrebbe più effetto sulle righe già salvate — che è il modo più silenzioso di far divergere
le due. Per togliere la rivalutazione si scrive **0**, che è una cosa diversa da «non l'ho deciso
io».

| `periodicity` | Come si conta |
|---|---|
| `una_tantum` | Un capitale: **stesso importo** su tutti gli orizzonti |
| `annuo` | Un flusso: **importo × anni** che mancano alla data (`sommaFlusso`) |

⚠️ **Tutto è al valore della DATA DI OSSERVAZIONE** — cioè in euro di oggi finché la ⏳ macchina
del tempo sta ferma su «oggi», che è il caso normale: un flusso è importo per anni, senza
rivalutazione composta lungo la sua durata. Le dotazioni sono in euro di oggi per costruzione — sono quello che c'è — e
rivalutare il solo fabbisogno metterebbe a confronto due grandezze che non si possono sottrarre.
Un domani più caro si tiene in conto scrivendo un importo annuo più alto: una scelta visibile nel
form, non un moltiplicatore nascosto nel codice. Fino alla v1.4.0 il flusso si rivalutava di una
percentuale composta, e il fabbisogno usciva in euro del giorno in cui sarebbe stato speso.
⚠️ L'ultimo anno parziale entra per la sua frazione: fermarsi all'anno intero perderebbe fino a
undici mesi di spesa.

⚠️ La colonna **`revaluation_pct` esiste ancora in tabella e non la legge più nessuno**
(`20260901160000_...`): va tolta con una migration quando si tocca di nuovo questa parte — una
colonna che nessuno riempie è un invito a reimplementare la rivalutazione una seconda volta.
⚠️ La rivalutazione della ⏳ macchina del tempo **non è lei**: è una percentuale sola per tutta la
pagina, in `cm_settings` chiave `fnz_rivalutazione`.

⚠️ **Dell'assicurazione ci sono DUE numeri, e sono grandezze diverse.** Il **premio** è una voce
del fabbisogno come le altre — un costo annuo, moltiplicato per gli anni che mancano — e sta in
fondo all'elenco, `periodicita: 'annuo'`, `fonte: 'manuale'`. Il **capitale** è il parametro del
contratto, si calcola (`coverageCapitale`) e vive nel riepilogo, non nell'elenco: sommarlo al
fabbisogno sarebbe come sommare l'affitto al prezzo della casa.

⚠️ Il capitale copre **tutte le voci di fabbisogno che stanno sopra, premio escluso**, e **le
dotazioni non si sottraggono**: è la scelta prudente, perché TFR, case e portafogli il giorno che
servissero potrebbero non essere né liquidi né disponibili, mentre la polizza paga comunque.
Quanto manca al netto di quello che c'è già lo dice la riga **Scopertura**, due righe più su: le
due domande convivono nel riepilogo invece di essere schiacciate in un numero solo. La voce
dell'assicurazione va **per ultima** in `COVERAGE_ITEMS` e non è un caso — il capitale è la somma
di quelle che la precedono.

⚠️ **Le dotazioni non si proiettano da sé**: un TFR o un portafoglio proiettati sarebbero un
rendimento inventato messo accanto a numeri veri. Le quattro colonne ripetono quindi lo stesso
numero quattro volte per ogni voce **tranne l'accordo con l'azienda**, che è l'unica a dipendere
dal piano che si sta guardando. Spostando la ⏳ macchina del tempo si rivalutano anche loro —
ma è una scelta **visibile**, fatta muovendo una barra e con la percentuale scritta accanto, non
un moltiplicatore che gira di nascosto.

⚠️ **Le dotazioni sono al NETTO delle imposte**, ed è l'unico posto in Finanza dove il netto
prende il posto del lordo invece di affiancarlo: una dotazione è quello con cui ci si arriva
davvero, e l'imposta si paga vendendo — cioè proprio nel momento in cui quella dotazione
servirebbe. Vale per «Finanza» (`coverageFinanza`, somma dei `netValue` dei portafogli) e per le
voci collegate a un asset, che passano da `assetTax()` (vedi il regime fiscale degli asset).
L'etichetta della riga porta sempre il lordo accanto, e quando l'imposta non si può stimare lo
dice invece di tacere.

⚠️ **Tutta la catena del calcolo passa dallo SCENARIO e non dai soli anni che mancano**
(`coverageValoreA(item, sc)`, `coverageTotaleA(side, sc)`, `coverageCapitale(sc)`,
`coverageAuto(item, sc)`): quasi tutte le voci guardano solo `sc.anni`, ma l'accordo con
l'azienda deve sapere **quale colonna** si sta disegnando, e sul filo dei soli anni quella
distinzione non passerebbe.

⚠️ **Una voce si può togliere dal conto senza cancellarla** (`excluded`,
`20260902110000_...`): il ☑️/🚫 accanto al ✎ la spegne e la riaccende, su tutte le voci — sia
fabbisogno sia dotazioni. Una voce esclusa **resta a schermo**, sbiadita e con gli importi
barrati, e sparisce dai totali, dalla scopertura e dal capitale da assicurare; non si conta
nemmeno fra le «da compilare», perché un importo che non entra nel conto non è un importo che
manca. Sparire sarebbe il modo peggiore di escluderla — un totale più basso senza niente che
dica perché — e **zero direbbe un'altra cosa**: «questa dotazione non c'è» invece di «c'è ma
non la conto». È il terzo stato accanto ad `amount` NULL, e sono tre cose diverse.

⚠️ Il flag sta **sulla riga della voce** e non in `cm_settings`: è una proprietà di quella
voce, e lontano da lei un'esclusione sopravvivrebbe alla voce che descrive. ⚠️ `saveCoverage`
deve nominarlo nell'upsert (`tieni('excluded', false)`), o **il ✎ rimetterebbe nel conto una
voce appena tolta**: l'upsert riscrive la riga intera e quel che non si nomina torna al DEFAULT.
Per la stessa ragione il prompt 💬 dell'assicurazione parte da `coverageVociAttive()`: deve
contenere gli stessi numeri che la pagina mostra.

⚠️ **`amount` NULL non è zero**, ed è la stessa scelta di `fnz_income` e delle misure di Memo. Su
una voce manuale dice «non l'ho ancora scritto»; su una automatica dice «vale il dato di Finanza»
— l'importo scritto a mano è un **override** che vince, si vede col badge *scritto a mano* accanto
al dato che ha coperto, e si toglie col ↺. Un override silenzioso resterebbe fermo mentre il dato
vero cambia sotto. Le voci senza valore si **contano accanto al totale** invece di sparirci dentro.

**Quando comincia l'università di Ada si ricava, non si scrive**: `ADA_SCUOLA` dice classe e anno
scolastico in corso (3ª superiore nel 2026/27) e `adaUniversita()` ne ricava inizio (settembre
2029), fine e anni che mancano. Scritto a mano, fra due anni direbbe ancora 2029 senza che niente
lo segnali.

I due 💬 (università di Ada, assicurazione) aprono un popup col **prompt già scritto** da
incollare in una chat con l'IA. Quello dell'assicurazione ci mette dentro i numeri che la pagina
già conosce — le quattro date, il fabbisogno voce per voce **su ciascun orizzonte**, il
capitale da assicurare, le dotazioni, le scoperture — perché riscriverli a memoria è il modo più
semplice di far ragionare l'IA su cifre sbagliate, e chiede come prima cosa **il premio annuo**,
che è il numero che serve per compilare la voce. ⚠️ La voce dell'assicurazione **resta fuori dal
proprio prompt**: il premio è quello che si sta chiedendo, e il capitale è la somma delle altre.

⚠️ **💼 Redditi da lavoro perso è un terzo riquadro, e sta FUORI da ogni conto.** Sotto
Dotazioni, con le stesse quattro colonne: quanto si continuerebbe a guadagnare lavorando fino a
ciascuna data. Non entra nel totale delle dotazioni, né nella scopertura, né nel capitale da
assicurare — sommarlo alle dotazioni conterebbe due volte l'accordo con l'azienda, che quello
stesso periodo lo copre già dalla sua parte. È un metro accanto agli altri, non una voce di
`fnz_coverage_items`: non si compila, non si esclude e non si scrive a mano.

Il netto di un anno è 💶 Reddito → 🧾 Liquidazione, colonna **calcolata** *Reddito netto*
(`INCOME_CALC.reddito_netto`, imponibile − imposta netta) dell'anno più recente **che ce l'ha** —
stessa regola di «Pensione Ada» e dell'accordo.

⚠️ **Le quattro colonne non rispondono tutte alla stessa domanda**, ed è voluto: sulle due uscite
si conta quello che **manca** rispetto a lavorare fino alla pensione, sulle due pensioni il netto
per gli anni che restano da lavorare.

| Colonna | Conto | Con netto 50.000 e aspettativa 1 anno |
|---|---|---|
| 🌿 Uscita naturale | `20 % × 5 anni di scivolo` | 50.000 |
| 🧭 Uscita concordata | lo stesso, **più** `uscita().aspettativa` a reddito intero | 100.000 |
| 🚪 Anticipata · 🏛️ Vecchiaia | `netto × sc.anni` | 9,4 anni → 470.000 |

⚠️ **La percentuale è quella che si PERDE, non quella che si prende**: durante lo scivolo
l'azienda paga l'80 %, quindi ne manca il 20 — `SCIVOLO_PERSO_PCT = 20`. Scritta al contrario
darebbe un numero quattro volte più grande e altrettanto plausibile.

⚠️ **Il garden leave NON entra**: quello l'azienda lo paga per intero, quindi non è reddito
perso. L'aspettativa sì, è non retribuita, e vale il netto pieno — sono due cose diverse proprio
come le tiene distinte `uscita()`, e schiacciarle in «tutti gli anni oltre i cinque» conterebbe
come persa una retribuzione che invece si prende.

⚠️ **Le due uscite non dipendono dalla data ma dalle durate**, quindi il conto si fa anche senza
la simulazione INPS; le due colonne della pensione sì, e senza `sc.anni` tornano `null` — non
zero — come i flussi del fabbisogno.

⚠️ **I 5 anni sono gli STESSI di `USCITA_NATURALE_ANNI`** e non una seconda costante uguale per
caso: l'uscita naturale *è* l'anticipata meno lo scivolo, e la concordata quello scivolo se lo
porta dentro. Uno `SCIVOLO_ANNI = 5` scritto accanto sarebbero due verità sullo stesso dato.

⚠️ **Sotto ogni importo la cella scrive da quali anni viene** (`notaRedditoLavoro`): «5 anni di
scivolo al 20 % + 1 di aspettativa al 100 %». Un numero che non si può rifare a mente è un numero
di cui non ci si fida.

⚠️ **Il netto si legge da `INCOME_CALC` e non si riscrive qui**: una seconda formula per lo
stesso numero sono due redditi diversi il giorno che una delle due cambia. Ne eredita anche il
caveat — **non toglie le addizionali** regionale e comunale — e la cella lo dice. Senza
imponibile o imposta netta il riquadro non inventa niente: dice dove compilarli.

⚠️ **Le voci NON sono una tabella, sono schede** (`.cov-item` + `.cov-vals`): tre colonne di
importi accanto a una voce che è prosa, coi caratteri di sistema grandi, danno righe alte mezzo
schermo col testo oltre il bordo destro — provato e ritirato. I tre importi sono una griglia
etichettata che **va a capo da sé** (`auto-fit` con la soglia in `rem`, quindi cresce col testo)
invece di scorrere di lato: un numero oltre il bordo è un numero che non si legge. È la stessa
scelta della tabella di «Ti pisasti?» in nativo. Il **riepilogo** resta una tabella — poche righe
ed etichette corte — con l'anno in intestazione e il nome breve sotto.

### Memorandum (`mm_`)
| Table | Purpose |
|---|---|
| `mm_cards` | Le schede. `kind` vale `'nota'`, `'lista'`, `'diario'` o `'link'`; `riservato` le tiene fuori dagli elenchi |
| `mm_attachments` | Quel che una scheda porta con sé: oggi il solo indirizzo di un 🔗 Link (`tipo` `'link'`\|`'file'`, `url`, `position`) |
| `mm_card_categories` | Associazione scheda ↔ `cm_categories` |
| `mm_images` | Metadati delle foto (i file stanno nel bucket `mm-images`) |
| `mm_list_items` | Voci di una lista: `text`, `done`, `position`, `done_at` |
| `mm_diary_metrics` | Le misure di un diario: `kind` `'scala'` (con `min_value`/`max_value`) \| `'numero'` (con `unit`) \| `'bool'` \| `'scelta'` (con `options`); `hint` spiega cosa vuol dire il punteggio |
| `mm_diary_entries` | Le registrazioni: `title` (obbligatorio lato app), `entry_date`, `note`, e `measures` jsonb |

**Una scheda è una riga sola in tutt'e quattro i casi**: il tipo è una colonna, non una tabella a
parte, quindi ricerca, categorie, colore, 📌 e foto valgono uguale per note, liste, diari e link.
`kind` ha `DEFAULT 'nota'`: le schede nate prima della colonna restano quello che erano.

### 🔗 Link — la scheda che nasce dal tasto «Condividi»

Una scheda `link` è **UNA cosa condivisa** (un video, un articolo), non una raccolta: raccogliere
lo fanno già categorie, ricerca e 📌, che valgono per tutti i kind, e una scheda contenitore
reimplementerebbe la lista. È l'unico tipo con un **campo obbligatorio** — senza indirizzo non
porta da nessuna parte, cioè è rotta, non vuota.

⚠️ **In `mm_attachments` c'è l'URL e basta.** Id del video, miniatura e nome del sito si
**ricavano** da lui ogni volta (`ytIdDa`, `thumbDa`, `hostDa` in `memo.html`): archiviarli sarebbe
una seconda verità sullo stesso dato, che diverge il giorno che una delle due cambia. È la stessa
scelta delle colonne calcolate della liquidazione in `fnz_income`.

⚠️ **La copertina di YouTube non costa niente**: è un url pubblico di `img.youtube.com`, quindi
nessuna API, nessuna chiave e **nessun byte nel bucket** — mille link condivisi stanno in qualche
decina di KB di tabella. Si usa `hqdefault`, che c'è su ogni video: `maxresdefault` manca sui video
vecchi e darebbe un riquadro rotto. Un link che una copertina non ce l'ha mostra il nome del sito,
non un'anteprima inventata.

⚠️ **Nessuna colonna per Drive** (`storage_path`, `mime`, `size_bytes`): oggi non le riempirebbe
nessuno, e una colonna che nessuno riempie è un invito a reimplementare due volte la stessa cosa —
le aggiunge la migration dei file, quando ci saranno i file. `tipo` ammette già `'file'` proprio
perché quel giorno non si debba toccare anche il vincolo.

#### La condivisione arriva dall'APK NATIVA, e da lì soltanto

`com.garsal.appsphere` dichiara `ACTION_SEND` + `text/plain`: `gestisciCondivisione()` in
`MainActivity.kt` mette testo e oggetto in `core/Condivisione.kt`, la navigazione porta a Memo, e
`MemoScreen` apre l'editor con una scheda 🔗 Link **già compilata**
(`BozzaScheda.daCondivisione`).

⚠️ **L'intent-filter lo dichiara UNA sola APK.** I due APK convivono sullo stesso telefono con lo
stesso logo, distinti solo dal fondo bianco/nero: dichiarandolo anche in `com.garsalapps`, nel
menù «Condividi» comparirebbero **due voci indistinguibili** e toccherebbe indovinare ogni volta. È
la stessa ragione per cui hanno due schemi OAuth diversi.

⚠️ **La voce si è SPOSTATA, non aggiunta** (nativa v1.0.69, WebView v1.1.1). Fino alla v1.1.0 la
riceveva l'APK WebView — `handleSharedText` apriva `memo.html`, che con `openMemoFromSharedLink()`
compilava la scheda — e la ragione dello spostamento è **l'interfaccia**: quella scheda si
compilava dentro un WebView, in una pagina pensata per il PC. Nel WebView non ci sono più né
l'intent-filter né `handleSharedText` né l'iniezione in `onPageFinished`, e in `memo.html` al
posto di `openMemoFromSharedLink()` (e di `primoUrlIn`) c'è il commento che dice dov'è finita.
⚠️ Non sono due strade che convivono: aggiungerne indietro una qualsiasi rimette le due voci
indistinguibili nel menù «Condividi».

⚠️ **Fra il tocco su «Salva in Memo» e la scheda ci stanno la biometrica e, la prima volta, il
login nel browser.** Per questo la condivisione vive in un oggetto in memoria e non nell'intent:
`getIntent()` lo restituirebbe a ogni ricreazione dell'Activity, riaprendo la stessa scheda giorni
dopo senza che nessuno abbia condiviso niente — è la stessa ragione per cui il deep link dell'OAuth
si azzera appena letto. E per la stessa ragione **non sta nelle preferenze**: sopravviverebbe al
riavvio dell'app, che è l'opposto di quel che serve.

⚠️ **Il titolo arriva da `EXTRA_SUBJECT`**, che YouTube riempie col titolo del video: è già pronto,
non costa nessuna chiamata di rete e funziona anche offline.

**Quando quel campo non c'è, il titolo si ricava dal link**, in quattro gradini:

| Gradino | Da dove | Quando |
|---|---|---|
| `EXTRA_SUBJECT` | l'app che condivide | quando c'è: è esatto e gratis |
| `titoloYouTube()` | oEmbed di YouTube | solo sui link YouTube, nessuna chiave né API |
| `titoloDaSlug()` | l'indirizzo stesso | tutto il resto, senza rete |
| `hostDa()` / `Link.sito()` | il nome del sito | ultima spiaggia |

⚠️ **La scaletta esiste in due posti e va cambiata in tutt'e due**: `Link.titolo()` in
`memo/MemoData.kt`, per la condivisione, e `riempiTitoloSeVuoto()` in `memo.html`, dove il caso che
conta non è la condivisione ma il **link incollato a mano dal PC** — lì `EXTRA_SUBJECT` non esiste
affatto, e senza questi gradini ogni video si chiamerebbe «youtu.be».

⚠️ **Non sovrascrive mai un titolo che c'è**, né quello scritto a mano né `EXTRA_SUBJECT`: un
ripiego che prende il posto del dato esatto è un peggioramento silenzioso.

⚠️ **Nel web è in due tempi, in nativo no, e non è una divergenza**: là il campo è già a schermo
mentre si scrive l'url, quindi prima lo slug (immediato) e poi l'oEmbed che rimpiazza il
provvisorio — e solo **se il campo porta ancora esattamente quel provvisorio**, perché una risposta
che arriva dopo un cambio di url metterebbe il titolo di un altro video (`titoloToken`). Qui la
scheda si apre **già compilata**, quindi si aspetta la risposta *prima* di aprirla — e solo nel
caso raro in cui l'oggetto manchi *e* il link sia di YouTube. Il risultato è lo stesso, l'ordine
dei gradini pure.

⚠️ **`titoloYouTube` fallisce in silenzio.** Senza rete, o il giorno che quell'endpoint chiudesse,
la scheda nasce lo stesso col ripiego: un titolo è una comodità, non una condizione. In nativo i
tetti di attesa stanno **sulla connessione** e non solo su `withTimeoutOrNull`: una lettura
bloccante non si annulla, e senza di loro l'attesa la deciderebbe il socket — con la scheda che non
si apre intanto.

⚠️ **Uno slug non è un titolo**, ed è per questo che viene *dopo* gli altri due: è quel che il sito
ha scritto nell'indirizzo per i motori di ricerca, spesso troncato. Su YouTube non si usa affatto —
lì lo slug è l'id del video (`jNQXAC9IVRw`), che come titolo è peggio del nome del sito. La pulizia
dell'id in coda vuole **almeno tre cifre**: senza quella soglia «Covid-19» diventerebbe «Covid».

⚠️ **La scheda non si salva da sola**: si apre l'editor già compilato e il salvataggio resta un
gesto. Il tasto «Condividi» sta accanto a mille altri, e una condivisione per sbaglio non deve
lasciare una riga che poi qualcuno deve andare a cercare per cancellarla. Un testo condiviso **senza
url** non si butta via: diventa una nota, e il form lo dice (`avvisoIniziale`) invece di lasciarlo
scoprire.

⚠️ **Il punto nel nome del sito è il controllo che conta** (`Link.valido`, `urlValido` nel web):
senza, `URI` accetta `https://ciao` come indirizzo validissimo, e una parola secca condivisa da
un'altra app diventerebbe una scheda link che non porta da nessuna parte, invece della nota che è.

⚠️ **Una condivisione arrivata a form aperto ha bisogno della `key`** in `MemoScreen`: `MemoForm`
tiene la bozza in un `remember` senza chiave, quindi senza di lei resterebbe a schermo quel che si
stava scrivendo e la scheda condivisa non si vedrebbe affatto.

#### I link ci sono anche in nativo

`memo/` legge `mm_attachments` e ha `TipoScheda.LINK`: la Tab 🔗 Link, la copertina sulla scheda in
elenco, la vista propria col pulsante *▶️ Apri* e il campo **Indirizzo** nel form. Le regole
duplicate da tenere allineate col web sono tre, e sono le stesse che valgono di là:

- **dall'url non si archivia nient'altro**: `Link.idYouTube` / `Link.copertina` / `Link.sito`
  in `MemoData.kt` sono i gemelli di `ytIdDa` / `thumbDa` / `hostDa` in `memo.html`, e vanno
  cambiati insieme;
- **una scheda = un allegato, aggiornato e non ricreato**: `MemoRepository.salvaLink` ricalca
  `syncLinkAttachment()` — il giorno che gli allegati saranno più d'uno, `position` deve
  sopravvivere al salvataggio;
- **un link è il suo indirizzo**: `BozzaScheda.linkValido` è l'unico campo obbligatorio di tutto
  Memo, col **punto nel nome del sito** come controllo che conta — senza, «ciao» passerebbe per un
  indirizzo validissimo. Il controllo però sta in `Link.valido` e `linkValido` ci si appoggia: la
  regola è una sola, e la usa anche la condivisione per decidere se quel che è arrivato è un link
  o una nota. Stessa regola di `urlValido` nel web.

⚠️ **`TipoScheda.daONull()` resta anche adesso che nessun tipo è sconosciuto**, e non è codice
morto: distingue «tipo che non conosco» da «tipo che non c'è». Sul primo torna `null` e la scheda
**non viene proprio caricata**; chiave assente o vuota resta `NOTA`, che è il `DEFAULT` del
database e sono le schede nate prima della colonna. È la guardia che ha impedito alle schede
`'link'`, prima che il nativo le conoscesse, di comparire come note senza indirizzo e di
**diventarlo davvero** al primo salvataggio — e che farà lo stesso col prossimo tipo nuovo.

⚠️ **La condivisione è di questa APK** (vedi sopra): l'intent-filter `ACTION_SEND` + `text/plain`
lo dichiara solo `com.garsal.appsphere`, e da lì nasce una scheda 🔗 Link già compilata. Il WebView
i link li mostra e basta.

⚠️ **Il titolo ricavato dal link c'è anche di qua, ma solo per la condivisione**: `Link.titoloDaSlug`
e `Link.titoloYouTube` sono i gemelli di `titoloDaSlug()` e `titoloYouTube()` di `memo.html` e vanno
cambiati insieme. **Scrivendo un link a mano nel form il titolo non si compila da sé**: là lo fa
`onLinkUrlInput` con la sua pausa di digitazione, qui il campo resta come lo si scrive — è una
comodità che manca, non una divergenza di dati.

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
| `ps_objectives` | Obiettivi di peso (nata a mano, non sta in nessuna migration) |
| `ps_milestone_prizes` | Il premio cibo grattato per una soglia: `prize_id`, `won_on` e `consumed_on` (il «Mangiato !!!») |
| `ps_milestone_points` | `⭐ Punti Totali Traguardi Intermedi`, una riga per obiettivo |

Le ultime due (`20260824100000_ps_milestone_prizes.sql`) esistono perché premi e punti stavano in
`localStorage` sul web e nelle preferenze del telefono nel nativo: erano **due verità diverse
sulla stessa stellina**. Ora il DB è la fonte di verità e il locale resta cache. `consumed_on` è
l'unica colonna sul «Mangiato !!!» — niente booleano accanto alla data, che sarebbe un secondo modo
di dire la stessa cosa.

### Diario alimentare (`al_`)
| Table | Purpose |
|---|---|
| `al_profile` | Una riga per utente. ⚠️ Di questa tabella si usa **solo `activity`** (fattore LAF): data di nascita, altezza e sesso si leggono da `cm_profile` |
| `al_foods` | Gli alimenti conosciuti. `source` `'base'` (voci generiche di partenza) \| `'off'` (Open Food Facts, col `barcode`) \| `'usda'` \| `'manuale'`; valori **per 100** di `unit` (`'g'` \| `'ml'`). `default_grams` è la porzione abituale e `portion_label`/`portion_label_plural` come si chiama. `verified` = i numeri li ha guardati l'utente |
| `al_log` | Le righe del diario: `day`, `meal`, `grams`, `unit`, e i valori per 100 **congelati sulla riga** |
| `al_days` | Il target di calorie di una giornata, **congelato**, con gli ingredienti del conto (`weight_kg`, `bmr`, `tdee`, `deficit_kcal`) |

⚠️ **I dati anagrafici stanno in `cm_profile` e da qui si leggono soltanto.** Chiederli di nuovo
in ogni app che ne ha bisogno vuol dire due altezze diverse il giorno che una delle due si
corregge, e nessun modo di sapere quale è quella giusta. La pagina li mostra in sola lettura con
il pulsante che apre la scheda (`/#profilo`, che `index.html` intercetta all'avvio per aprire il
modale invece di lasciare sulla home); `al_profile.activity` resta di qua perché è una scelta di
questa pagina e in `cm_profile` non c'è. Le colonne `birth_date`, `height_cm` e `sex` di
`al_profile` non vengono più né lette né scritte — restano lì in attesa di una migration che le
tolga.

⚠️ `cm_profile.sesso` ammette anche `'Altro'`, che Mifflin-St Jeor non prevede: le sue due
varianti differiscono di una costante (+5 contro −161), quindi lì si usa la **via di mezzo** e la
pagina lo scrive nella spiegazione del target, col pavimento più alto dei due (1500). Fermare il
conto sarebbe la scelta pulita e inservibile — l'app non calcolerebbe più niente per un campo che
non cambia l'ordine di grandezza del risultato. La casella **vuota** invece ferma il conto per
davvero: non dice quale variante usare.

⚠️ **Il target non si archivia in nessuna impostazione, si ricava.** `calorie.html` legge
l'ultimo obiettivo **attivo** di `ps_objectives` e le sue milestone, e ne ricava:

```
basale (Mifflin-St Jeor su età, altezza, peso, sesso)
  × al_profile.activity                       = consumo stimato
  − deficit                                   = target del giorno
```

⚠️ **Il deficit è la somma di DUE addendi, e la separazione è la funzionalità.** I traguardi di un
obiettivo non sono equidistanti — chi ne mette tre nel primo mese e uno nei due successivi vuole
perdere in fretta e poi tenere — quindi:

| Addendo | Formula | Perché |
|---|---|---|
| **Ritmo del tratto** | `kg/giorno del tratto in corso × 7700` | Un piano che chiede 0,70 kg a settimana vuole ~770 kcal al giorno. Spalmare la perdita residua su tutto il tempo residuo dà lo **stesso numero nei due tratti** e fa restare indietro proprio dove il piano correva |
| **Recupero dello scarto** | `(peso vero − peso di piano) × 7700 / giorni che restano **in tutto**` | È l'addendo che stringe il target da sé quando si è indietro e lo allarga quando si è avanti. Sui giorni del **solo tratto** sarebbe feroce a ridosso di un traguardo: due chili in cinque giorni non sono un obiettivo |

Senza il primo il piano non si sente, senza il secondo non ci si accorge di essere rimasti
indietro. La somma **non scende sotto zero**: essere molto avanti allenta il target fino al
mantenimento, non oltre — un deficit negativo sarebbe l'app che invita a mangiare di più per
tornare sulla curva. `segmentoDi()` trova il tratto; senza curva (meno di due traguardi) o a
piano finito si ripiega sulla media fino alla fine, che è tutto quel che si può dire.

⚠️ **L'ultimo traguardo vale come peso finale solo se i traguardi sono almeno due**: uno solo è il
punto di partenza, non la meta, e prenderlo per tale fa credere l'obiettivo già raggiunto (peso di
oggi ≤ «finale») e azzera il deficit senza dire niente. Con meno di due si usa `ob.end_weight`.

⚠️ Fino alla v1.0.1 il deficit era la sola media `(peso di oggi − peso finale) / giorni che
restano`. Su un piano a due velocità (3 kg nel primo mese, 1 kg nei due dopo) dava 322 kcal nel
tratto che ne chiedeva 770: il ritmo del piano non si sentiva affatto.

⚠️ `pesoPianoAl()` è la copia di `getInterpolatedTarget()` di `weight-quest.html`: **se cambia
là va cambiata anche qui**, o le due pagine daranno due traguardi diversi per lo stesso giorno.

⚠️ **Il target non ha pavimenti: è quello che il piano richiede, per basso che sia** — anche
negativo, che è il modo più chiaro di dire che in quei giorni quel peso non ci si arriva nemmeno
digiunando. `KCAL_ATTENZIONE` (1500 M / 1200 F) fa comparire un **avviso** e nient'altro: la
decisione di allargare il piano è di chi legge, non della pagina. Fino alla v1.1.0 era un
pavimento vero e il target ci si fermava sopra.

In ⚙️ Impostazioni il riquadro **📐 Stima delle calorie** è in due parti: il riepilogo (peso di
oggi, peso di piano, scarto, kg e giorni che restano, basale, consumo, target di oggi) e la
tabella dei **tratti**, una riga per coppia di traguardi, con periodo, pesi di partenza e fine,
target, ritmo, deficit e consumo. ⚠️ Ogni tratto si calcola sul **peso medio che il piano prevede
lì** e non su quello di oggi — il basale cala col peso, quindi a parità di ritmo l'ultimo tratto
chiede meno del primo — e **senza il recupero dello scarto**: sono i valori «se stai sul piano»,
e nei tratti futuri lo scarto sarebbe un'invenzione. Il conto vero di oggi, recupero compreso,
sta nel riquadro del Diario. ⚠️ L'ordine delle colonne è quello di lettura: sul telefono la
tabella scorre dentro il suo riquadro, quindi il **target** viene subito dopo i pesi e ritmo,
deficit e consumo possono uscire dal bordo.

`al_days` è la stessa scelta di `ps_weight_tracking.target_weight`, per la stessa ragione: il
target si **congela** alla prima riga segnata quel giorno, e spostare un traguardo in «Ti pisasti?»
domani non deve riscrivere il giudizio su un giorno già passato. Si ricalcola **solo su richiesta
esplicita**, un giorno per volta. Per lo stesso motivo `al_log` porta i valori nutrizionali sulla
riga e non solo `food_id`: correggere un alimento non riscrive quel che si è mangiato il mese
scorso, e cancellarlo non fa sparire le calorie già contate (`food_id` va a NULL, la riga resta).

⚠️ **La porzione abituale è tre colonne, non una**: `default_grams` dice *quanto* (un uovo 55 g,
una pizza 300 g, un cucchiaio d'olio 10 g), `portion_label` e `portion_label_plural` *come si
chiama*. Il singolare e il plurale sono due colonne perché **in italiano il plurale non si ricava
a regola proprio dove serve di più** — uovo → uova — e una parola sbagliata a schermo si legge
come un difetto dell'app; nemmeno una colonna sola con dentro `'uovo|uova'`, che sarebbe un
formato da interpretare dentro un campo di testo. Il plurale è **facoltativo** e ripiega sul
singolare, e senza nessuna etichetta si legge «porzione / porzioni», vero per qualunque cosa.
⚠️ Un'etichetta **non si inventa**: chi la legge scritta se la crede, e sono le calorie della
giornata — `20260830120000_al_porzione_etichetta.sql` ne compila 17 sulle 44 voci base, e le
altre (petto di pollo, insalata, zucchine) restano senza perché «1 porzione» è già quel che sono.

⚠️ **`unit` dice se quell'alimento si pesa o si versa** (`20260901120000_al_unita_g_ml.sql`):
`'g'` = i valori sono per 100 g, `'ml'` = per 100 ml, che è come l'etichetta scrive il latte,
l'olio, il vino e una spremuta. **Non è una conversione e nessuna densità entra in gioco**: il
conto resta la stessa moltiplicazione — 250 ml × (kcal per 100 ml) / 100 — e convertire i ml in
grammi vorrebbe dire inventare un numero (l'olio sta a 0,91, il miele a 1,42) per rimoltiplicarlo
poi per valori che quella densità l'avevano già dentro. Le colonne restano `*_100g` e `grams` coi
loro nomi: dicono «per cento unità di questo alimento», e `unit` dice quali — rinominarle sarebbe
una migration che tocca due implementazioni e una Edge Function senza cambiare nessun numero.

⚠️ **Sta anche su `al_log`, e non è un doppione**: la riga porta già i valori congelati, e
l'unità è parte di quei valori — senza, cambiare un alimento da ml a g (o cancellarlo, che porta
`food_id` a NULL) riscriverebbe all'indietro l'etichetta di righe già segnate. È la stessa scelta
dei valori nutrizionali sulla riga, un passo più in là. ⚠️ **`NOT NULL DEFAULT 'g'` e nessun
backfill «intelligente»**: indovinare i liquidi dal nome marcherebbe in ml anche il latte in
polvere e l'olio di semi di una tabella di composizione, che in grammi ci stanno di proposito.

La scelta si fa **da 🍎 Alimenti → ✏️** (i due pulsanti *⚖️ Grammi* / *🥛 Millilitri*), e da lì
in poi si legge dappertutto: la finestra della porzione chiede «Quantità (millilitri)» invece di
«Peso (grammi)» e offre i tagli dei liquidi (`ML_RAPIDI`: 50, 100, 150, 200, 250, 330, 500 — un
bicchiere, una lattina, una bottiglietta), le righe del diario si leggono «250 ml», e nella
tabella di 🍎 Alimenti un liquido porta il badge `100 ml` accanto al nome. ⚠️ **Il badge compare
solo sui liquidi**, per la stessa ragione del ✅: i grammi sono quel che è quasi tutto, e un «g»
su ogni riga smetterebbe di distinguere qualcosa.

⚠️ **Nel form dell'alimento le due scelte vicine sono diverse e si comportano al contrario**: la
misura (g/ml) è una proprietà dell'alimento e **si riapre su quel che c'è in archivio**; l'unità
dei valori (100 / 1 porzione) è una dichiarazione sui numeri scritti adesso e **riparte sempre da
100** — ricordarla farebbe dividere i valori una seconda volta al primo salvataggio.

⚠️ **L'unità di un prodotto di rete la legge `al-food-search`, non la pagina**: viene da
`product_quantity_unit`, `serving_quantity_unit` o dalla quantità in chiaro («1 l», «33 cl»),
cioè è **scritta sul prodotto** e non dedotta dal nome. Senza nessuna unità dichiarata resta
`'g'`. È la stessa regola per cui la normalizzazione vive solo nella Edge Function.

⚠️ **`verified` non è una seconda `source`, ed è ortogonale a lei**
(`20260831120000_al_foods_verified.sql`): `source` dice **da dove viene** un numero, `verified`
dice **se qualcuno l'ha guardato**. Un prodotto letto da Open Food Facts e confrontato con
l'etichetta che si ha in mano è verificato; una voce scritta a mano di fretta non lo è —
ricavarla dalla fonte le farebbe dire una cosa diversa da quella per cui esiste. È la domanda
che ha lasciato la «Pizza condita» in archivio a 1225 kcal per 100 g: la riga sembrava una riga
qualunque, e non c'era nessun posto in cui dire «questa l'ho controllata».

⚠️ **`NOT NULL DEFAULT false` e nessun backfill, nemmeno sulle voci `'base'`**: la spunta
significa «l'ho controllato io», e metterla d'ufficio su righe che nessuno ha mai riguardato
direbbe il falso proprio nella colonna che esiste per dire se ci si può fidare — un catalogo
tutto verificato al primo avvio non distingue più niente. Le voci base restano quel che dice il
riquadro di 🍎 Alimenti: valori indicativi delle tabelle di composizione pubbliche. Per la stessa
ragione è un **booleano e non una data di verifica**: la domanda è «me ne fido?», e una data
sarebbe un secondo dato da tenere aggiornato per rispondere alla stessa.

La spunta si mette dal form ✏️ dell'alimento e si vede in due posti: **nelle righe di ricerca del
📓 Diario** (badge *✅ verificato*), che è il momento in cui si sceglie quale numero far entrare
nella giornata, e **nella tabella di 🍎 Alimenti**, accanto al nome. ⚠️ **Solo sulle righe del
catalogo**: un risultato di rete non è ancora in archivio, quindi nessuno ha potuto verificarlo e
un ✅ lì direbbe il falso — è la stessa distinzione 📗/🌐 che l'icona in testa alla riga già fa. E
**solo quando c'è**: «non verificato» è lo stato normale di quasi tutto, e un segno su ogni riga
smetterebbe di distinguere qualcosa. ⚠️ Nella tabella il ✅ sta **accanto al nome e non in una
colonna sua**: l'ultima colonna di una tabella che scorre di lato sta oltre il bordo destro, ed è
la stessa ragione per cui ✏️ e 🗑 stanno in testa alla riga.

In 🍎 Alimenti c'è il filtro **✅ Solo verificati** (`S.soloVerificati`), che restringe l'elenco a
quelli con la spunta; l'intestazione dice sempre quanti sono sul totale, che è la domanda «quanto
manca» senza bisogno di accenderlo. Tre cose volute:
- è un **AND col testo**, non una ricerca a sé: accendendolo mentre si cerca, l'elenco si
  accorcia invece di ripartire da capo;
- **vive in memoria e non in `localStorage`**, a differenza del filtro per carta di Spese
  Personali: è il taglio di una sessione di lavoro, e ritrovarlo acceso domani farebbe sembrare
  mezzo vuoto un catalogo pieno;
- ⚠️ **col filtro acceso la ricerca in rete non parte proprio**: un risultato di Open Food Facts
  non è in archivio, quindi nessuno l'ha verificato e non verrebbe comunque mostrato — e OFF le
  ricerche testuali le conta, una decina al minuto. Per la stessa ragione l'elenco vuoto **dice
  che il filtro è acceso**: «nessun alimento» sarebbe indistinguibile da un catalogo vuoto.

⚠️ **Una casella vuota resta vuota e non vale zero**, né in `al_foods` né in `al_log`: «non so
quante fibre ha» e «non ha fibre» sono due cose diverse, e uno zero falso farebbe sembrare magro
un alimento di cui non si sa niente. È la stessa scelta di `fnz_income` e delle misure di Memo.

### Spese Ada (`ada_`)
| Table | Purpose |
|---|---|
| `ada_super_categories` | Super-categorie: **l'unico livello che porta il `color`** |
| `ada_categories` | Voci di spesa (nome unico per utente, `icon`, `super_id`); `color` non è più usato |
| `ada_transactions` | Movimenti importati dal conto: `amount` **con segno**, **una sola** `category_id`, `card_identification`, `external_id` (unico per `bank_connection_id`) |
| `ada_merchant_map` | Negozi imparati: `merchant_key` normalizzato → categoria, **per fonte** (`source` `'bank'`\|`'excel'`, unico su `(user_id, source, merchant_key)`) |

Sono volutamente **separate dalle `ca_*`**: le spese di Ada non devono entrare nei totali di
Spese Famiglia, e una separazione per campo sarebbe retta solo finché ogni query si ricorda di
filtrarla.

### Spese Personali (`sal_`)
| Table | Purpose |
|---|---|
| `sal_super_categories` | Super-categorie: **l'unico livello che porta il `color`** |
| `sal_categories` | Voci di spesa (nome unico per utente, `icon`, `super_id`) |
| `sal_transactions` | Movimenti del conto personale: `amount` **con segno** (entrate comprese), **una sola** `category_id`, `card_identification`, `external_id` (unico per `bank_connection_id`) |
| `sal_merchant_map` | Negozi imparati: `merchant_key` normalizzato → categoria, **per fonte** (`source` `'bank'`\|`'excel'`, unico su `(user_id, source, merchant_key)`) |

Stesso schema delle `ada_*` e stessa ragione per cui è separato dalle `ca_*`: le spese del conto
personale di Salvatore non devono entrare nei totali di Spese Famiglia. **L'unica differenza è
cosa ci finisce dentro**: qui si archiviano anche le entrate, perché la pagina tiene il saldo del
periodo — nessuna colonna in più, `amount` è già con segno.

### Spuntiamola (`sp_`)
| Table | Purpose |
|---|---|
| `sp_settings` | Una riga per utente: traguardo, emoji, periodo (`start_date` → `end_date`), `skip_weekend`, `mood` |
| `sp_checks` | Una riga per giorno spuntato (`UNIQUE (user_id, day)`); `emoji` è quella pescata a caso alla spunta |
| `sp_key_days` | Giornate chiave (`UNIQUE (user_id, day)`); `label` è l'etichetta libera mostrata alla spunta |
| `sp_stecche` | Archivio delle stecche chiuse: traguardo e periodo com'erano, `mood`, `total_days`/`done_days`, `satisfaction` (1-100), `note`, e la fotografia jsonb di `checks` e `key_days` |

`mood` (`20260901100000_sp_mood_stecca.sql`) dice **con che voce l'app commenta la griglia**, e
ammette due valori: `'attesa'` — il tempo che passa avvicina qualcosa, quindi ogni spunta è un
giorno tolto di mezzo — e `'bei_giorni'` — il tempo che passa porta via qualcosa, quindi ogni
spunta è un bel giorno che se ne va e l'ultimo giorno è un addio, non un arrivo.

⚠️ **La colonna sta sulla STECCA e non sull'utente**: si aspettano le ferie e nel frattempo le
si vive, e le due cose convivono nel tempo senza essere la stessa. Sta anche in `sp_stecche`
perché riaprendo l'archivio non si saprebbe più di che stecca si trattava. Le righe nate prima
restano `'attesa'`, che è quello che erano davvero: l'app sapeva parlare in un modo solo.

### Forziere (`frz_*`)
| Table | Purpose |
|---|---|
| `frz_vault` | Una riga per utente: dov'è la cartella su Drive e i suoi due oggetti di servizio, più le date di collaudo ed export |
| `frz_files` | Una riga per file: `drive_file_id`, `meta_enc` (nome, tipo, dimensione vera, note — **tutto cifrato**), peso del cifrato |
| `frz_thumbs` | Le miniature, cifrate. Tabella a parte perché l'elenco si legge a ogni apertura e deve restare leggero |
| `frz_boxes` | Gli **scomparti**: `meta_enc` (nome ed emoji, cifrati), `position`. `frz_files.box_id` li collega |

⚠️ **Gli scomparti sono ORGANIZZAZIONE, non separazione**, e il nome inganna: due scomparti
**non si proteggono a vicenda** — le 24 parole sono una sola per tutto il forziere, quindi chi
lo apre li apre tutti. Servono a tenere in ordine (Documenti, Casa, Ada). La separazione vera —
un forziere che l'altro non apre — vorrebbe dire **24 parole per forziere**, cioè N segreti da
ricordare per sempre: è un'altra funzionalità e va decisa sapendo che costa quello.

⚠️ **Il nome dello scomparto è cifrato** come quello dei file: uno scomparto «Divorzio» scritto
in chiaro in una colonna racconta la storia da solo, che è precisamente ciò che
`frz_files.meta_enc` esiste per evitare. Ne discende che l'elenco degli scomparti si legge solo
a forziere aperto — e va bene, perché prima non c'è comunque niente da vedere.

⚠️ **Su Drive non cambia niente: nessuna sottocartella per scomparto.** Una cartella «Divorzio»
rimetterebbe in chiaro proprio il nome appena cifrato, e vanificherebbe l'aver dato ai file nomi
uuid. Gli scomparti vivono **solo nell'indice cifrato**, quindi la proprietà «ogni `.gpg` si
riapre da solo» resta intatta: perso il database si perde l'ordine, non un byte.

⚠️ **`box_id` è `ON DELETE SET NULL`, non CASCADE**: cancellare uno scomparto non porta via i
file, che tornano in «Senza scomparto» — un contenitore che si porta dietro il contenuto è il
modo più veloce di perdere un documento per sbaglio. NULL è uno stato buono e non un dato
mancante, ed è per questo che le righe già in archivio non hanno avuto bisogno di nessuna
migrazione. La conferma di eliminazione **dice cosa non succede**, o davanti a trenta documenti
si preme Annulla.

⚠️ **In queste tabelle non c'è niente da attaccare, ed è la scelta che regge tutto.** Il
segreto sono **24 parole** che stanno solo nella testa di Salvatore; i file cifrati stanno su
Google Drive e le due chiavi di servizio pure. Chi si portasse via un dump del database non
avrebbe nemmeno *su cosa* provare a indovinare.

⚠️ **Il database NON è indispensabile**, ed è la proprietà per cui il Forziere esiste: ogni
`.gpg` si riapre da solo con `gpg -d` e le 24 parole, e porta dentro di sé il proprio nome
originale (pacchetto *literal* di OpenPGP). Perso il database non si perde un byte — si perde
la comodità di sfogliare, cercare e vedere le miniature. Nessuna modifica futura deve togliere
questa proprietà.

⚠️ **Il formato è OpenPGP simmetrico e non un formato nostro.** La procedura di recupero
dev'essere **ricordabile a mente** — `gpg -d documento.gpg`, poi le 24 parole — e una ricetta
nostra (normalizzazione, sale, giri, HKDF) sarebbe fragile proprio dove non può esserlo: basta
sbagliare un dettaglio e non si apre più niente, e quei dettagli non stanno in nessuna testa.
Il file si porta dentro algoritmo, sale e giri, e il programma li legge da sé. Una prima
versione del progetto aveva quella ricetta ed è stata **ritirata prima di scrivere una riga**.

**I tre oggetti su Drive**, nella cartella `Forziere AppSphere` (che la Edge Function si crea
da sé, e **non** è quella dei backup — una rotazione sbagliata cancellerebbe il forziere):

| Oggetto | Cos'è | Chiuso con |
|---|---|---|
| `<uuid>.gpg` | un documento | le 24 parole |
| `indice.gpg` | la chiave dell'indice (32 byte casuali) | le 24 parole |
| `scorciatoia.gpg` | le 24 parole | la passphrase quotidiana |

⚠️ **`indice.gpg` fa anche da PROVA**: se si apre, le parole sono quelle giuste; se no, no. Un
verificatore a parte sarebbe una seconda verità sulla stessa domanda — e un oracolo in più per
chi prova a indovinare.

⚠️ **I metadati non passano da OpenPGP ma da AES-256-GCM** sotto la chiave dell'indice: ogni
apertura OpenPGP con una password rifà la derivazione lenta, e per trecento nomi di file
vorrebbe dire trecento derivazioni, cioè minuti. La chiave dell'indice è 32 byte casuali —
non si indovina, quindi non serve renderla lenta.

⚠️ **La ricerca è lato client e non può essere altrimenti**: il server i nomi non li può
leggere. È il prezzo dell'E2EE ed è giusto pagarlo qui.

⚠️ **Quel che resta visibile** a chi guarda il database o il Drive: **quanti** file ci sono,
**quanto pesano** e **quando** sono stati caricati. Non si può nascondere senza complicazioni
sproporzionate, e va detto invece di lasciarlo credere protetto.

### SOS (`sos_*`)
| Table | Purpose |
|---|---|
| `sos_types` | I diversi SOS. `current_seconds` è la durata del **prossimo** giro, `base_seconds` quella di partenza, `min_seconds`/`max_seconds` gli estremi entro cui le percentuali possono muoverla |
| `sos_outcomes` | Le risposte a «com'è andata?»: `points` (anche negativi) e `time_delta_pct` (positiva allunga, negativa accorcia) |
| `sos_messages` | Le frasi che scorrono sotto il countdown; `sos_type_id NULL` = vale per tutti i SOS |
| `sos_sessions` | Un giro: `planned_seconds`, `completed` (false = countdown interrotto), la risposta scelta e `seconds_before`/`seconds_after` |
| `sos_devices` | Il codice con cui l'APK si accoppia. `token` **è** la credenziale |

⚠️ **La regola del tempo sta nelle RPC, non nel client** — è la stessa scelta di `task_complete`
e `sf_finalize_challenge`, per la stessa ragione: due implementazioni della stessa formula sono
due durate diverse il giorno che una delle due cambia.

| Funzione | Chi la chiama | Cosa fa |
|---|---|---|
| `sos_config(token)` | APK | Tutti i SOS attivi con risposte e frasi, in una chiamata sola |
| `sos_session_start(token, type_id)` | APK | Apre il giro e **dice quanto dura** — la durata la decide il server |
| `sos_session_finish(token, session_id, outcome_id, …)` | APK | Applica punti e percentuale: `next = clamp(current × (1 + pct/100), min, max)` |
| `sos_session_log(token, type_id, outcome_id, …)` | APK | Il giro fatto senza rete, rispedito dopo: crea la sessione già chiusa e delega a `sos_session_finish` |
| `sos_device_create(name)` | `sos.html` | Genera un codice di accoppiamento |

`sos_session_finish` è **idempotente**: una sessione già chiusa risponde con quello che era stato
deciso allora invece di riapplicare punti e percentuale. Senza, il rinvio della coda offline
dell'APK conterebbe due volte gli stessi punti e sposterebbe il tempo due volte.

Le quattro RPC dell'APK sono eseguibili dalla **anon key**: il controllo è il codice di
accoppiamento (`sos_devices.token`), non il ruolo. Le tabelle restano dietro la RLS del
proprietario — da PostgREST con la anon key non si legge niente — e `sos_user_by_token`, che il
codice lo risolve, ha l'EXECUTE revocato ai client.

⚠️ **Le risposte si aggiornano riga per riga e non si cancellano per ricrearle**
(`salvaSos()` in `sos.html`): `sos_sessions.outcome_id` le cita, e ricreandole ogni giro passato
resterebbe agganciato a una riga che non esiste più. È la stessa scelta delle misure di un diario
in Memo. Le **frasi** invece si riscrivono da capo: nessuno le cita, sono testo in un ordine.

### Obiettivi (`ob_`)
| Table | Purpose |
|---|---|
| `ob_objectives` | Obiettivi, gerarchia a due livelli (`annual` → `quarterly`) via `parent_id` |
| `ob_metrics` | Metriche di un obiettivo (1..N) |
| `ob_measurements` | Rilevazioni: `value` e `note`, una per metrica e per giorno |
| `ob_milestones` | Curva attesa (`expected_value` per data) — base del semaforo |
| `ob_actions` | Le azioni: il «cosa faccio». Gemella di `ts_tasks`, stessi sei tipi |
| `ob_action_history` | Storico e punti delle azioni. Gemella di `ts_history`. `occurrence_date` = il giorno **per cui** l'azione era in calendario, accanto al `timestamp` che dice quando è stata chiusa |
| `ob_action_metrics` | Quali metriche un'azione dovrebbe muovere (anche più d'una) |

`ob_metrics.role` distingue **`primary`** (il risultato, alimenta la barra e il semaforo — **una sola per
obiettivo**, vincolo `uq_ob_metrics_one_primary`), **`control`** (secondo riscontro lagging) e **`leading`**
(lo sforzo).

⚠️ **`ob_metrics.kind` ha due soli valori, e ciascuno compila le sue colonne**
(`20260827110000_ob_metriche_semplificate.sql`):

| `kind` | Colonne che compila | Colonne che lascia NULL |
|---|---|---|
| `autovalutazione` | `min_value`, `max_value` (di norma 1-10) | `baseline`, `target`, `unit` |
| `automisurazione` | `baseline`, `target`, `unit` | `min_value`, `max_value` |

Il vincolo `ob_metrics_scala_per_tipo` lo impone: senza, una metrica potrebbe portare una scala
*e* un target, e quale dei due comanda sarebbe una scelta arbitraria dentro il codice. Gli estremi
si leggono quindi **da un posto solo** — la RPC `ob_metric_scale`, ricalcata in `scaleOf()`
(`obiettivi.html`) e in `ObMetrica.scala` (nativo) — e l'avanzamento resta la stessa formula per
tutt'e due: `(corrente − da) / (a − da)`.

`ob_metrics.descrizione` è **come votare** per un'autovalutazione (*1 = …, 10 = …*) e **cosa si
misura** per un'automisurazione, e viene riproposta a ogni rilevazione: se cambia il metro la serie
storica non è più confrontabile.

⚠️ Prima c'erano quattro `kind` (`state`, `cumulative`, `checklist`, `rubric`), la colonna
`direction`, la finestra `period`, `source_query` e una tabella di criteri pesati
(`ob_rubric_criteria`) con i punteggi in `ob_measurements.detail`: **tutto cancellato**, insieme ai
criteri e ai pesi che conteneva. Le rilevazioni no — il valore di una rubrica era già la media
pesata, e su una scala 1-5 resta quello che era: le rubriche sono diventate autovalutazioni 1-5, le
altre tre automisurazioni. `direction` in particolare era un secondo modo di dire quel che gli
estremi già dicono, e poteva contraddirlo.

#### Le azioni — tabelle proprie, non un collegamento a Tasks

`ob_actions` è la gemella di `ts_tasks`: **gli stessi sei tipi** (`single`, `recurring`,
`simple_recurring`, `multiple`, `free_repeat`, `workflow`), le stesse colonne nome per nome, gli
stessi punti (successo / fallimento / salto / ritardo), le categorie `cm_categories` e le priorità
`cm_priorities` **condivise con i task** — una terza tassonomia da tenere allineata a mano sarebbe
il difetto, non la separazione.

⚠️ **Tabelle separate e non un campo `objective_id` su `ts_tasks`**: i task sono letti da
`tasks.html`, dal planner, dall'APK WebView, da AppSphere nativa e dalle notifiche Smart Block. Un
campo da filtrare regge finché **ogni** query si ricorda di filtrarlo, ed è la stessa ragione per
cui le spese di Ada stanno nelle `ada_*` e non nelle `ca_*`.

⚠️ **Niente FOREIGN KEY verso `cm_categories` e `cm_priorities`**: quelle due tabelle non stanno in
nessuna migration (nascono a mano in produzione), e una FK farebbe fallire il `db push` su un
database nuovo. Se non si leggono, la pagina resta usabile senza invece di non aprirsi affatto.

⚠️ **`ob_action_history.action_id` è `ON DELETE SET NULL`, non CASCADE**, e la riga porta con sé
`action_title`: cancellare un'azione non deve riscrivere all'indietro i punti già presi, e una riga
che resta senza dire di che cosa parlava non si legge più. È la stessa scelta di `al_log`, che porta
i valori nutrizionali sulla riga invece del solo `food_id`.

⚠️ **Com'è finita un'azione lo dice lo storico, non lo `status`**: `terminated` lo diventa anche
fallendo (`ob_action_fail`). `esitoDi()` nella pagina legge l'ultima riga di storico che non sia
`terminated`, ed è la stessa lettura che fa `ob_objective_progress` per la barra dell'esecuzione.

#### Un'azione può muovere più metriche

`ob_action_metrics` (`20260827150000_ob_action_metrics.sql`) collega un'azione alle metriche che
la sua fatica dovrebbe spostare: **anche più d'una**, perché una lezione di conversazione muove
insieme la scioltezza e il numero di pause. Serve a dire *cosa* dovrebbe cambiare, e nient'altro —
non registra rilevazioni e non muove nessuna barra.

Tabella di collegamento e **non** una colonna `metric_ids uuid[]` come `categories`: lì l'array è
obbligato, perché `cm_categories` non sta in nessuna migration e una FK farebbe fallire il
`db push`; `ob_metrics` invece c'è, quindi la chiave esterna si può avere davvero — e fa un lavoro
vero, perché cancellando una metrica i suoi collegamenti spariscono da sé invece di restare come
id che puntano al nulla.

⚠️ **Si collegano le sole metriche del proprio obiettivo.** Il form offre solo quelle, ma
l'obiettivo di un'azione si può cambiare, e in quel momento i collegamenti di prima parlano di
metriche che non c'entrano più: il trigger `trg_ob_action_metrics_pulisci` **li toglie** invece di
rifiutare la modifica — spostare un'azione sotto un altro obiettivo è legittimo, ed è il
collegamento a non avere più senso, non lo spostamento. `onActionObjectiveChange()` fa lo stesso
nella pagina, così quel che si vede è già quel che verrà salvato.

⚠️ **I collegamenti si riscrivono da capo a ogni salvataggio** (`salvaCollegamentiMetriche`): si
cancellano tutti quelli dell'azione e si reinseriscono quelli scelti. È la stessa scelta delle
categorie di una scheda in Memo, per la stessa ragione — senza la cancellazione una metrica tolta
resterebbe attaccata per sempre, e l'azione continuerebbe a dire di muovere qualcosa che non muove.

`ob_task_links` — il collegamento a `ts_tasks` / `hb_habits` — **è stata eliminata**
(`20260827130000_ob_actions.sql`) con i collegamenti già salvati. Diceva soltanto che un task
esisteva altrove: da Obiettivi non si poteva né crearlo, né completarlo, né sapere com'era andato.
I task e le abitudini che citava restano dove sono, intatti.

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

Stessa regola per **Obiettivi**: il calcolo del progresso vive in `ob_objective_progress`, e
`ob_record_measurement` è l'**unico punto di scrittura** di una rilevazione — è lei a rifiutare un
voto fuori dalla scala della metrica, non il client
(`20260827110000_ob_metriche_semplificate.sql`), non il JS.
`ob_metric_current` è invece volutamente `SECURITY INVOKER`: riceve una riga `ob_metrics` dal chiamante,
quindi la RLS su `ob_measurements` deve restare attiva.

E vale **anche per le azioni di Obiettivi**, che sono la copia dei task
(`20260827130000_ob_actions.sql`):

| Funzione | Gemella di | Descrizione |
|---|---|---|
| `ob_action_complete(p_action_id, p_today)` | `task_complete` | Completa un'azione, tipo per tipo |
| `ob_action_skip(p_action_id, p_days)` | `task_skip` | La manda alla prossima volta |
| `ob_action_fail(p_action_id)` | `task_fail` | La segna fallita |
| `ob_action_next_recurring_date(p_action, p_base)` | `task_next_recurring_date` | Prossima data di una ricorrente |

⚠️ **La prossima occorrenza si calcola dalla vecchia occorrenza, non da oggi**, in tutt'e tre le
RPC e per tutti i tipi: `COALESCE(next_occurrence_date, start_date)` è la base, e `p_today` serve
solo a decidere se una singola è stata chiusa in ritardo. Verificato su un'azione scaduta dal 7
agosto e chiusa il 28: la ricorrente settimanale va al **14 agosto** (non al venerdì dopo oggi),
la «ogni 7 giorni» al 14, la singola saltata di 3 giorni al 10, la multipla alla data seguente del
suo elenco. Calcolarla da oggi salterebbe le volte arretrate senza dirlo.

Il comportamento per tipo è quello dei task, riga per riga. Due differenze **volute**, da non
"correggere" indietro:

1. la riga si cerca con `AND user_id = auth.uid()`. Le `task_*` sono `SECURITY DEFINER` e la RLS
   lì dentro non vale: senza quel filtro basta l'id di una riga altrui per completarla. Le `task_*`
   quel controllo non ce l'hanno, ma non è un motivo per rifare lo stesso buco;
2. non si tocca `cm_notification_rules`: le azioni non hanno (ancora) promemoria Smart Block, e
   cancellare regole per `app = 'tasks'` da qui spegnerebbe le notifiche di un task che non c'entra.

Scrivere un'azione è invece un `insert`/`update` diretto e **non** una RPC, e non è un'eccezione:
le RPC governano il ciclo di vita — dove va la prossima occorrenza — non com'è fatta l'azione. È la
stessa divisione di `saveTask()` nel web e di `TaskForm` nel nativo.

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
| `notification-action` | manuale (da `telegram-webhook` e dall'APK nativo) | Che cosa fa un pulsante di un promemoria: ✅ Fatto, ⏸ rinvia, ❌ annulla. **L'unica implementazione**, chiamata sia dal bot sia dal telefono |
| `forziere-drive` | manuale (da `forziere.html`) | Il ponte col Drive del Forziere: crea la cartella, apre i caricamenti, restituisce e cancella i file. ⚠️ **Non vede mai niente in chiaro** — tutto quel che le passa davanti è già cifrato con OpenPGP dalle 24 parole, che qui non arrivano né adesso né mai. Il caricamento **non passa di qui**: `upload-url` chiede a Google un indirizzo ripristinabile e i byte vanno dal browser a Google diretti (con ripiego su `put` per i file piccoli, se quella strada è bloccata). Lo scaricamento passa — Drive non ha indirizzi firmati — ma **in flusso** |
| `al-food-search` | manuale (da `calorie.html`) | **Legge e basta**: cerca un alimento per nome o per codice a barre nelle banche dati pubbliche e lo restituisce **già normalizzato**. Fonti in ordine: Open Food Facts Search-a-licious, la vecchia `/cgi/search.pl` come ripiego, e USDA FoodData Central se c'è il secret `USDA_API_KEY`. Ogni fonte torna col suo esito (HTTP, tempo, errore) |
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

### ⚠️ Il regime fiscale degli asset (`fnz_other_assets`)

Due colonne (`20260901180000_...`): **`tax_regime`** dice con quale aliquota, **`cost_basis`** su
quale base. Si compilano dal form dell'asset (💎 Patrimonio), e servono a **un posto solo**: le
**dotazioni** delle 🛡️ Possibili soluzioni, dove il valore di un asset compare al netto. Elenco asset,
Dashboard, snapshot e totali del patrimonio restano al lordo.

| `tax_regime` | Aliquota | Base |
|---|---|---|
| `CAP. GAIN 26%` | 26 % | plusvalenza |
| `AGEVOLATA 12.5%` | 12,5 % | plusvalenza |
| `TFR SEPARATA` | **stima 27 %** | **importo intero** |
| `PIR`, `ESENTE` | 0 % | plusvalenza |
| `ALTRO` | — | nessuna stima |
| NULL | — | nessuna stima |

⚠️ **`TFR SEPARATA` è l'unico che tassa l'importo intero**, ed è il motivo per cui esiste: il TFR
è tassato tutto a tassazione separata, non sulla sola rivalutazione — lì il costo di acquisto non
c'entra e non serve.

⚠️ **Il 27 % è una stima dichiarata, non una costante di legge**: l'aliquota della tassazione
separata è l'aliquota media IRPEF del reddito di riferimento degli ultimi cinque anni, quindi
cambia da persona a persona e sta fra il 23 % e il 30 % per la gran parte dei dipendenti (fonti
consultate il 1° settembre 2026: [centrofiscale.com](https://centrofiscale.com/tassazione-tfr-2026-aliquote-calcolo-netto-esempi/),
[fiscoinvestimenti.it](https://fiscoinvestimenti.it/tassazione-tfr-2026-aliquota-media/) — più
lungo il servizio, più bassa l'aliquota). `TAX_TFR_SEPARATA` la porta in chiaro e la pagina la
scrive accanto al numero: una percentuale che sembra esatta e non lo è sarebbe peggio di una
stima dichiarata. ⚠️ L'imposta sostitutiva del **17 %** sulla rivalutazione annua è un'altra cosa,
già trattenuta anno per anno: non è questa e non si somma.

⚠️ **NULL non vale 26 %, al contrario dei prodotti**: su uno strumento finanziario il 26 % è il
regime ordinario, su una casa o un'auto un default non esiste — e metterlo scriverebbe una tassa
dove non ce n'è nessuna. Regime assente, `ALTRO`, o costo di acquisto mancante su un regime che
tassa la plusvalenza: **nessuna imposta stimata**, il valore resta il lordo e l'etichetta della
dotazione dice *perché* (`assetTax().motivo`). Non si inventa una tassa, e non si tace che manca.

### ⚠️ Il valore netto dei portafogli: si affianca al lordo, non lo sostituisce

`finanza.html` mostra accanto al valore di un portafoglio anche il **valore al netto delle
imposte sulle plusvalenze** — nella Dashboard (riga sotto il totale e colonna *Netto tasse*),
nell'elenco 💼 Portafogli, nel dettaglio di un portafoglio (KPI e le colonne *Tasse* / *Netto* su
ogni posizione) e nel dettaglio di un Dossier.

L'aliquota è il **tag `TASSAZIONE`** di `fnz_products.tags` — lo stesso che si sceglie dal form
del prodotto e che si vede già come colonna:

| Tag | Aliquota |
|---|---|
| `CAP. GAIN 26%` | 26 % |
| `AGEVOLATA 12.5%` | 12,5 % |
| `PIR`, `ESENTE` | 0 % |
| `ALTRO`, o tag assente | **26 %**, e la pagina lo dice |

⚠️ **Un prodotto senza tag si tassa al 26 %, non a zero**: è l'aliquota ordinaria italiana, e uno
0 % silenzioso gonfierebbe il patrimonio proprio dove si sta cercando di essere prudenti. Le
posizioni in guadagno senza tag si contano in un avviso sopra la tabella e portano un ⚠️ nella
cella della tassa: un default che non si vede è un numero sbagliato che sembra vero.

⚠️ **La tassa si calcola posizione per posizione sulla sola plusvalenza, ed è zero dove si è in
perdita**: le minusvalenze **non compensano** le plusvalenze di un'altra posizione. Quella
compensazione passa dallo zainetto fiscale — quando è stata realizzata, entro quanti anni, su
quali strumenti — e nessuno di quei dati sta in questa app: contarla darebbe un numero più bello
e sbagliato. ⚠️ **La liquidità non si tassa**: non è una plusvalenza, sono soldi versati e mai
investiti, quindi entra intera nel netto.

⚠️ **Lo snapshot resta al LORDO, ed è voluto**: `fnz_dashboard_snapshots`, il Patrimonio Netto
della Dashboard, `buildSnapshotPayload`, la Edge Function `save-snapshot` e
`fetchPortfolioLiveValue` in `index.html` non conoscono le tasse. `computeHoldings` e
`portfolioStats` hanno campi in più (`taxRate`, `taxDeclared`, `taxDue`, `netValue`,
`taxUndeclared`) ma i totali che la Edge Function replica — `totalValue`, `totalCost`, `pnl` —
sono **identici a prima**: la tripla copia dello snapshot resta allineata senza toccare niente,
e la serie storica continua a confrontare grandezze omogenee. Portando il netto nello snapshot,
i valori salvati fino a oggi diventerebbero non confrontabili con quelli nuovi.

### ⚠️ Logica dello snapshot duplicata

Il calcolo del patrimonio esiste in **due copie che devono restare allineate**:

| Dove | Perché |
|---|---|
| `finanza.html` — `buildSnapshotPayload`, `portfolioStats`, `computeHoldings`, `computeLoanValue`, `computePricesFromHistory` | Il browser deve calcolare gli stessi numeri per disegnare la dashboard |
| `supabase/functions/save-snapshot/index.ts` — stesse funzioni riscritte in TypeScript | Il job delle 23:00 deve salvare lo snapshot anche se nessuno apre l'app |

**Se cambi una di quelle funzioni in `finanza.html`, cambiala anche nella Edge Function**, altrimenti
lo snapshot notturno diverge da quello che l'app mostra a schermo.

⚠️ **Un'eccezione dichiarata**: `computeLoanValue(loan, aData)` ha un secondo parametro
**facoltativo** — la data a cui calcolare il residuo, che serve alla ⏳ macchina del tempo di
🛡️ Possibili soluzioni. Senza data si resta a oggi, quindi ogni altro chiamante legge
esattamente il numero di prima e la gemella nella Edge Function — che calcola sempre e solo
oggi — **non va toccata**. Le due restano allineate perché il parametro non cambia nessun
risultato di chi non lo passa. La duplicazione è voluta — il
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

## Notifiche — tre canali, e i pulsanti in un posto solo

Il giro è sempre lo stesso e sta tutto attorno a **una riga per canale**:

```
cm_notification_rules  (una riga per user + app + entità + CANALE)
  → fill-notification-queue (cron, ogni 6 h) riempie cm_notification_queue con un fire_at
      → send-notifications (ogni 5 min) consegna le righe 'telegram' e 'android'
      → l'APK Smart Blocker si prende in polling le righe 'smart_block'
```

| `channel` | Dove arriva | Chi la consegna |
|---|---|---|
| `telegram` | Il bot, coi bottoni inline | `send-notifications` |
| `android` | L'APK **nativo** (`com.garsal.appsphere`), come push FCM | `send-notifications` |
| `smart_block` | L'APK Smart Blocker, che blocca lo schermo | l'APK, in polling |

⚠️ **`telegram` e `android` sono la stessa notifica su due strade e arrivano tutt'e due**: due
righe di coda, nate da due regole, consegnate indipendentemente. Il telefono spento non deve far
sparire il promemoria da Telegram, ed è il motivo per cui non c'è nessuna logica del tipo «se il
telefono ha risposto non mandare al bot».

### ⚠️ I pulsanti hanno UNA implementazione, e non sta nel client

✅ Fatto, ⏸ rinvia e ❌ annulla vivevano dentro `telegram-webhook`. Ora stanno in
`notification-action`, che chiamano **sia il bot sia il telefono**: al webhook resta solo quel che
è di Telegram (rispondere al bottone, togliere i messaggi dalla chat). Riscrivere quelle regole in
Kotlin per la notifica Android avrebbe voluto dire due implementazioni di completamento, punti e
archivi — cioè due esiti diversi per lo stesso promemoria il giorno che una delle due cambia. È la
stessa scelta delle RPC del ciclo di vita dei task.

Dentro `notification-action`:

- **complete** — esegue gli insert di `metadata.completion_update` risolvendo i segnaposto
  (`{{fire_date_local}}`, `{{slot_time}}`, `{{monday_of_week}}`, `{{day_of_week_n}}`), poi chiama
  `task_complete` o `habit_post_completion`. ⚠️ I segnaposto si ricavano dal **`fire_at` in ora di
  Roma** e non dall'istante del clic: un promemoria delle 23:30 chiuso dopo mezzanotte verrebbe
  segnato sul giorno dopo;
- **snooze** — chiude l'occorrenza e ne inserisce una copia più avanti. ⚠️ Una riga **nuova** e non
  un update del `fire_at`: la vecchia resta a dire che quel promemoria è suonato davvero. E la
  copia si fa **per ogni canale** su cui era arrivato, o rimandarlo dal telefono spegnerebbe di
  nascosto Telegram;
- **cancel** — chiude l'occorrenza e basta;
- chi chiama: il **service role** (il webhook) passa senza controlli, un **utente col suo JWT**
  (l'APK) solo sulle proprie righe. Dentro si scrive col service role, dove la RLS non vale:
  senza quel controllo basterebbe l'id di una riga altrui per completarla.

### ⚠️ La stessa occorrenza su due canali ha due `occurrence_id`

`occurrence_id` è `"{rule_id}:{YYYY-MM-DD}:{HH:MM}"` e porta dentro il **rule_id** — ma ogni canale
ha la **sua** regola, quindi la riga Telegram e quella Android dello stesso promemoria hanno due
occurrence_id diversi.

Cercando i fratelli per occurrence_id uguale — com'era finché il canale era uno solo — premere
✅ Fatto sul telefono avrebbe chiuso il task lasciando la riga Telegram in `pending`: il bot avrebbe
suonato per una cosa già fatta, e premendo Fatto anche lì `task_complete` avrebbe chiuso
**l'occorrenza successiva**, cioè la volta dopo, senza che niente lo dicesse. `notification-action`
riconosce quindi la stessa occorrenza da **utente + app + entità + la coda `:giorno:ora`**, che è
la parte che non dipende dalla regola.

### ⚠️ Due canali vogliono dire due righe: chi legge con `maybeSingle()` si rompe

Le pagine che scrivono le regole ne scrivono ora **due**, e ogni lettura che si aspettava una riga
sola va filtrata per canale — `maybeSingle()` su due righe non torna la prima: risponde con un
**errore**, cioè un promemoria che smette di salvarsi senza dire perché. Sistemato in:

| Dove | Cosa |
|---|---|
| `tasks.html` | `scriviRegola(canale)` / `togliRegola(canale)`, un blocco per canale coi propri preset |
| `habit-tracker.html` | `syncHabitNotificationRule` scrive i due canali; le tre letture che salvano la regola prima di ricreare uno stack leggono ora **tutte** le righe, e `migrateHabitNotificationRule` le migra tutte |
| `index.html` | Il promemoria al volo nasce già in coda: una regola **e** una riga di coda per canale. L'elenco raggruppa per `entity_id` (o comparirebbe due volte) e il 🗑 cancella per entità |

### ⚠️ Il telefono è un canale che si sceglie da sé, non un'aggiunta a Telegram

I due canali sono **indipendenti in tutt'e tre le pagine**: si accendono uno per uno, e spegnere
il bot non zittisce il telefono. Dove ciascun canale ha **anticipi propri da scegliere** la
separazione è una **Tab** (Tasks, Abituati); dove il promemoria è un istante solo sono **due
caselle** (il promemoria al volo di AppSphere) — una Tab che contenesse una spunta e basta
sarebbe un clic in più per vedere una spunta.

| Pagina | Come | Cosa ha di suo il telefono |
|---|---|---|
| `tasks.html` v19.25.0 | tre Tab: 📱 Telegram · 📲 Telefono · 🔐 Smart Block | il suo elenco di anticipi (`androidReminders`) |
| `habit-tracker.html` v9.5.0 | due Tab dentro *Promemoria*: 📱 Telegram · 📲 Telefono | il suo elenco di anticipi (`addHabitAndroidIds` / `editHabitAndroidIds`) |
| `index.html` v1.6.1 | due caselle: 📱 Su Telegram · 📲 Sul telefono | niente: un promemoria al volo ha un `fire_at` e basta |

⚠️ **In tutt'e tre la riga BASTA a dire che il canale è acceso**: `reminder_presets.android`
non si scrive più da nessuna parte. Era nato perché l'assenza della regola android valeva anche
per una riga nata prima del canale; ma da quando il telefono si accende da sé quella riga la si
è scelta, e un campo che ripete quel che la riga già dice sono due verità sullo stesso dato. Le
righe salvate prima ce l'hanno già, il telefono acceso: si rileggono così, ed è quello che erano.

⚠️ **In Abituati la regola `android` porta SEMPRE `completion_update`**, anche col bottone Fatto
di Telegram spento: sul telefono i pulsanti li disegna l'app e ci sono comunque, e senza quel
blocco un ✅ Fatto chiamerebbe `habit_post_completion` **senza scrivere la riga in
`hb_completions`** — cioè un'abitudine segnata che non risulta fatta. `telegram_complete_button`
resta invece solo sulla riga Telegram, che è l'unica a cui quel bottone appartenga.

⚠️ **In Abituati l'orario è dell'abitudine e non del canale**: `times`, `days` e `from-to` sono
gli stessi nelle due righe, a cambiare sono i soli anticipi. Per questo la cache in pagina è
`{ telegram, android }` per abitudine (`regolaCanale` / `regolaQualsiasi`): piatta terrebbe
quelli dell'ultima riga letta, e il form riaprirebbe a caso gli anticipi dell'uno o dell'altro.

#### In Tasks: tre Tab

In `tasks.html` i canali sono **tre Tab**: 📱 Telegram, 📲 Telefono, 🔐 Smart Block.
Il telefono ha una spunta sua e un **proprio elenco di orari** (`androidReminders`), che si
riempie dalle stesse pillole ma è un elenco diverso: la regola `android` porta i **suoi**
`reminder_presets.reminders`, non quelli di Telegram.

⚠️ **La ragione è che i due canali si scelgono uno per uno.** Fino alla v19.24.0 il telefono era
una spunta *dentro* la Tab Telegram e ne usava i preset: chi non voleva il bot non poteva avere la
push, e spegnere Telegram zittiva anche il telefono senza che niente lo dicesse. Erano due strade
per la stessa notifica trattate come una sola.

⚠️ **Adesso la riga basta a dire che il canale è acceso**: `androidEnabled = !!anRule`, e
`reminder_presets.android` sulla regola Telegram **non si scrive più**. I task salvati prima ce
l'hanno già, quella riga, coi preset di Telegram — si rileggono come suoi, ed è quello che erano
davvero, quindi nessuna migrazione.

⚠️ **`loadNotificationRulesMap` legge l'UNIONE dei due canali** (`.in('channel', […])`): il badge
🔔 sulla scheda dice «questo task un promemoria ce l'ha», e da quando il telefono si accende da sé
un task può averlo **solo lì** — guardando il solo Telegram sparirebbe, insieme alla sua riga nella
pagina Reminder. `telegram_complete_button` si legge invece dalla sola riga Telegram, che è l'unica
a cui quel pulsante appartenga.

⚠️ **La barra delle Tab scorre di lato e non va a capo**: coi caratteri di sistema grandi tre voci
su 360 px non ci stanno, e schiacciarle le renderebbe non toccabili. È la stessa regola delle righe
di pulsanti in nativo.

⚠️ **Nel promemoria al volo di AppSphere almeno un canale ci vuole**: spente tutt'e due, il
salvataggio si ferma e lo dice — una notifica che non arriva da nessuna parte non è un
promemoria. E le due caselle **ripartono accese a ogni apertura del form**: una lasciata spenta
dalla volta prima spegnerebbe il promemoria dopo senza che nessuno l'abbia deciso.

### Le push: FCM HTTP v1, e i due file che nascono fuori dalla repo

`send-notifications` manda alle righe `android` una push per ogni telefono in `cm_push_devices`.

⚠️ **API HTTP v1, non la vecchia server key**: quella (`key=AAAA…`) è spenta dal 2024. Serve un
access token OAuth2 firmato con l'account di servizio — JWT RS256 con Web Crypto, poi scambiato su
`oauth2.googleapis.com` — e il token si tiene in memoria per un'ora: rifarlo a ogni notifica
sarebbe una firma RSA per ogni riga della coda.

⚠️ **Messaggio di soli `data`, mai una `notification`**: una notification payload di FCM **non può
portare pulsanti**, e senza ✅ Fatto e ⏸ Rinvia il telefono direbbe meno di Telegram. La notifica la
disegna l'app (`notifiche/Notifiche.kt`), che è anche il motivo per cui il servizio viene svegliato
pure ad app chiusa. `priority: high`, perché un promemoria in ritardo di venti minuti non serve più.

⚠️ **Basta un telefono raggiunto perché la notifica sia consegnata**: il tablet spento non deve far
risultare fallito un promemoria arrivato sul telefono in tasca. Un token che FCM dichiara
`UNREGISTERED` spegne la sua riga (`enabled = false`): una riga morta lasciata accesa fa fallire
ogni invio successivo e non lo dice a nessuno.

**Due cose nascono sulla console Firebase e non possono stare qui:**

| Cosa | Dove va | Senza |
|---|---|---|
| `google-services.json` (app Android, package `com.garsal.appsphere`) | `android-app/appsphere-native/app/` | L'APK **si compila lo stesso** e le push restano spente |
| Chiave dell'account di servizio (JSON intero) | secret Supabase `FCM_SERVICE_ACCOUNT` | Le righe `android` finiscono `failed`; Telegram continua a funzionare |

⚠️ **Il plugin `com.google.gms.google-services` si applica SOLO se il file c'è**
(`if (schedaFirebase.exists())` in `app/build.gradle`): applicandolo comunque, la build fallirebbe
con «File google-services.json is missing» — cioè un APK che non si compila più per una
funzionalità che non è ancora accesa. `Push.disponibile()` risponde `false` finché
`FirebaseApp` non è inizializzata, e ogni chiamata a Firebase sta in un `runCatching`.

### La notifica sul telefono (`appsphere-native/…/notifiche/`)

| File | Cosa fa |
|---|---|
| `Push.kt` | Il token di questa installazione e la chiamata a `notification-action` |
| `PushService.kt` | Riceve la push (soli dati) e il token rigenerato |
| `Notifiche.kt` | Canale, disegno e pulsanti |
| `AzioniNotifica.kt` | Il pulsante premuto: chiude la notifica e chiama la Edge Function |
| `RinvioActivity.kt` | Le altre scelte: 30 min / 1 h / 3 ore / domani / annulla |

⚠️ **Android mostra tre pulsanti e basta.** Sulla notifica ci sono ✅ Fatto, ⏸ 1 ora e ⋯ Altro:
mettendo lì tutte e quattro le durate si perderebbe il Fatto, che è quello che si preme davvero.
Le altre stanno nel dialogo di `RinvioActivity` — le stesse quattro del bot, più l'annullamento.

⚠️ **La notifica si chiude PRIMA della risposta del server**: il tocco deve avere un effetto
immediato, o si preme una seconda volta credendo che non sia passato — e due «Fatto» sullo stesso
promemoria sono due chiusure. Se il server rifiuta, il messaggio lo dice.

⚠️ **Ogni pulsante ha un `action` diverso nel suo Intent** (`"$azione:$queueId:$minuti"`): due
`PendingIntent` con lo stesso requestCode e intent che differiscono per i soli **extra** sono lo
stesso PendingIntent, e con `FLAG_UPDATE_CURRENT` il primo si prenderebbe gli extra del secondo —
cioè «Fatto» che rimanda di un'ora.

⚠️ **Il token si riscrive a ogni avvio, in upsert sul token, anche col permesso negato**: FCM lo
rigenera da sé (reinstallazione, ripristino da backup, dati svuotati), e una riga vecchia in
tabella è una notifica che parte e non arriva. Il permesso `POST_NOTIFICATIONS` si chiede **dopo lo
sblocco biometrico**: due finestre di sistema insieme se ne annullano una, e quale dipende dal
telefono.

⚠️ **L'icona piccola dev'essere monocromatica** (`ic_notifica.xml`): Android ne tiene solo il canale
alfa, e il marchio a cinque cerchi diventerebbe una macchia bianca senza forma.

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

## SOS — il bottone rosso e il countdown che blocca il telefono

`android-app/sos/` è un modulo del progetto Gradle di `android-app/` (come `smartblocker`, non
come i progetti standalone), `applicationId` `com.garsal.sos`, APK
`releases/Sos-latest.apk`. **È nato da Smart Blocker** e ne riusa il pezzo che conta: la
finestra `TYPE_APPLICATION_OVERLAY` tenuta viva da un servizio in primo piano.

Una schermata sola: un SOS per pagina, si sfoglia con lo **swipe destra/sinistra**
(`ViewPager2`), e in ogni pagina c'è un cerchio rosso col diametro ricavato dallo schermo
(72 % della larghezza, con un tetto) e la scritta in **autosize** — così l'ingrandimento dei
caratteri di sistema non la fa uscire dal cerchio. Si preme, parte il countdown, il telefono
resta bloccato, e alla fine arriva la domanda «com'è andata?».

Le cose che *sono* la funzionalità, e che vanno cambiate insieme al database:

- **il blocco è l'overlay, non un'Activity.** `SosOverlay` è una finestra di sistema: sta sopra
  qualsiasi app, launcher compreso, non se ne va col tasto Home (non è nello stack) e i tocchi si
  fermano lì. Non c'è nessun servizio di accessibilità come in Smart Blocker, e non serve: là
  rilanciava un'*Activity* che l'utente poteva scavalcare, qui non c'è niente da rilanciare;
- **il countdown vive nel servizio** (`SosSessionService`, foreground `specialUse`), non
  nell'Activity. Uno schermo che si spegne o una rotazione non devono poter accorciare un blocco;
- **una via d'uscita c'è, e costa.** *Tieni premuto per arrenderti* (3 secondi, conferma al
  rilascio) interrompe il giro — che finisce comunque in archivio, con `completed = false`, e la
  domanda si fa lo stesso. Un blocco senza uscita, su un telefono che è anche il modo per
  chiamare qualcuno, sarebbe un rischio e non una funzionalità;
- **il bottone parte anche senza rete.** La configurazione sta in cache (`Prefs`), il countdown
  parte sulla durata che il telefono conosce e `sos_session_start` va in parallelo: la durata del
  server si adotta solo nei primi quattro secondi, dopo di che allungare quel che si sta già
  guardando sembrerebbe un difetto. Il giro chiuso senza rete finisce in **coda** e si rispedisce
  con `sos_session_log` alla prossima apertura;
- ⚠️ **in coda va solo ciò che può ancora riuscire.** Un errore di merito — codice revocato,
  risposta cancellata dalla configurazione — fallirebbe identico ad ogni rinvio e resterebbe lì
  per sempre: si riprova solo quando il server non ha risposto affatto;
- **punti e durata del prossimo giro non si calcolano in Kotlin.** Li dà
  `sos_session_finish`, e quello che l'overlay mostra è la sua risposta.

Il **testo motivante scorre su una riga sola** invece di andare a capo: con i caratteri di
sistema grandi un paragrafo spingerebbe fuori schermo proprio il countdown. La larghezza del
`TextView` si **misura** (`paint.measureText`) invece di lasciarla a `WRAP_CONTENT`, che dentro
un contenitore largo quanto lo schermo si ferma al bordo e manda il testo a capo — cioè proprio
quello che non deve fare.

⚠️ **L'APK non fa il login Google.** Si accoppia una volta col codice generato da
`sos.html` → 📱 Telefoni e da lì in poi parla con quattro RPC che lo riconoscono da quel codice.
La ragione è la stessa per cui il bottone è grande: si preme in un momento di crisi, e in quel
momento non si può inciampare in una sessione scaduta o in una schermata di Google che chiede di
riautenticarsi.

---

## AppSphere nativa — l'unico modulo Android che non è un WebView

`android-app/appsphere-native/` è un progetto Gradle standalone (come `situazione-rosa/` e
`pressure-tracker/`) e **l'unica app Android della repo scritta davvero in nativo**: schermate in
Kotlin/Compose, dati da PostgREST via `supabase-kt`. Tutti gli altri moduli caricano una pagina
Netlify dentro una `WebView`.

**Non sostituisce l'APK WebView, gli si affianca.** `applicationId` è `com.garsal.appsphere`
(contro `com.garsalapps`), quindi i due si installano insieme sullo stesso telefono e leggono lo
stesso database. Per tutto ciò che non è ancora nativo si continua ad aprire quello WebView.

⚠️ **I due APK si chiamano diversamente sul telefono**: il nativo è **AppSphere**, il WebView è
**AppSfera Web** (`app_name` in `strings.xml`; si chiamava *GarsalApps*). ⚠️ Il nome che si legge
nel cassetto delle app è l'unica cosa che è cambiata: `applicationId` resta `com.garsalapps`, e
non si tocca — cambiarlo farebbe di quell'APK **un'app diversa**, che si installerebbe accanto
alla vecchia invece di aggiornarla, lasciando indietro dati e permessi. Per la stessa ragione
restano `releases/GarsalApps-latest.apk` e `.json`: gli APK già installati interrogano
**quei** percorsi per sapere se c'è un aggiornamento, e rinominarli li lascerebbe su un 404 per
sempre. Segue `app_name` anche il messaggio di `HealthConnectBridge`, che dice sotto quale nome
cercare l'app in Health Connect — se restasse indietro manderebbe a cercare un nome che lì non
esiste.

⚠️ **Le due icone portano lo stesso disegno e si distinguono per il fondo**: i cinque cerchi di
AppSphere — quattro che si toccano a due a due (arancio, rosso / verde, viola) e il blu al centro,
sopra a tutti — su **fondo bianco per la WebView** e **nero per la nativa**. È l'unica cosa che le
distingue in un cassetto delle app, dove il nome è lo stesso e sta scritto piccolo. Il disegno vive
in `drawable/ic_launcher_foreground.xml`, **identico nei due progetti**, e il fondo in
`values/colors.xml` (`ic_launcher_background`): cambiando il disegno va copiato in tutt'e due, o le
due app smettono di sembrare la stessa app.

⚠️ **Il raggio dei cerchi non è scelto a occhio.** Un'icona adattiva è una tela da 108 dp di cui il
launcher garantisce solo il **cerchio centrale da 36 dp di raggio**: col disegno a `r = 14` dp
l'angolo più esterno cade a `r·(1+√2) = 33,8` dp dal centro, dentro quel cerchio. Allargandolo per
riempire di più la maschera quadrata, la maschera **tonda** taglierebbe i quattro cerchi esterni —
e non si vedrebbe finché non lo si prova su un launcher che la usa.

| Cosa | Dove |
|---|---|
| Home a bolle, avvisi, riquadro del totale, login, biometria | `home/`, `MainActivity.kt`, `core/` |
| Catalogo premi (riscossione, gestione, cronologia) | `premi/` |
| Notifiche push dei promemoria (canale `android`) | `notifiche/` — vedi *Notifiche, tre canali* |
| App portate | `spuntiamola/`, `eventslog/`, `tasks/`, `tafiri/`, `peso/`, `memo/`, `abituati/`, `calorie/`, `obiettivi/` (la bolla apre il 📆 **Piano quotidiano**) |

### ⚠️ Righe di pulsanti e liste di scelta: due componenti condivisi, non uno per schermata

Due pattern per i caratteri di sistema grandi, nati in `tasks/Panoramica.kt` e `tasks/Gestione.kt`
e poi duplicati a mano schermata per schermata, ora vivono **una sola volta** in
`core/PulsantiTendine.kt` e si importano invece di reinventarli:

- **`RigaScorrevole` + `larghezzaPulsanti(testi)` + `Pillola`** — una riga di pulsanti-azione
  (Salva/Elimina, Fatto/Fallito, Modifica/Cancella…) **non va mai a capo**: scorre col dito su una
  riga sola, e i pulsanti sono **larghi uguale**, misurati con `larghezzaPulsanti()` sullo stile e
  l'ingrandimento correnti — mai una costante in `dp`. `larghezzaPulsanti` vuole **tutte** le
  etichette che possono comparire in quella riga, non solo quelle mostrate in un dato momento,
  altrimenti un pulsante condizionale farebbe traballare la larghezza degli altri. Dentro una
  `RigaScorrevole` (che scorre, quindi ha larghezza indefinita) **non si può usare `weight(1f)`**
  per pareggiare le larghezze — va bene solo in un `Row` normale, non scorrevole — serve sempre la
  larghezza misurata.
- **`Tendina` / `TendinaFacoltativa`** — una scelta singola fra poche opzioni fisse (tipo, stato,
  priorità, categoria…) è **sempre una tendina**, mai una fila di pillole che va a capo o si
  accorcia: un campo chiuso col valore scelto, che si apre in un `DropdownMenu` al tocco.
  `TendinaFacoltativa` è la stessa cosa con una voce «tutte/nessuna» in cima, per i filtri e le
  scelte facoltative. `Tendina` accetta anche `abilitata = false` per i campi che esistono ma non
  si possono cambiare (es. il tipo di un task o di un obiettivo già esistente): mostra il valore
  attuale fisso, invece di sparire o restare comunque cliccabile.

Quello che **resta** una `FlowRow` che va a capo, di proposito: le **liste a scelta multipla**
(giorni della settimana/mese, categorie di un task) — dove più valori possono essere scelti
insieme, quindi non c'entra una tendina — passano comunque da `RigaScorrevole` con la larghezza
misurata (restano selezionabili in multipla, semplicemente scorrono anziché andare a capo); le
**barre di icone** (formattazione testo di Memo, tavolozza colori) restano `FlowRow`, perché sono
toolbar e non scelte da ricordare; e la scelta di un'opzione `SCELTA` in un diario di Memo resta
volutamente pillole invece di tendina — è un'eccezione documentata in `MemoRegistrazione.kt`,
perché lì una tendina taglierebbe l'etichetta proprio dove la scelta si fa.

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

⚠️ **Lo stesso giro esiste ora anche nell'APK WebView**, ed è voluto che siano due copie parallele:
`build-android.yml` pubblica `releases/GarsalApps-latest.json` con le stesse sette chiavi, e
`android-app/app/.../Aggiornamento.kt` la legge da **☰ → 📱 Versione app** nel launcher. Le
differenze sono solo di forma — lì Compose e `BuildConfig`, qui un `AlertDialog` di AppCompat e la
versione letta dal **pacchetto installato** (`packageManager.getPackageInfo`), che è quella vera e
non quella che il codice credeva di essere. La voce di menù compare **solo dentro l'APK**: il
launcher la mostra se `window.AndroidBridge.checkUpdate` esiste, quindi resta nascosta sul PC e
negli APK precedenti a questo ponte. Cambiando la forma della scheda in un workflow, cambiala
anche nell'altro e in `mostraVersione()` di `comandi.html`, che ora disegna tutt'e due i pulsanti.

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

### ⚠️ «Ti pisasti?» nativo: pesata, obiettivi e bilancia sì, il resto no

`peso/` porta in nativo **le cose che si fanno col telefono in mano**:
segnare la pesata (il pulsante ⚖️, che scrive in `ps_weight_tracking`), la **Tabella**
giorno per giorno, il **Grafico** dell'andamento, da **✏️ Gestisci**
(`GestioneObiettivoScreen`) creare/modificare/chiudere/cancellare un obiettivo — lo stesso form di
*🎯 Gestione Obiettivo* nel web, campo per campo: nome, tipo (perdere/mantenere), punti
bonus/malus giornalieri e finali, milestone progressive (o le tre voci di «mantenere»: data
inizio, settimane, peso) — e da **⚖️ Salute** aprire la bilancia Renpho e sincronizzare le pesate
lette da Health Connect (`Salute.kt`). La scheda *Oggi* apre con la domanda che dà il nome
all'app — *oggi ti sei pesato?* — e sotto i **sei riquadri della pagina**, nello stesso ordine:
Minimo oggi, Target oggi, Mancano al target, Kg alla fine, Punteggio, Punti oggi.

**Il FAB è un solo pulsante per le due strade della pesata**: il tocco apre un `DropdownMenu`
ancorato al FAB stesso con «➕ Inserisci a mano» (il dialogo di sempre) e «🔄 Sincronizza
(Renpho)» — non due pulsanti separati in barra, che coi caratteri di sistema grandi non
avrebbero spazio garantito.

**«🔄 Sincronizza (Renpho)»** ricalca `openRenpho()` + `onAndroidResume()` + `syncFitNative()` +
`processWeights()` del web, ma **senza passare da un `JavascriptInterface`**: l'app nativa chiama
l'SDK Health Connect direttamente da una coroutine, quindi niente `ExecutorService` con timeout
da 25 s — quella complicazione nel bridge Kotlin dell'APK WebView esiste solo perché lì la
chiamata parte da un thread JS sincrono. La voce apre Renpho (`com.renpho.health`, o lo Store
se non è installata) e arma un flag locale; al **ritorno in primo piano** (`DisposableEffect` +
`Lifecycle.Event.ON_RESUME`) — e solo se il flag è armato, altrimenti ogni ritorno in foreground
per qualunque motivo lancerebbe una sync — legge Health Connect (90 giorni + 25 ore di margine,
stessa finestra del web) e scrive: **peso troncato a un decimale** (`Math.floor`, non
arrotondato), target interpolato sull'obiettivo che si sta guardando, e le pesate **manuali**
degli stessi giorni tolte prima di scrivere quelle vere — altrimenti resterebbero a fare da
doppione. Il permesso si chiede col contratto ufficiale `PermissionController` (l'unico modo per
cui Health Connect sblocchi le letture successive, come nel web) e, quando manca, la
sincronizzazione si rilancia da sola dopo la concessione.

#### ⚠️ Health Connect legge solo 30 giorni indietro, se non glielo si chiede

Senza il permesso `android.permission.health.READ_HEALTH_DATA_HISTORY`, Health Connect lascia
leggere **soltanto i dati scritti nei 30 giorni prima della concessione**. La finestra di 90
giorni che le due app chiedono torna quindi tagliata **senza nessun errore**: la
sincronizzazione sembra funzionare benissimo, su un terzo dei dati.

È esattamente quel che si vedeva il 24 agosto 2026 — la stessa bilancia, lo stesso telefono,
lo stesso istante: **290 pesate** dall'APK WebView (permesso concesso mesi prima, quindi con
tutto lo storico da lì in poi) e **37** dal nativo, che il permesso l'aveva avuto da poco. Il
codice delle due letture era identico, e infatti non era lì. Il permesso è ora dichiarato e
richiesto in tutt'e due (`PERMESSI_SALUTE` in `Salute.kt`, `HC_PERMISSIONS` in `MainActivity.kt`
dell'APK WebView); su un dispositivo dove non esiste resta semplicemente non concesso.

Corollario: **il conteggio da solo non dice niente**. La sincronizzazione nativa mostra ora lo
stesso riepilogo del `showSyncResult()` del web — quante pesate, da quando a quando, l'ultima —
perché la domanda «è la finestra che mi aspetto?» abbia una risposta a schermo. Ed entrambe
seguono ora il `pageToken` di `readRecords`, che ne restituisce al massimo mille per volta:
fermarsi alla prima pagina non darebbe un errore, darebbe qualche pesata in meno.

La **barra di stelline** con `⭐ Punti Totali Traguardi Intermedi` e il **gratta e vinci** ci sono
(`BarraTraguardi` in `GestioneObiettivo.kt`, `DialogoGrattaEVinci` in `PesoGrattaEVinci.kt`), e
dall'agosto 2026 **premi e punti sono gli stessi del web**: stanno in `ps_milestone_prizes` /
`ps_milestone_points` (vedi lo schema `ps_`), non più nelle preferenze del telefono. Un premio
grattato qui si ritrova sul PC già scoperto, e dopo il gratta si può dire **«Mangiato !!!»** —
il premio resta vinto ma si spegne (emoji sbiadita col ✓), ed è reversibile perché un tocco per
sbaglio non deve costare un cannolo. Le vecchie righe rimaste nelle preferenze salgono sul DB da
sé al primo avvio (`PesoPremi.premi`), altrimenti l'aggiornamento avrebbe fatto sparire premi già
vinti. **Restano su `weight-quest.html`**: le statistiche e *genera dieta*.

Le due regole di chiusura di `closeObjective('success')` sono ricalcate in
`PesoViewModel.preparaChiusura()`, non riscritte a occhio — se cambia una delle due condizioni nel
web, va cambiata anche là:

- «perdere» — il **minimo di oggi** (nessun ripiego sull'ultima pesata nota, a differenza del
  riquadro *Minimo oggi*) deve stare sotto il peso finale, o la chiusura è bloccata;
- «mantenere» — il **massimo del periodo** `[start_date, end_date]` deve stare sotto il peso
  stabilito.

Il punteggio di chiusura (`+final_bonus` sul successo, `-final_malus` sul fallimento, sommato ai
punti giornalieri già maturati) si mostra **prima di confermare**, come il `confirm()` del web.
Elimina non ha questo blocco — un obiettivo chiuso si cancella lo stesso.

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
restano le curve che si guardano davvero — il peso e il target — perché un grafico fitto di
etichette su uno schermo di telefono è illeggibile prima ancora di essere utile. La spezzata del
target è quella dei **traguardi presi diretti**, non i valori interpolati giorno per giorno che
restano nella tabella: fra un traguardo e l'altro serve la retta vera, non tanti segmenti
arrotondati a due decimali che sembrano seghettati.

⚠️ **Del peso si disegnano minimo e massimo della giornata, non il solo minimo.** Il minimo è la
pesata del mattino — quella che fa punti, e per questo è la linea piena — mentre il massimo è una
linea più sottile e sbiadita; la **fascia fra le due** è quanto il peso è ballato quel giorno, che
con la sola linea del minimo non si vedeva affatto. ⚠️ Un giorno **ricostruito** un massimo non ce
l'ha (`RigaGiorno.massimo` è `null`): lì il massimo ripiega sul minimo e la fascia si chiude su sé
stessa — inventarne una vorrebbe dire disegnare un'oscillazione che nessuno ha misurato, ed è la
stessa regola del pallino che sui giorni ricostruiti non si disegna.

⚠️ **Il colore dice se si sta dentro il piano: sotto o pari al target è verde, sopra è rosso** —
fascia, linee e pallini insieme. Non è una decorazione, è la **stessa** domanda che decide i punti
della giornata (`PesoRegole.punti`, confronto a un decimale), quindi una giornata verde nel grafico
è una giornata guadagnata nella tabella. Il taglio si fa **ritagliando sulla spezzata del target
già disegnata** (`clipPath` sulle due regioni sopra e sotto di lei) e non spezzando le curve a
mano: il confine cade così esattamente sulla linea che l'occhio confronta, e una giornata a cavallo
del target viene per metà verde e per metà rossa — il minimo dentro il piano e il massimo fuori è
il caso normale, non un difetto. ⚠️ Senza curva di traguardi (meno di due) non c'è nessun target da
superare e il peso resta del suo colore neutro: un verde o un rosso lì sarebbe un giudizio
inventato. ⚠️ Il verde del peso (`VerdePeso`, `#00967A`) è **un passo più scuro** di quello della
spezzata del target: le due linee si incrociano di continuo, e con lo stesso identico verde nel
punto in cui si toccano non si distinguerebbe più quale si sta guardando.

⚠️ **Il grafico copre l'intero periodo dell'obiettivo, futuro compreso, ed è scorrevole.** La
curva del peso comincia dal giorno di inizio (non dal mese di respiro che il caricamento tiene
prima per la prima interpolazione) e si ferma all'ultima pesata — il futuro non si disegna, solo
il piano lo promette. La spezzata del target invece arriva fino alla **fine** dell'obiettivo anche
se quel giorno non è ancora passato: è l'unico modo per vedere a colpo d'occhio quanto manca al
traguardo finale, non solo quanto già fatto. Il `Canvas` del disegno è largo quanto l'intero
periodo (16.dp per giorno) dentro un `Box` con `horizontalScroll`, mentre la colonna delle
etichette in kg a sinistra resta fissa e non scorre — altrimenti scorrendo si perderebbe subito il
riferimento dell'altezza della curva. Il passo è quello **di partenza**: si stringe e si allarga
col **pizzico** (`pinch`, due dita), fra la scala che fa stare tutto il periodo in una schermata e
un massimo di 48 dp al giorno. ⚠️ Il gesto è scritto a mano e intercettato nel passaggio
**`Initial`**: `detectTransformGestures` e `transformable` prendono anche il trascinamento a un
dito — cioè si mangerebbero lo scorrimento — e `Main` arriverebbe prima allo scorrimento, che
tratterebbe il pizzico come un trascinamento. Il giorno al centro si legge prima di cambiare scala
e si ritorna lì dopo, o stringendo le dita il grafico scivolerebbe via da sé; il passo delle date
segue lo zoom (settimana, due settimane, quattro) e i pallini spariscono sotto gli 8 dp al giorno,
dove si impasterebbero. In **altezza** prende tutto quello che avanza
(`weight(1f)`), meno il posto del FAB in basso: sotto il disegno non c'è più niente scritto — le
due righe che spiegavano come leggerlo si prendevano un terzo dello schermo per dire quel che si
vede, e legenda e minimo del periodo stanno ora in una riga sola **sopra**.

⚠️ **Le misure del disegno sono in `dp`, mai in pixel grezzi.** Spessori, margini e la fascia
sotto l'asse erano numeri in px (`4f`, `34f`): su uno schermo denso valgono un terzo di quello che
sembrano, e le date sotto l'asse venivano **tagliate a metà** — la fascia era più bassa del testo
che ci andava dentro. Un pallino segna ogni pesata **vera** e non i giorni ricostruiti (che la
linea attraversa lo stesso): un pallino su un giorno interpolato sembrerebbe una misura che non
c'è mai stata. All'apertura la vista si centra da sé su **oggi** (marcato
da una riga tratteggiata verticale con l'etichetta «Oggi»), con circa due settimane prima e dopo
in vista: lo scorrimento iniziale si calcola con un `LaunchedEffect` sulla larghezza reale del
riquadro (`onSizeChanged`), disponibile solo dopo il primo posizionamento — prima di allora il
riquadro è 0×0 e non c'è niente da centrare.

`ps_weight_tracking` e `ps_objectives` non stanno in nessuna migration e si leggono come
`JsonObject`, non come `data class` serializzate: è la stessa scelta di `ts_tasks` e per la stessa
ragione — `id` può essere un numero o un uuid e `weight` un intero o un decimale, e con una data
class una colonna del tipo inatteso non darebbe un campo storto ma la schermata vuota.

### ⚠️ Memo nativo: il contenuto è HTML, e il giro deve chiudersi

`memo/` porta in nativo le schede di `memo.html` — **note, liste e diari**: le **quattro Tab** del
web (📄 Note, ☑️ Liste, 📊 Diari, 📌 Fissa), ricerca su titolo e testo, filtro per categoria,
ordinamento (ultime modificate / ultime create / titolo), il **dettaglio in lettura** e la
**modifica** con foto e OCR. Le tabelle sono le stesse (`mm_cards`, `mm_card_categories`,
`mm_images`, `mm_list_items`, `mm_diary_metrics`, `mm_diary_entries`) più le categorie condivise
`cm_categories`, e si leggono come `JsonObject`: non stanno in nessuna migration — nascono dal SQL
che la pagina mostra in Impostazioni — quindi vale la stessa scelta di `ts_tasks`.

Come sul web, **una nota si apre in modifica e una lista o un diario no**: hanno una vista propria
(`MemoListaView`, `MemoDiarioView`), perché la cosa che si fa più spesso su di loro — spuntare una
voce, aggiungere una registrazione — non è modificare la scheda. All'editor si arriva da lì col ✏️,
e il **+ crea una scheda del tipo della Tab** invece di chiedere quale. **📌 Fissa è l'unica Tab che
attraversa i tre tipi**, ed è la ragione per cui il segno del tipo resta sulla scheda anche ora che
ogni Tab ne mostra uno solo.

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

#### ⚠️ `riservato`: il filtro sta nella query, non nel disegno

La spunta 🙈 **Riservato** (`mm_cards.riservato`) si vede **solo nella definizione di un diario**,
come sul web: su una nota o una lista il campo non c'è e il salvataggio manda `false`. La colonna è
però su `mm_cards` e il filtro vale per tutti i tipi, quindi estenderla è una riga di Compose.

⚠️ Fuori dalla modalità nascosta **la riga non viene proprio letta**: il filtro è nella `select` di
`MemoRepository.schede()`, non nel rendering. Nasconderla solo a schermo la lascerebbe in chiaro a
chiunque guardi il traffico o la memoria dell'app, che è esattamente ciò da cui la modalità
protegge. Gli stessi tre stati del web: spenta si vedono solo le schede normali, accesa si vede
tutto, accesa col 👁 si vedono **solo** le riservate — e il pulsante 🙈/👁 compare solo a modalità
già accesa, perché a modalità spenta non c'è niente da alzare.

Una differenza dal web, e vale la pena saperla: là il ramo dei NULL (`riservato.is.null`) è scritto
per prudenza, qui c'è la sola uguaglianza. La colonna nasce `NOT NULL DEFAULT false`, quindi una
riga senza valore non può esistere e i due filtri dicono la stessa cosa.

#### ⚠️ Liste e diari: le stesse tre regole di qua

Le tre cose che **sono** la funzionalità, e che vanno cambiate nelle due implementazioni insieme:

- **le spunte di una lista sono ottimistiche con rollback** (`MemoViewModel.spuntaVoce`, come
  `toggleViewItem()` e come Spuntiamola): la voce cambia subito e torna indietro se il database
  rifiuta, così non resta a schermo una spunta finta che sparisce al ricarico;
- **le misure si aggiornano riga per riga e non si cancellano per ricrearle**
  (`MemoRepository.salvaMisure`, come `syncDiaryMetrics()`): le registrazioni le citano per id
  dentro `measures`, e ricreandole tutto lo storico resterebbe senza nome. Per la stessa ragione
  **le opzioni di una combo tengono il loro id** mentre se ne cambia l'etichetta, e la
  registrazione archivia l'**id**, mai l'etichetta;
- ⚠️ **una misura non toccata non finisce in `measures`**, e non ci finisce come zero: «non l'ho
  misurata» e «vale zero» sono due cose diverse, e uno slider lasciato a metà scriverebbe la
  seconda al posto della prima. Una misura non registrata mostra `—`, e *Non l'ho misurata* la
  toglie anche a posteriori.

Ne discendono le stesse conseguenze del web, già in codice: una **misura tolta** non cancella le
registrazioni (i valori restano con una chiave che non ha più una riga, e si mostrano come *misura
tolta*), un'**opzione tolta** si dice invece di far sparire il valore, e una **scelta non ha un
numero** — `MmMisura.numerico()` torna `null` — quindi nel riepilogo al posto dello storico in
miniatura c'è la **distribuzione**, che è la domanda che una combo pone davvero.

Due differenze di forma, volute, entrambe per i caratteri di sistema grandi: le **opzioni di una
combo sono pulsanti e non una tendina** (una tendina taglia le etichette proprio dove la scelta si
fa), e lo storico in miniatura è disegnato con dei `Box` invece che con le `.spark` del CSS —
questa schermata non carica nessuna libreria di grafici, come la pagina.

Il **titolo della registrazione è obbligatorio nell'app, non nel database**, esattamente come sul
web: `mm_diary_entries.title` resta `NOT NULL DEFAULT ''` perché le registrazioni fatte prima della
colonna un titolo non ce l'hanno, e un vincolo che le rifiutasse renderebbe impossibile perfino
aprirle per correggerle.

### ⚠️ Abituati nativo: le regole stanno nel database, non in Kotlin

`abituati/` porta in nativo le abitudini di `habit-tracker.html`: la scheda
🎯 **Oggi** con le spunte (fatto / fallito / saltato, e una riga per orario sulle abitudini a più
slot), 📋 **Tutte** con creazione, modifica, **interruzione, ripresa** ed eliminazione, 📦 **Archivio**
degli stack finiti, più le due cerimonie del web — lo stack vinto e il game over, ciascuna con
*Ricomincia da una data* oppure basta. Restano di là statistiche, categorie, promemoria e
impostazioni.

📋 **Tutte** ha la stessa **tendina sullo stato** della pagina (*Attive / Interrotte / Tutte*,
`FiltroStato`), e parte da «Attive» come là: le interrotte sono memoria, non lavoro di oggi, e un
elenco che le mescola alle vive fa sembrare da spuntare qualcosa che è fermo. Sulla scheda di
un'interrotta la **modifica non si offre** — quel che serve è rimetterla in moto — e i pulsanti
stanno in una `RigaScorrevole` con le larghezze misurate su **tutte e quattro** le etichette
possibili, comprese quelle che quella scheda non mostra.

⚠️ **Riprendere chiede da quale data ripartire e riscrive `started_at`** (proposta: il **giorno
dopo l'ultima spunta**), esattamente
come nel web: con la data originale il primo `hb_reconcile` marcherebbe `missed` ogni giorno passato
dall'interruzione — guarda da `started_at` a ieri — i jolly finirebbero sul posto e il game over
scatterebbe prima ancora di rivedere la scheda. Quanto costa la data scelta lo dice
`hb_giorni_da_recuperare`, **la stessa RPC che chiama la pagina**, riletta a ogni cambio di data; se
i jolly non bastano il pulsante non si blocca, cambia scritta in *Riprendi lo stesso*.
`current_failures` torna a zero perché è una cache che la riconciliazione ricalcola.

Interrompere e riprendere sono un `update` diretto su `status` in tutt'e due le implementazioni, e
non è un'eccezione alla regola qui sotto: le RPC governano dove va la prossima occorrenza, non se
l'abitudine è in corso. L'interruzione **annulla anche le notifiche già in coda** prima di
cancellare la regola (una partita dopo chiederebbe di spuntare un'abitudine ferma): stesso ordine di
`deleteHabitNotificationRule()`.

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
| `hb_giorni_da_recuperare` | Quanto costa **riprendere** un'abitudine interrotta da una certa data: un jolly per giorno dovuto senza spunta. Sola lettura, la chiamano tutt'e due i client |
| `hb_giorno_risolto` | Di quel giorno resta qualcosa in sospeso? Ogni periodo dovuto ha la sua riga, comunque sia andata — **non** «è andato bene», che è `hb_giorno_fatto`. È la condizione che lascia chiudere uno stack già l'ultimo giorno |

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

### ⚠️ Calorie nativo: due pagine, e il conto è quello della pagina

`calorie/` porta in nativo le due schermate che si aprono **col telefono in mano**:
📊 **Dashboard** — le cinque sezioni della pagina nello stesso ordine (⚖️ le pesate, 🔥 le calorie
di oggi, 📐 le calorie per tratto, 📈 come sta andando, e il giorno per giorno dell'intera dieta) —
e 📓 **Diario**, un giorno per volta coi pasti configurati, la barra del target, il riquadro che
spiega da dove esce il numero, *📋 Ricopia da ieri*, i macro e ✏️/🗑 su ogni riga. Più il
**+ galleggiante**, che è la ragione per cui questa app sta sul telefono: si segna l'alimento nel
momento in cui lo si mangia, non la sera al computer.

**Restano sul web 🍎 Alimenti e ⚙️ Impostazioni**, e non è una mancanza: curare il catalogo,
configurare i pasti e scegliere il fattore di attività sono cose che si fanno da seduti e una
volta sola. Il nativo quelle scelte le **legge** — `cm_settings.al_pasti`, `al_profile.activity`,
`cm_profile`, e `al_foods.unit` — e non le può cambiare. ⚠️ L'unità è fra queste: un liquido si
segna in ml anche dal telefono (la casella dice «Quantità (millilitri)» e i tagli sono quelli dei
liquidi), ma **quale alimento sia un liquido si decide di là**. Il `put("unit", …)` sulle righe
del diario però c'è, e non è facoltativo: senza, una riga segnata dal telefono nascerebbe in
grammi per il valore di DEFAULT della colonna, cioè con un'etichetta sbagliata e nessun errore.

⚠️ **I due pulsanti g/ml nella schermata della porzione sono stati provati e tolti** (v1.0.63,
ritirata dalla 1.0.64): quella schermata risponde a una domanda sola — «quanto ne hai mangiato?» —
e una scelta su com'è fatto l'alimento, con la riga che la spiega, si prendeva mezzo schermo per
una cosa che lì non si sta chiedendo; coi caratteri di sistema grandi il campo della quantità
finiva sotto la piega. Il posto di quella scelta è una schermata degli alimenti, che in nativo non
c'è — non il caricamento del pasto. Restano di là anche il **codice a barre** (qui non c'è né
`BarcodeDetector` né una fotocamera accesa per questo) e lo *scrivilo a mano*, che è il form di
🍎 Alimenti: sono le due strade per mettere in dispensa. Come sul web, la **dieta non si decide
qui**: l'obiettivo si crea in «Ti pisasti?» e da questa parte si legge soltanto.

Le regole stanno in `calorie/CalorieRegole.kt`, ricalcate dalla pagina una per una, e **vanno
cambiate nelle due implementazioni insieme**: deficit in due addendi (ritmo del tratto + recupero
dello scarto, mai sotto zero), l'ultimo traguardo che vale come peso finale solo se i traguardi
sono almeno due, il target **congelato** in `al_days` alla prima riga del giorno e ricalcolabile
solo su richiesta esplicita, il saldo spalmato su **tutti** i giorni che restano, un giorno senza
righe che non entra nel saldo, e da **oggi** solo lo sforo — perché un diario a metà non è un
digiuno.

⚠️ **Una duplicazione che la pagina ha e qui no**: `pesoPianoAl()` è la copia di
`getInterpolatedTarget()` di `weight-quest.html`, e in nativo quella funzione esiste già
(`PesoRegole.targetInterpolato`) — si chiama quella. Per la stessa ragione l'obiettivo attivo si
decodifica con `Obiettivo.da` del modulo `peso`, che è già l'unico posto in cui `ps_objectives` e
le sue milestone si leggono.

Il **➕ prende lo schermo intero** invece di aprire un dialogo — ci stanno una ricerca, un elenco di
risultati e la scelta della porzione, e coi caratteri di sistema grandi un dialogo sarebbe una
feritoia — ed è in due passi, come nella pagina: si cerca (📗 catalogo e 🌐 rete sotto **due
intestazioni**, mai concatenati) e poi si dice quanto (½/1/2 con l'etichetta dell'alimento, i
pulsanti che **sommano**, l'↺ che azzera). La ricerca in rete passa dalla Edge Function
`al-food-search` con la stessa pausa di digitazione del web: **Open Food Facts limita le ricerche
testuali a una decina al minuto**, e cercare a ogni tasto premuto fa bandire l'indirizzo IP.

Due differenze di forma, volute, entrambe per i caratteri di sistema grandi. I **numeri non sono
una griglia di riquadri** ma una riga ciascuno, etichetta a sinistra e valore a destra: tre celle
affiancate con testi di lunghezza diversa vanno a capo un numero diverso di volte e perdono
l'allineamento. E un **tratto è una scheda, non una riga di tabella** — sette colonne o si tagliano
o vanno a capo ognuna per conto suo — con l'ordine di lettura della pagina conservato: il
**target** subito dopo i pesi, perché è la risposta per cui quella tabella esiste. Il grafico delle
calorie è disegnato a mano su un `Canvas` scorrevole, con le misure in **dp e mai in pixel
grezzi**; il grafico del peso resta di là, perché il peso è materia di «Ti pisasti?» e qui compare
già nei riquadri e nel giorno per giorno.

### ⚠️ Obiettivi nativo: il solo 📆 Piano quotidiano

`obiettivi/` porta in nativo **una pagina sola di `obiettivi.html`**, ed è quella che la pagina
apre per prima: il 📆 **Piano quotidiano** (`PianoScreen.kt`). Tutte le azioni ancora da fare,
giorno per giorno — le arretrate in un riquadro solo in cima con quanti giorni ha la più vecchia,
poi oggi, poi i giorni che vengono, e in fondo quelle a libera ripetizione — e da lì si chiudono.
È la copia di `renderPianoPage()` riquadro per riquadro, comprese le due etichette *Domani* e
*Dopodomani* che valgono **solo per due giorni** (oltre, «fra 9 giorni» costringe a fare il conto
e la data no).

La **bolla in home apre il Piano** e non l'elenco degli obiettivi: le azioni si chiudono nel
momento in cui si sono fatte, non la sera al computer, ed è la ragione per cui questa app sta sul
telefono. L'elenco che c'era già (`ObiettiviScreen`, metriche e rilevazioni) resta un passo più
in là, dal 🎯 nella barra: `Route.OBIETTIVI` porta al Piano, `Route.OBIETTIVI_ELENCO` a lui.
Fino alla v1.0.67 Obiettivi era **sospesa in home** proprio perché il nativo le azioni non le
leggeva affatto.

⚠️ **Restano sul web** ✅ Azioni, 📊 Andamento, il Dettaglio di un obiettivo, le Esecuzioni e le
Impostazioni: da qui un'azione non si crea, non si modifica e non si cancella. Il piano dice cosa
resta da fare — le **concluse non ci sono**, di qua come di là: una riga che non si può più
toccare è memoria, e si guarda in 📊 Andamento.

Le regole che valgono qui sono le stesse del web e **vanno cambiate insieme**:

- **il ciclo di vita passa solo dalle RPC** `ob_action_complete` / `ob_action_skip`, e poi si
  rilegge — nessun calcolo di prossima occorrenza in Kotlin. `p_days` si chiede **solo** per le
  `single`, perché per gli altri tipi la RPC lo ignora e chiedere un numero che il server butta
  via sarebbe una bugia;
- ⚠️ **un'azione non si fallisce**: o la si fa, o la si sposta. Nessun *Fallisci*, come di là
  dalla v1.8.0. *Salta* compare solo su `PUO_SALTARE`, cioè su ciò che ha una prossima volta a
  cui rimandare;
- **completando con successo si aprono le rilevazioni** (`DialogoRilevazioni`), una riga per
  metrica collegata: **dopo** che la RPC ha chiuso l'azione — il ciclo di vita non deve dipendere
  dal fatto che uno si ricordi il numero — solo su `completed`/`completed_late`, e con la **data
  di occorrenza letta prima della RPC**, che subito dopo la riga è già sulla volta successiva.
  Una misura lasciata su *non adesso* **non si registra e non vale zero**, e ogni metrica passa
  da `ob_record_measurement`, che è l'unico punto di scrittura e l'unico a dire se un voto sta
  dentro la scala;
- ⚠️ **il giorno di un istante si legge in ora locale** (`giornoLocale()`, la copia di
  `localDay()`): `obiettivi.html` scrive `next_occurrence_date` con `toISOString()`, cioè in UTC,
  quindi un'azione delle 00:30 sta in archivio come le 22:30 **del giorno prima**. Tagliare i
  primi dieci caratteri — come fa `giornoDa()` in Tasks, dove i timestamp sono scritti già locali
  — la metterebbe fra le arretrate. È l'unico punto in cui i due moduli leggono le date in modo
  diverso, e non è una svista.

⚠️ **Un `workflow` non ha il *Completa* e da qui non si chiude**: il pulsante *Step* mostra a che
punto è (`n/N step`) e dice di andare sul web. Non è una dimenticanza — chiudere uno step vuol
dire riscrivere `workflow_steps`, sbloccare chi dipendeva da lui, scrivere la riga di storico coi
suoi punti e poi richiamare `ob_action_complete`: è la regola di `chiudiStep()`, che oggi vive in
un posto solo. Copiarla in Kotlin sarebbe una seconda regola su punti e archivi. Il giorno che
scendesse in una RPC come le altre, il pulsante si accende qui senza altro lavoro.

⚠️ **`ob_actions` si legge come `JsonObject`**, non come `data class` serializzata: `categories`
è un array di uuid e `workflow_steps` un jsonb, e una sola colonna di forma inattesa non darebbe
un'azione storta ma la **schermata vuota**. È la stessa scelta di `ts_tasks`. Categorie e
priorità invece **si leggono da `TasksRepository`**: `cm_categories` e `cm_priorities` sono
condivise coi task, e un secondo decoder per le stesse due tabelle sarebbe una seconda verità.

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

⚠️ **Il nome dentro la bolla non si taglia mai: il carattere si misura, non si stima**
(`misuraTesto` in `HomeScreen.kt`, la ricerca binaria di `fitFontSize()` in `index.html`). I due
vincoli sono gli stessi di là — la **parola più lunga** dentro `diametro × 0,64` senza essere
spezzata, e l'**altezza del nome mandato a capo** dentro quel che resta tolto il posto del
punteggio, misurato pure lui. Fino alla v1.0.66 era una stima (larghezza utile ÷ lunghezza della
parola più lunga) con un `maxLines = 3` a fare da rete: ma la stima è in `sp`, che **il sistema
moltiplica ancora per `fontScale`**, quindi coi caratteri di sistema grandi il nome andava a capo
una volta in più del previsto e la riga in eccesso spariva — ritagliata dal `maxLines` o dal
`clip(CircleShape)` della bolla, e **senza nemmeno i puntini** a dire che mancava qualcosa.

⚠️ **Il pavimento del carattere è in `dp`, non in `sp`**, ed è l'unica misura di testo dell'app che
lo sia: la bolla ha una dimensione **fisica** — il pavimento di 6 cm² non cresce coi caratteri di
sistema — quindi un minimo in `sp` crescerebbe con loro, e a ingrandimento doppio un nome lungo non
ci starebbe **nemmeno al minimo**, cioè il taglio tornerebbe proprio nel caso per cui quel conto
esiste.

### ⚠️ Non tutti i numeri di `score_query` sono punti

`cm_apps.score_query` restituisce un numero, ma per alcune app quel numero **conta delle cose**:
Spuntiamola dà i giorni che mancano al traguardo, Obiettivi gli obiettivi attivi, Memo le schede,
le app del conto familiare le transazioni in archivio. Quei numeri **non si scrivono sotto il nome
della bolla e non entrano nel totale che paga i premi** — un conteggio sommato ai punti è un saldo
che nessuno può rifare a mano, e un traguardo spuntato che *abbassa* i punti spendibili è un premio
che va e viene da sé.

L'elenco è **duplicato e va tenuto uguale**: `APP_SENZA_PUNTI` in `index.html` e `AppSenzaPunti`
in `home/PortedApps.kt`. Se divergono, le due home mostrano due totali diversi e un premio
comprabile da una parte non lo è dall'altra — è lo stesso rischio di `totaleNetto` qui sotto, un
gradino più a monte. Comprende anche app che in nativo non hanno una bolla: i loro punti entrano
comunque nel totale, quindi la domanda «sono punti?» le riguarda uguale.

⚠️ **Un punteggio può essere negativo**, ed è un'informazione, non un errore: una risposta di SOS
toglie punti, una decisione sbagliata pure. La bolla lo scrive **col segno** — la condizione è
«diverso da zero», non «maggiore di zero», in tutt'e due le home — perché una bolla senza numero
si legge come «zero», che è il modo peggiore di dire −40. **Solo lo zero resta muto**: una bolla al
minimo con uno «0» sotto sembra rotta, non vuota. Il **saldo spendibile** invece non scende sotto
zero (`updateScorePanel()` e `HomeState.totaleNetto`, entrambi `max(0, lordo − spesi)`): non si
comprano premi con un debito, ma il debito si vede.
`20260824190000_score_query_ammette_negativi.sql` ha tolto i `GREATEST(0, …)` che schiacciavano a
zero il totale di SOS e Decisioni — con quelli, da −40 a 0 la bolla mostrava lo stesso numero e il
malus non esisteva. Le altre `score_query` non stanno in nessuna migration: se una ha lo stesso
clamp, si toglie dal pannello ⚙️ delle badge query in `index.html`.

⚠️ **Il pavimento di 6 cm² è in «cm» del web, non col righello** (`sizeOf()` in `index.html`,
`BubbleLayout.diametro` nel nativo): `sizeOf` lavora in CSS px e li converte a 96 dpi, ma su
Android un CSS px **è** un dp — 1/160 di pollice — quindi quei 6 cm² valgono ~2,1 cm² veri, cioè
un pavimento di **104,5 dp** di diametro. Il nativo usava la densità vera (`160f`) e otteneva un
pavimento di 6 cm² fisici, **174 dp**: su uno schermo da 393 dp ci finivano sopra quasi tutte le
bolle, e la home diventava una fila di cerchi uguali — fra 10 punti e 5952 correva il 26 % invece
del 110 %. La proporzionalità *è* il senso delle bolle, quindi il fattore è quello del web,
`96f`: qui si convertono dp, non centimetri.

⚠️ **Il massimo su cui si normalizza è quello delle bolle mostrate**, non di tutte le app: il web
le mostra tutte, il nativo solo le portate, quindi ciascuna home scala sulla sua bolla più
grande. È voluto — con il massimo globale, in nativo la bolla più grande non arriverebbe mai a
220 dp — ma è la ragione per cui le stesse app possono avere due diametri diversi nei due APK.

⚠️ Il numero continua invece a **dimensionare** la bolla, anche quando non si vede: quella di
Spuntiamola che si sgonfia man mano che i giorni finiscono è la cosa che rende utile guardarla, e
con l'area a zero resterebbe al minimo per sempre. Un punteggio negativo dà la bolla **al minimo**,
in tutt'e due le implementazioni: l'area si ferma al pavimento di 6 cm² che tiene la bolla
toccabile. La scelta si fa sull'`html_file` e non su un
campo salvato dentro la bolla, perché le due cache (`localStorage` sul web, le preferenze nel
nativo) portano le bolle dell'avvio precedente: un campo aggiunto oggi lì dentro non c'è, e il
totale ripartirebbe da zero al primo avvio dopo l'aggiornamento.

### ⚠️ Il totale in home e i premi: la stessa cifra da tutt'e due le parti

In basso a sinistra c'è il **riquadro rosso del totale** (`PannelloTotale`, il `#score-panel` del
web), e toccarlo è l'unico modo per arrivare al **catalogo premi** — di qua come di là. Il numero
è **guadagnati meno spesi**, mai sotto zero: `HomeState.totaleNetto` qui, `updateScorePanel()` sul
web. Se le due formule divergono, un premio comprabile da una parte non lo è dall'altra.

⚠️ **Il lordo è la somma dei punteggi di *tutte* le app attive, non delle sole app portate.**
`HomeRepository.carica()` fa girare la `score_query` di ogni riga di `cm_apps`, comprese quelle che
qui non hanno una bolla: quei punti sono guadagnati lo stesso, e un premio costa uguale da tutt'e
due gli APK. Sommare le sole bolle darebbe un saldo diverso a seconda di che app si è aperta.
Restano fuori dal totale le app il cui numero **non è un punteggio** (sezione qui sopra).

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
  ⚠️ **`points_per_use` a zero è legittimo** — è il premio ripetibile che costa sempre uguale — e
  va distinto dal campo lasciato in bianco, che resta non valido. Il web ci era cascato con
  `parseInt(…) || null`, che trasformava lo zero in «non impostato»: il premio si salvava senza
  incremento e il form si riapriva vuoto. Le due implementazioni ammettono `>= 0` e all'elenco
  scrivono *costo fisso* invece di tacere, perché un incremento taciuto è indistinguibile da un
  incremento che nessuno ha scritto.

### ⚠️ Modalità nascosta: si accende col codice a colori, e vive in memoria

La modalità nascosta è **una sola per l'app** (`core/ModalitaNascosta`, l'equivalente di
`sessionStorage.hidden_mode`): l'accende la home, e le altre schermate la leggono — oggi Memo, che
quando è accesa mostra il proprio 🙈/👁.

⚠️ **Sta in memoria e basta, di proposito.** Sul web muore con la scheda del browser, qui col
processo. Scriverla nelle preferenze la farebbe ritrovare accesa al risveglio dell'app — cioè le
schede riservate a schermo senza che nessuno abbia rifatto il codice, che è esattamente ciò che la
modalità esiste per impedire.

Si accende con lo stesso gesto del launcher, in quattro passi: **pressione lunga sul campo delle
bolle** apre il widget delle caselle, si **toccano le bolle** e ognuna mette **il proprio colore**
nella casella successiva, la **freccia ›** (o una casella già piena) confronta, e l'occhio 👁 —
che compare solo a modalità accesa — la rispegne senza rifare il codice. Un confronto che torna
**inverte** la modalità, come `checkSequence()`; uno sbagliato non dice niente e chiude il widget.
Il widget si richiude da sé dopo **15 secondi** di inattività, e ogni tocco rimanda la chiusura.

Tre cose che sono il motivo per cui il codice funziona:

- **il codice non è scritto da nessuna parte nell'app**: sta in `cm_settings`, riga
  `hidden_mode_sequence`, la stessa che legge `loadHiddenSequence()` nel launcher. Senza quella
  riga il confronto esce subito e non apre niente — come sul web. Fino alla v1.0.20 bastava invece
  una pressione lunga sull'icona ↻, che era una modalità nascosta con la chiave sotto lo zerbino;
- **è la bolla a dare il colore**, quindi il codice cambia da sé se un giorno cambiano i colori
  delle app, e non c'è una tastiera da guardare mentre si digita;
- **a widget aperto le bolle non si aprono e non si trascinano**: il tocco vale solo come cifra. È
  la stessa scelta del web, dove `onDown` esce prima di armare `onUp` e `launchApp` non scatta mai
  — senza, ogni cifra del codice aprirebbe un'app.

Il fumetto degli avvisi di `index.html` ha una **settima sezione, 🎯 Obiettivi**
(`loadHomeAlertObiettivi`): le azioni da svolgere oggi, cioè quelle che cadono oggi più quelle
rimaste indietro, marcate con ⚠️. ⚠️ Le **prossime non entrano**: il fumetto è un promemoria di
quel che si può chiudere adesso, non l'agenda — quella è il 📆 Piano quotidiano dentro l'app. Le
azioni a libera ripetizione restano fuori per costruzione, perché `next_occurrence_date` è nullo.

Gli avvisi in home nativa hanno per ora **due fonti, Spuntiamola e Ta Firi?**: le altre quattro del web
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

### ⚠️ Il marchio vive in tre posti, e vanno cambiati insieme

Il logo di AppSphere sono **cinque cerchi** — arancio, rosso, verde e viola che si toccano a due a
due, e il blu al centro sopra a tutti — e sta scritto in tre file:

| Dove | File |
|---|---|
| Icone di lancio dei due APK | `app/…/ic_launcher_foreground.xml` e `appsphere-native/…/ic_launcher_foreground.xml` (fondo bianco per il WebView, nero per il nativo: è il segno che distingue le due app sul telefono) |
| Tutto il nativo (barra, login, biometria) | `appsphere-native/…/core/Logo.kt` — `LogoAppSphere`, disegnata su `Canvas` |
| Barra in alto delle pagine | l'SVG in linea dentro `#garsal-top-bar` (e `#user-bar` / `.login-top-bar-icon` in `index.html`) |

⚠️ **È già successo che divergessero**: le icone di lancio erano passate al marchio nuovo e le
barre mostravano ancora i cerchi olimpici — cioè il logo di due generazioni prima. Cambiando il
marchio si toccano tutti e tre.

⚠️ **Nella barra il marchio sta su un disco bianco**, e non è decorazione: la barra è `#0081C8` e
il cerchio centrale del marchio è `#067BC0`, quindi senza fondo il pezzo che regge il disegno
sparisce nel colore della barra. Sul launcher il fondo ce l'ha già.

⚠️ **I cerchi olimpici sono spariti dall'interfaccia**: `CerchiOlimpici` non esiste più —
`LogoAppSphere` ha preso il suo posto anche sull'avvio e sulla schermata della biometria, che
erano gli ultimi due punti rimasti indietro. **`Palette.olimpici` invece resta**, ed è un'altra
cosa: è il ripiego dei colori delle bolle della home quando `cm_apps.color` è vuoto.

⚠️ **Nel nativo il marchio è una funzione sola, non un drawable per contesto**: `LogoAppSphere`
prende un parametro `disco`, e il fondo bianco si accende solo dove serve. Cinque cerchi disegnati
su `Canvas` si adattano a qualsiasi dimensione senza un asset per densità — è la stessa scelta che
era già stata fatta per i cerchi olimpici.

⚠️ **Le barre delle pagine non sono tutte lo stesso marchio**: quelle che dicono *Garsal Apps* e
rimandano a `/` portano il logo; `calorie.html` (🍽️), `finanza.html` e figlie (*GarsalFinanza*),
`spese-ada.html`, `spese-personali.html` e le pagine ospiti hanno un'identità propria e **non**
vanno allineate.

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

### ⚠️ Otto app ora esistono in due implementazioni

`spuntiamola.html`, `obiettivi.html`, `events-log.html`, `ta-firi.html`, `weight-quest.html`,
`memo.html`, `habit-tracker.html` e `calorie.html` hanno un gemello Kotlin che lavora sulle
**stesse tabelle e sugli stessi campi**. È voluto — si spunta un giorno dal nativo e lo si ritrova
sul web con la sua emoji — ma non è gratis: **cambiare le regole di una senza l'altra le fa
divergere in silenzio**, esattamente come per lo snapshot del patrimonio e la vista Spese Famiglia.
I punti dove la regola *è* la funzionalità, e non un dettaglio:

- **Spuntiamola** — la chiusura della stecca scrive **prima** in `sp_stecche` e cancella **dopo**
  (`SpuntiamolaRepository.chiudiStecca`, come `dbCloseStecca()`); le spunte sono ottimistiche con
  rollback; frasi, emoji e messaggi dei traguardi sono copiati parola per parola.
  ⚠️ **L'umore della stecca c'è da tutt'e due le parti** (`sp_settings.mood` / `sp_stecche.mood`,
  vedi lo schema `sp_`): `MOODS` in `spuntiamola.html` e la `data class Mood` +
  `MOODS`/`moodDi()` in `SpuntiamolaModel.kt` sono **la stessa tabella scritta due volte** — frasi,
  emoji, traguardi, messaggi di chiusura, etichette e `fuochiAl100` — e **vanno cambiate insieme**,
  o le due implementazioni commentano la stessa stecca in due modi diversi. Il modo per
  accorgersene: far stampare a tutt'e due la propria tabella e confrontarla campo per campo — i
  **quattordici** campi per umore devono coincidere alla lettera, comprese le 31 frasi dell'attesa
  e le 30 dei bei giorni. ⚠️ Anche il **sottotitolo delle due schede** sta in `MOODS` (`sub` /
  `sottotitolo`) e non nel markup: scritto a mano nell'HTML era una seconda copia di quei testi,
  ed è già divergito una volta dalla riga che spiega cosa cambia (`hint` / `spiegazione`), che è
  una cosa diversa e sta sotto le schede. Da tutt'e due la voce
  della **chiusura si legge prima** che lo stato torni al campo libero (parametro `voce` nel web,
  `val voce = stato.mood` nel nativo): dopo, l'umore è già tornato all'attesa e dei bei giorni
  verrebbero salutati con la voce sbagliata.
  ⚠️ **Le giornate chiave sono facoltative e devono cadere dentro il periodo** in tutt'e due:
  `perchePuoNonEsserci()` è ricalcata riga per riga in `SpuntiamolaModel.kt`, coi due motivi
  distinti (fuori dall'intervallo / sabato o domenica su una stecca che salta i fine settimana) e
  la marcatura in rosso di una chiave che il periodo, accorciandosi, ha lasciato fuori. Differenza
  di forma: sul web la data si sceglie da un calendarietto con `min`/`max`, nel nativo si scrive a
  mano — quindi lì il motivo compare **solo su una data già completa**, o si leggerebbe un errore
  a ogni carattere digitato.
- **Obiettivi** — il progresso resta in `ob_objective_progress` e la scrittura in
  `ob_record_measurement`, che è anche l'unica a dire se un voto sta dentro la scala. Gli estremi
  di una metrica si leggono da `ob_metric_scale`, ricalcata in `scaleOf()` e in `ObMetrica.scala`:
  se cambia il modo di ricavarli, va cambiato in tutt'e tre. Le due barre non si fondono mai in una
  media.
  ⚠️ **Delle azioni il nativo porta il solo 📆 Piano quotidiano** (v1.0.68): si vedono quelle
  ancora da fare e si chiudono con *Completa* / *Salta*, che passano dalle RPC come sul web, e il
  successo apre le stesse rilevazioni. Restano di là ✅ Azioni, 📊 Andamento, il Dettaglio e le
  Esecuzioni, e da qui un'azione non si crea né si modifica. Le etichette sotto la barra
  dell'esecuzione, nella schermata dell'elenco, parlano ancora dei soli sotto-obiettivi e
  milestone. Dettagli nella sezione qui sopra.
- **Events Log** — le tabelle `el_*` non sono in nessuna migration: le colonne dei `data class`
  sono ricavate da come `events-log.html` le scrive. Gli eventi `DA_SELECT` registrano **solo se il
  conteggio è cresciuto** rispetto all'ultimo `count:N`. Il **registro mostra solo il gruppo
  scelto** in tutt'e due: `el_logs` non porta il gruppo, quindi il filtro passa per gli id degli
  eventi di quel gruppo (`logDelGruppo` nel nativo, le stesse due righe in `renderLogPage()`).
  Conseguenza in entrambe: la riga di un evento cancellato non sta in nessun gruppo e non si vede
  più da nessuna parte, pur restando sul database. Differenza voluta: il
  web si ferma agli 8 più recenti, il nativo li elenca tutti perché lì la lista scorre.
  ⚠️ **I gruppi 🙈 riservati hanno gli stessi tre stati da tutt'e due le parti** — modalità
  nascosta spenta: solo i normali; accesa: tutti; accesa col 👁: solo i riservati — e **il filtro
  sta nella query**, come in Memo: fuori dalla modalità nascosta la riga non si legge proprio.
  Fino all'agosto 2026 il nativo la colonna la decodificava e non la guardava: un gruppo riservato
  si vedeva sempre, con i suoi eventi e le sue registrazioni. Differenza di forma: il web ha un
  FAB, il nativo il 🙈/👁 nella top bar (e a modalità accesa marca col 🙈 la scheda del gruppo
  riservato, che il web non fa).
- **Ta Firi?** — il punteggio finale resta in `sf_finalize_challenge`; il check-in di oggi passa
  da `sf_checkin_set` e la correzione di un giorno passato no; la regola Smart Block si scrive da
  tutt'e due. Dettagli nella sezione qui sopra.
- **Ti pisasti?** (`weight-quest.html`) — target interpolato fra i traguardi, giorni senza pesata
  ricostruiti e contati lo stesso, confronto peso/target **a un decimale**, `target_weight`
  congelato nella riga; le due condizioni di `closeObjective('success')` (minimo di oggi / massimo
  del periodo sotto il peso finale) e il punteggio di chiusura; la sincronizzazione con Health
  Connect e Renpho (finestra di 90 giorni, permesso sullo storico, peso troncato a un decimale,
  pesate manuali tolte quando arriva il dato vero). **Premi dei traguardi e punti stanno sulle
  stesse righe** (`ps_milestone_prizes`, `ps_milestone_points`): soglie, distribuzione dei punti,
  id dei cinque premi e il «Mangiato !!!» vanno cambiati nelle due implementazioni insieme.
  Lo stesso vale per **come si chiude un obiettivo**: `total_score` = punti giornalieri +
  punti delle soglie **raggiunte** (solo col successo) ± bonus/malus finale. Il
  nativo porta pesata, tabella, grafico, Gestione Obiettivo, gratta e vinci e Salute: statistiche
  e dieta restano di là. Dettagli nella sezione qui sopra.
- **Memo** — note, liste, diari e 🔗 link esistono in tutt'e due. Il contenuto è **HTML**: il nativo lo
  converte in marcatori per modificarlo e lo riconverte salvando (`MemoHtml`), quindi ogni voce
  della barra del web deve avere il suo marcatore di qua. ⚠️ **La condivisione (`ACTION_SEND` +
  `text/plain`) è del solo nativo** dal settembre 2026: la scaletta del titolo vive quindi in
  `Link.titolo()` di qua e in `riempiTitoloSeVuoto()` di là, e va cambiata insieme. Le altre regole da tenere allineate:
  spunte ottimistiche con rollback, misure aggiornate riga per riga e mai ricreate, opzioni di una
  combo archiviate per **id**, misura non toccata che **non** finisce in `measures`, categorie
  riscritte da capo a ogni salvataggio, file del bucket cancellati prima della riga. Il filtro
  `riservato` sta nella query da tutt'e due le parti. Dettagli nella sezione qui sopra.
- **Abituati** — è l'unica dove le regole **non** sono duplicate: streak, jolly, giorni mancati e
  chiusura degli stack stanno nelle RPC `hb_*`, che il nativo chiama già e la pagina web deve
  ancora cominciare a chiamare. La prima che chiamano **tutt'e due** è
  `hb_giorni_da_recuperare`, il costo di una ripresa: è nata dopo il passaggio al database, quindi
  una copia in JavaScript non è mai esistita. Interrompi, Riprendi ed Elimina ci sono ora da tutt'e
  due le parti, con la stessa regola sulla data di ripartenza. Dettagli nella sezione qui sopra.

- **Calorie** — il conto del target è duplicato riga per riga in `CalorieRegole`: deficit in due
  addendi (ritmo del tratto + recupero dello scarto, mai sotto zero), peso finale solo con almeno
  due traguardi, target congelato in `al_days` e ricalcolabile solo su richiesta, saldo spalmato
  sui giorni che restano, giorno senza righe che non è un digiuno, e da oggi solo lo sforo. Il
  nativo porta 📊 Dashboard, 📓 Diario e il ➕ che segna un alimento; 🍎 Alimenti e ⚙️ Impostazioni
  restano di là, e da qui si leggono soltanto. Dettagli nella sezione qui sopra.

(`tasks.html` è la nona, ma ha una sezione tutta sua: le RPC del ciclo di vita.)

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
- **Interrompi ↔ Riprendi** sono le due direzioni di `hb_habits.status` fra `active` e `stopped`, e
  non passano dall'archivio: un'abitudine interrotta resta in `hb_habits` con tutte le sue spunte,
  ma esce dalla dashboard e dalla riconciliazione, quindi da lì in poi non genera `missed`, non
  consuma jolly e non può né vincere né fallire.
  ⚠️ **Riprendere chiede da quale data ripartire e riscrive `started_at`**. La finestra si apre sul
  **giorno dopo l'ultima spunta** (`defaultResumeDateStr()` / `AbituatiState.ripartenzaSuggerita()`),
  che è dove l'abitudine si era fermata davvero; le righe `missed` non contano come spunte — le
  scrive la riconciliazione, non l'utente — senza nessuna spunta si ripiega su oggi, e la proposta
  **non va mai oltre oggi**, perché uno `started_at` nel futuro è un'abitudine che non cade mai.
  Con la
  data originale, `checkMissedDays` — che guarda da `started_at` a ieri — marcherebbe `missed` ogni
  giorno passato dall'interruzione: i jolly finirebbero sul posto e il game over scatterebbe prima
  ancora di rivedere la scheda. La finestra conta quei giorni **prima** di scrivere
  (`countMissedIfResumed`, lo stesso conto di `checkMissedDays` — un jolly per giorno mancato, non
  per sessione) e lo dice; se anche così i jolly non bastano, chiede conferma invece di impedirlo.
  ⚠️ Il **promemoria non torna**: `stopHabit()` cancella la riga di `cm_notification_rules`, e ora
  che è cancellata orario e canale non stanno più da nessuna parte — si riscrive da MODIFICA (il
  nativo i promemoria non li gestisce affatto, quindi da lì rimanda al web).
  ⚠️ **Il conteggio dei giorni da recuperare non sta nel client**: è la RPC
  `hb_giorni_da_recuperare`, che chiamano sia la pagina sia il nativo — due copie di quella formula
  sarebbero due avvisi diversi sullo stesso game over. Torna `null` (non zero) se la chiamata non
  riesce: «non lo so» e «non costa niente» sono due cose diverse.
- **Interrompi, Riprendi ed Elimina esistono ora in tutt'e due le implementazioni**, con la stessa
  regola sulla data. In Gestione il filtro *Attive / Interrotti / Tutte* è la tendina `#filterStatus`
  del web e `FiltroStato` nel nativo, e parte da **Attive** in tutt'e due: le interrotte sono
  memoria, non lavoro di oggi. Fino all'agosto 2026 la pagina non aveva l'eliminazione
  (`deleteHabit()` esisteva ma nessun pulsante la chiamava) e il nativo non aveva né interruzione né
  ripresa: erano due elenchi che facevano cose diverse sulla stessa tabella.
- ⚠️ **Uno stack si chiude anche l'ultimo giorno, non solo il giorno dopo.** Le due strade per
  chiuderlo si perdevano proprio il giorno in cui la stecca finisce, e su una **giornaliera a più
  orari** si vedeva bene: segnati tutti gli appuntamenti dell'ultimo giorno — chi fatto, chi saltato
  col jolly — non succedeva niente fino all'indomani. L'obiettivo raggiunto conta i soli giorni
  **fatti per intero** (`countCompletedDaysInPeriod`, `hb_giorni_fatti`), e un giorno in cui un
  appuntamento è stato saltato non ci entra: per quanti giorni si segnino, il conto non arriva mai
  al traguardo. La scadenza del calendario, che i giorni saltati li copre coi jolly
  (`completato_con_jolly`), scattava invece da `oggi > ultimo giorno`. Ora scatta **anche l'ultimo
  giorno, ma solo quando di quel giorno non resta niente in sospeso** (`isDayResolved()` nel web,
  `hb_giorno_risolto()` per il nativo): chiudere l'ultimo giorno a stecca ancora da segnare la
  archivierebbe come persa mentre c'è tutto il tempo per finirla. ⚠️ «Risolto» non è «fatto»: vuol
  dire che **ogni periodo di quel giorno ha la sua riga**, comunque sia andata (`completed`,
  `failed`, `skipped`, `missed`); un giorno non dovuto è risolto per definizione.
  ⚠️ Non ci si può appoggiare allo streak per chiudere: quello del web (`computeHabitStreak`) conta
  le righe di **qualunque** stato ma **salta il primo giorno** — riceve `started_at` a mezzogiorno e
  lo confronta con giorni a mezzanotte — quindi l'ultimo giorno vale sempre `goal - 1`.
- ⚠️ **Il nuovo ciclo si propone da domani, non da oggi** — in tutt'e due le cerimonie (stack vinto
  e game over) e in tutt'e due le implementazioni (`tomorrowDateStr()` nel web,
  `LocalDate.now().plusDays(1)` in `Festa`/`GameOver`): la stecca che si è appena chiusa **si è
  presa oggi** — l'ultimo giorno è proprio quello che l'ha chiusa — e un ciclo che ripartisse dallo
  stesso giorno nascerebbe con la prima giornata già spesa, o già segnata dalla stecca di prima.
  Resta una proposta, il campo si cambia.
- ⚠️ **`hb_habits.frequency` ha tre valori e basta**: `daily`, `daily_multiple`, `weekly`. La
  tendina offriva anche *Personalizzata* (`custom`), che però **nessuna riga di codice leggeva** —
  la stringa compariva una volta sola, nell'`<option>`. Un'abitudine così cadeva nel ramo `else`
  di ogni funzione, cioè si comportava da giornaliera, **tranne nel controllo dei giorni
  mancati**: `checkMissedDays` e `hb_reconcile` (che ha un `ELSE false` esplicito) non la
  guardavano, quindi nessun giorno diventava `missed`, nessun jolly si consumava e lo stack non
  poteva fallire. La voce è stata tolta e
  `20260824180000_hb_frequenza_custom_a_daily.sql` ha riportato quelle righe a `daily`.
  ⚠️ Aggiungendo una frequenza nuova, i posti da toccare sono **cinque**: le due tendine della
  pagina, `checkMissedDays`, `hb_reconcile`/`hb_periodo_key` e `FREQUENZE` + `cadeIl()` nel
  nativo — dove una frequenza sconosciuta vale `false`, cioè l'abitudine **non compare mai in
  🎯 Oggi**. La tendina di modifica ripiega su `daily` quando la riga porta un valore che non
  conosce: prima il select restava senza selezione e il salvataggio scriveva `frequency: ''`.

### `events-log.html` — Events Log
- Groups → Events → Logs hierarchy
- Quick-log UI: select event, tap to log with timestamp
- **Un gruppo può essere 🙈 riservato** (`el_groups.riservato`, spunta nel modale del gruppo): è
  la stessa modalità nascosta del launcher e di `memo.html`, coi soliti tre stati. La accende
  **AppSphere, non questa pagina** (`sessionStorage.hidden_mode`, `postMessage`,
  `BroadcastChannel`); qui il FAB 🙈/👁 compare solo a modalità già accesa e alza o abbassa il
  solo filtro. ⚠️ **Il filtro sta nella query** (`loadGroups()`): i gruppi riservati non si
  leggono proprio. Eventi e registrazioni non hanno un flag proprio — seguono il gruppo
  (`filterEventsByVisibleGroups()`, poi i log per eventi visibili).

### `obiettivi.html` — Obiettivi
- Obiettivi annuali con sotto-obiettivi trimestrali (`parent_id`, due soli livelli)
- ⚠️ **Dettaglio e Andamento sono due finestre con due mestieri**, e la separazione è la
  funzionalità: **Dettaglio** dice *com'è definito* l'obiettivo — metriche, sotto-obiettivi,
  milestone e azioni, che da lì **si aggiungono** ma non si chiudono, e non ha né il pulsante
  *Rileva* né una riga di storico. **📊 Andamento** dice *come sta andando*. Le azioni si
  completano da ✅ Azioni.
- ⚠️ **La Tab 📈 Rilevazioni non esiste più** (v1.7.0), e con lei `openMeasure()` e la finestra
  della rilevazione singola: **un numero si registra chiudendo l'azione che dovrebbe muoverlo**, e
  si guarda in 📊 Andamento. Una pagina che chiedeva un valore slegato da quel che si era fatto
  invitava a inventarlo — e la data lì si sceglieva a mano, che è proprio ciò che
  `giornoDiChiusura` toglie di mezzo.
- **📊 Andamento** (pulsante accanto a *Dettaglio* sulla scheda): quattro numeri di testa
  (risultato, esecuzione, punti presi, giorni alla scadenza), **una curva per metrica** e lo stato
  delle azioni.
- **Due barre affiancate, mai fuse in una media**: *risultato* (metrica `primary` dell'obiettivo) e
  *esecuzione* (% figli + milestone completati). Il progresso del padre **non** è la media dei figli:
  quando le due barre divergono di ≥ 25 punti l'app mostra un avviso esplicito, perché è il segnale
  che il piano viene eseguito ma il metodo non funziona.
- Semaforo (`on_track` / `at_risk` / `off_track`) confrontando il risultato con l'ultima milestone scaduta
- **Una metrica chiede due cose, non otto**: un ruolo e un tipo. I due tipi sono i due modi veri di
  rispondere a «come va?» — **autovalutazione**, dove ti dai un voto dentro una scala che scegli tu
  (minimo e massimo, proposti 1-10) con scritto accanto cosa vuol dire votare basso e cosa alto; e
  **automisurazione**, dove misuri un numero, con scritto cosa si misura, da dove parti e dove vuoi
  arrivare. Il form mostra la scala *oppure* partenza/obiettivo, mai entrambe, com'è il vincolo un
  livello sotto.
- Il voto si dà con uno **slider che vive dentro la scala della metrica**: un voto fuori scala lo
  rifiuterebbe comunque `ob_record_measurement`, ma di lì non si può nemmeno comporre.
- Formula unica di avanzamento per tutt'e due i `kind`, e regge anche una scala che scende:
  `(corrente − da) / (a − da)` — es. pause, partenza 14 → obiettivo 3, corrente 6 ⇒ 73 %
- **📆 Piano quotidiano** è la pagina che si apre per prima: **tutte** le azioni ancora da fare,
  giorno per giorno — le arretrate in un riquadro solo in cima (con quanti giorni ha la più
  vecchia), poi oggi, poi i giorni che vengono, e in fondo quelle a libera ripetizione. Da lì si
  chiudono. ⚠️ Le **concluse non ci sono**: un piano dice cosa resta da fare, e una riga che non si
  può più toccare è memoria — si guarda in 📊 Andamento. È la stessa materia di ✅ Azioni letta in
  un altro modo: lì si cerca e si filtra, qui si scorre il calendario.
- ⚠️ **Una libera ripetizione non mostra una data**: `actionDay()` ripiega su `start_date`, e
  scriverla la farebbe sembrare una scadenza. Di lei conta l'ultima volta che è stata fatta.
- ⚠️ **Il Piano quotidiano esiste anche in nativo**
  (`android-app/appsphere-native/app/.../obiettivi/PianoScreen.kt`): è l'**unica** pagina di
  Obiettivi portata, e la bolla della home nativa apre lei. Sezioni, etichette dei giorni, quali
  pulsanti compaiono e la finestra delle rilevazioni sono ricalcate riga per riga e **vanno
  cambiate nelle due implementazioni insieme** — dettagli in *AppSphere nativa → Obiettivi
  nativo*.
- **✅ Azioni** è una voce di menù a sé, oltre alla sezione dentro il dettaglio di ogni obiettivo.
  L'elenco è raggruppato come la panoramica dei task — ⚠️ Scadute, 🎯 Oggi, 📅 Prossime,
  🔄 A libera ripetizione, 🏁 Concluse — con i filtri per obiettivo, tipo, priorità, categoria,
  stato e testo.
- **Quali pulsanti compaiono**: *Completa* sempre, *Salta* solo su ciò che ha una prossima volta a
  cui rimandare (`single`, `recurring`, `simple_recurring`, `multiple`). ⚠️ Un `workflow` mostra
  **Step** al posto di *Completa*: si chiude dai suoi step, e un pulsante che lo chiudesse di forza
  salterebbe quelli ancora aperti.
- **Esecuzioni** (v1.10.0): ogni scheda della pagina ✅ Azioni porta il pulsante *Esecuzioni*, col
  numero di volte che l'azione è stata chiusa, e apre l'elenco di quelle volte — **programmata**,
  **eseguita**, com'è andata, quanti punti — con un 🗑 per ciascuna. ⚠️ È l'**unico pulsante che compare anche su
  un'azione conclusa**: è lì che le esecuzioni ci sono, ed è la scheda che di pulsanti non ne ha
  nessun altro. Resta invece fuori dal 📆 Piano quotidiano e dal Dettaglio dell'obiettivo — un
  piano dice cosa resta da fare e il Dettaglio com'è definito l'obiettivo: le volte già fatte sono
  memoria.
  ⚠️ **Le due date sono due cose diverse e vanno lette insieme**
  (`20260831100000_ob_action_history_occurrence_date.sql`): lo storico diceva *quando* un'azione
  era stata chiusa (`timestamp`) e non *per quando* era programmata, e su un'occorrenza arretrata
  chiusa tre settimane dopo il ritardo non si vedeva da nessuna parte — l'etichetta lo dice solo
  per una `single` con scadenza (`completed_late`). Ora `ob_action_history.occurrence_date` porta
  il giorno di calendario, e la riga marca in rosso lo scarto (*24 gg dopo*).
  ⚠️ **La data si legge PRIMA che la RPC sposti `next_occurrence_date`**, dentro le tre
  `ob_action_*`: subito dopo la riga è già sulla volta successiva e quella che si stava chiudendo
  non è più leggibile da nessuna parte. È la stessa ragione per cui `occorrenzaDi()` nella pagina
  la legge prima di chiamare la RPC.
  ⚠️ **Niente backfill sulle righe già in archivio, e NULL su ogni libera ripetizione**: la data
  che le vecchie righe avevano non è più ricostruibile, e riempirla col `timestamp` direbbe che
  sono state tutte puntuali; una libera ripetizione un giorno programmato non ce l'ha per
  costruzione — `start_date` lì è quando è nata. In tutt'e due i casi la pagina mostra un
  trattino: un dato che non c'è si vede, un dato inventato no. È la stessa scelta delle caselle
  vuote di `fnz_income` e delle misure non registrate di Memo.
  ⚠️ **Le righe `terminated` non sono esecuzioni e non si elencano**, come in `esitoDi()`:
  chiudendo una singola la RPC ne scrive due nello stesso istante — l'esito, coi suoi punti, e la
  chiusura, sempre a zero punti e che nessun conto guarda — e mostrarle tutt'e due farebbe sembrare
  chiusa due volte un'azione chiusa una volta sola. Cancellando l'esecuzione se ne va con lei anche
  la sua riga di chiusura (`chiusuraGemella`, riconosciuta dallo stesso istante: i due INSERT
  stanno nella stessa transazione, quindi `now()` è identico), o resterebbe una riga che non si
  vede da nessuna parte e che nessuno può più togliere.
  ⚠️ **Cancellare un'esecuzione non riporta indietro l'azione**: i punti spariscono con la riga e
  la barra dell'esecuzione si rifà senza — `ob_objective_progress` conta le azioni che hanno una
  riga `completed`/`completed_late` — ma la **prossima occorrenza resta dov'è**, perché l'ha
  spostata la RPC quando la riga è nata e rifarne il conto all'indietro vorrebbe dire riapplicare
  in ordine tutta la storia. È la stessa scelta della cancellazione di un giro in SOS, e la
  conferma lo dice prima.
- ⚠️ **Un'azione non si fallisce** (v1.8.0): o la si fa, o la si sposta. Il *Fallisci* chiudeva una
  singola per sempre con un malus, e l'unica cosa che serviva davvero — «oggi no» — la fa già il
  salto. Via il pulsante, la funzione `failAction` e il campo *Fallimento* dal form;
  `failure_points` si salva a **zero**, perché un valore che non si può più prendere è meglio a
  zero che scritto e finto. ⚠️ La RPC `ob_action_fail` **resta nel database** e nessuno la chiama:
  toglierla è una modifica di schema, e i punteggi già presi restano nello storico.
  In 📊 Andamento il segmento si chiama **«non riuscite»** e non «fallite» — ci finisce solo chi è
  chiuso senza esserci riusciti, per esempio una multipla arrivata in fondo alle sue date — e
  compare solo se ce n'è almeno una.
- **I punti di partenza di un'azione**: successo **+10**, salto **−2**, in ritardo **−2**. Il
  ritardo valeva +3 (un premio ridotto per averla fatta comunque) ed è diventato una penalità come
  il salto. I punti restano dentro l'app: Obiettivi sta in `APP_SENZA_PUNTI`.
- ⚠️ **Il tipo di un'azione che esiste già non si cambia** (la tendina è bloccata in modifica):
  decide quali colonne quell'azione ha, e cambiandolo resterebbero dietro quelle del tipo di prima
  — le date multiple su una ricorrente — che nessuno ripulisce. È la stessa scelta di `TaskForm`
  nel nativo.
- ⚠️ **I giorni della settimana si mostrano da lunedì ma si salvano con la numerazione `extract(dow)`
  di Postgres** (0 = domenica), che è quella che `ob_action_next_recurring_date` confronta. Toccando
  una delle due parti senza l'altra le ricorrenze scatterebbero il giorno sbagliato, senza nessun
  errore.
- ⚠️ **Le date si scrivono e si rileggono in ora locale.** `localDay()` passa da `Date` invece di
  tagliare la stringa ISO: `slice(0,10)` darebbe il giorno UTC, e un'azione di mezzanotte finirebbe
  nel giorno prima. In salvataggio `localToISO()` fa il giro inverso, così l'ora scritta è quella
  che si rilegge.
- ⚠️ **Scrivendo il nome di uno step si aggiornano le pillole che lo citano, senza ridisegnare
  l'elenco**: il ridisegno sostituisce i campi di testo e chi sta scrivendo si vede sparire il
  cursore da sotto le dita.
- **Un'azione si collega alle metriche che dovrebbe muovere**, anche più d'una: si spuntano nel
  form fra quelle dell'obiettivo, si vedono sulla scheda dell'azione (📈 col bordo, per non
  confonderle con le categorie) e sotto ogni metrica nel dettaglio dell'obiettivo, e l'elenco
  azioni si filtra per metrica.
- **Completando un'azione con successo si apre la finestra delle rilevazioni** (`chiediRilevazioni`),
  una riga per metrica collegata — slider per un'autovalutazione, casella per un'automisurazione —
  con la descrizione della metrica sopra, una data e una nota per tutte. È il momento in cui il
  numero lo si sa. Tre regole che sono la funzionalità:
  - ⚠️ **si apre solo al successo** (`completed` / `completed_late`, e alla chiusura di un
    workflow): dopo un fallimento o un salto non è il momento di chiedere un numero;
  - ⚠️ **si apre DOPO che la RPC ha chiuso l'azione**, non prima: il ciclo di vita non deve
    dipendere dal fatto che uno si ricordi il numero. Chiudendo la finestra senza registrare
    niente, l'azione resta completata lo stesso;
  - ⚠️ **una misura lasciata su «non adesso» non si registra**, e non si registra come zero — è la
    stessa scelta delle misure di un diario in Memo e delle caselle vuote di `fnz_income`. La riga
    resta visibile ma spenta, così si vede che c'era;
  - ⚠️ **la data non si sceglie ed è la _data di occorrenza_**, non il giorno del clic: la
    rilevazione appartiene alla volta che si sta chiudendo. Si legge da `next_occurrence_date`
    **prima** di chiamare la RPC (`occorrenzaDi`), perché subito dopo la riga è già stata spostata
    sulla volta successiva; una `free_repeat` non ha un'occorrenza e allora vale oggi.
    ⚠️ Su un'azione scaduta occorrenza e oggi sono giorni diversi, e datare tutto a oggi vorrebbe
    dire che **due occorrenze arretrate chiuse nello stesso pomeriggio si sovrascrivono a
    vicenda** — l'unicità è `(metric_id, measured_on)`.
  Ogni metrica passa da `ob_record_measurement`, una chiamata per metrica; se una fallisce le altre
  restano registrate e la finestra resta aperta dicendo quali non sono passate. L'unicità
  `(metric_id, measured_on)` fa sì che registrare due volte lo stesso giorno **corregga** invece di
  aggiungere.
- In ⚙️ Impostazioni c'è una **zona pericolosa** che svuota Obiettivi: le sole sette tabelle
  `ob_*`, cancellate **dal figlio al padre** (`TABELLE_DA_SVUOTARE`). ⚠️ Categorie e priorità
  restano fuori di proposito: `cm_categories` e `cm_priorities` sono **condivise con Tasks**, e
  cancellarle da qui lascerebbe i task senza — un danno in un'app che non si sta nemmeno
  guardando. Si fa scrivere `CANCELLA` invece di un `confirm()` con l'OK a portata di clic, come
  in `spese-personali.html`, e la finestra dice **quante righe** stai per perdere e offre
  l'esportazione prima. ⚠️ Ogni DELETE porta il filtro `user_id`, ridondante rispetto alla RLS e
  voluto: una DELETE senza condizione è una riga che, il giorno che una policy cambia, cancella
  più di quel che dice. Le cascate basterebbero partendo da `ob_objectives`, ma una riga rimasta
  orfana resterebbe lì senza che nessuno la veda più.

#### ⚠️ I caratteri di sistema grandi: il difetto che si vedeva solo sul telefono

Il 28 agosto 2026 il Dettaglio si apriva **tagliato a sinistra** sul telefono, e da PC non si
riproduceva. La causa erano due cose che si sommavano:

1. `.section-title` è un flex `space-between` con l'etichetta e il suo pulsante: coi caratteri
   ingranditi non ci stavano su una riga, e il pulsante sbordava. **`flex-wrap: wrap`** — «una riga
   sola è un'ipotesi, non un dato»;
2. `.modal-content` aveva `overflow-y: auto` e nessun `overflow-x`. ⚠️ Per specifica CSS, con uno
   dei due su `visible` e l'altro no, il primo **si calcola `auto`**: bastava un pulsante largo e
   l'intera finestra diventava scorrevole di lato. Ora `overflow-x: hidden` è scritto, e quel che è
   davvero largo (le tabelle dell'Andamento) scorre **dentro il proprio riquadro** (`.trend-scroll`).

Da lì la passata su tutto il resto, verificata a 220 % di carattere su 360 px: `min-width: 0` sul
titolo del modale e `flex-shrink: 0` sulla ✕ (il titolo lungo la spingeva fuori); `.form-row` con
**`minmax(9rem, 1fr)` invece di `1fr 1fr`** — la soglia in `rem` cresce col testo, quindi le due
colonne diventano una sola da sé, senza una media query che guarda lo schermo invece del testo;
`flex-wrap` sulle righe degli step e delle misure; e **`overflow-wrap: anywhere` su `body`**,
perché a quella dimensione una parola sola può essere più larga del riquadro — spezzarla è meglio
che tagliarla, e a carattere normale non cambia niente.

#### I grafici dell'Andamento

Sono **SVG scritti a mano**: la pagina non carica nessuna libreria di grafici, e per due spezzate e
una barra non vale mezzo megabyte dal CDN — è la stessa scelta delle miniature di Memo.

⚠️ **L'asse verticale di una metrica è la sua scala** (`da → a`), non il minimo e il massimo
osservati: così l'altezza della curva si legge come «quanto manca», e due rilevazioni vicine non
sembrano un terremoto. Se un valore esce dalla scala l'asse si allarga per contenerlo, invece di
tagliarlo. Ne discende che il traguardo di norma **coincide col bordo**: la riga dell'obiettivo si
disegna solo quando cade *dentro* il grafico, altrimenti ripeterebbe l'etichetta dell'asse
sovrapponendocisi — al suo posto la parola «obiettivo» marca quale dei due estremi è la meta.

⚠️ **Gli spessori sono in pixel veri** (`vector-effect="non-scaling-stroke"`): il viewBox scala col
riquadro, e senza quello la linea sarebbe sottile sul telefono e grassa sul PC.

⚠️ **I tre colori delle azioni sono colori di stato, non di categoria**: non si riusano per «la
serie 4» e vanno sempre con l'etichetta accanto, mai il colore da solo. Il verde
(`#00967A`) è un passo più scuro di `--success` perché a 2,5:1 sul bianco la barra non si
distingueva dal fondo; il giallo invece resta quello della pagina — scurendolo **collassa sul
rosso** (ΔE 12 contro i 18 che servono). Le azioni ancora aperte non prendono un colore: sono il
**fondo** della barra, cioè quel che resta da fare. La legenda porta i conti a parole e sotto c'è
la tabella per tipo, che è anche il rimedio dovuto al giallo, sotto il rapporto di contrasto 3:1.

- **La barra «Esecuzione» conta anche le azioni** (`ob_objective_progress`):
  `(sotto-obiettivi raggiunti + milestone centrate + azioni riuscite) / totale`. ⚠️ Entrano le
  sole azioni che possono **finire** (`single`, `multiple`, `workflow`): una ricorrente e una a
  libera ripetizione non finiscono mai, al denominatore resterebbero per sempre tenendo
  l'esecuzione sotto il 100 % a piano concluso — e sarebbero un rimprovero per un'abitudine che
  sta funzionando. ⚠️ Al numeratore ci va l'azione **riuscita**, non quella chiusa: `terminated`
  lo diventa anche fallendo, e un piano fallito che riempie la barra direbbe il contrario di quel
  che è successo.

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
  I premi stanno su Supabase (`ps_milestone_prizes`), come i punti dei traguardi
  (`ps_milestone_points`), e `localStorage` (`wq_prizes_<id>`, `wq_mpts_<id>`) è rimasto **solo
  cache**, perché la barra compaia subito senza aspettare la rete: il premio è quindi lo stesso
  da ogni dispositivo e dall'app nativa. Le righe che stavano solo in locale salgono sul DB al
  primo caricamento (`fetchPrizes`).
- ⚠️ **I punti dei traguardi si incassano alla chiusura, e solo col successo**: al
  `total_score` si somma la distribuzione delle **soglie raggiunte** — raggiunte, non
  grattate: dimenticarsi di toccare una stellina non deve costare punti — mentre su un
  obiettivo chiuso come fallito valgono zero. Prima di questa regola i «+N» sotto le
  stelline non entravano in nessun totale: erano una promessa scritta a schermo e mai
  incassata. Il conto sta in `milestoneAwards()` (web) e in
  `PesoViewModel.puntiTraguardiRaggiunti()` (nativo), e la conferma di chiusura mostra i
  tre addendi separati.
- **Le caselle «Punteggio» e «Punti oggi» si cliccano** e aprono 📜 *Cronologia dei punti*,
  **una per casella e mai mescolate**: il «Punteggio» elenca i soli obiettivi **chiusi**
  (nome, periodo, esito, punteggio), i «Punti oggi» il solo obiettivo **aperto** —
  traguardi raggiunti, quanto varrebbe chiuderlo oggi da vincitore o da perdente, e il
  giorno per giorno con peso, target, punti e totale progressivo. Mescolarli vorrebbe dire
  far cercare il proprio numero fra righe che non c'entrano. Non ricalcola niente per conto
  suo — legge `buildScoreRows()` e `milestoneAwards()`, gli stessi due conti dei badge e
  della chiusura.
- ⚠️ **La casella «Punti oggi» somma i giornalieri e i traguardi già raggiunti**
  (`puntiOggiMostrati` nel nativo): è quel che si porta a casa chiudendo bene, al netto del
  bonus finale. La **chiusura non legge quel numero** — somma i due addendi per conto suo,
  o i traguardi conterebbero due volte. ⚠️ Quelle due
  funzioni sono ora **l'unico posto** dove i punti si contano: prima la stessa formula
  stava in `calculateTotalScores()` e in `closeObjective()`, che leggeva il badge dal DOM
  (e un trattino a schermo sarebbe diventato uno zero in archivio).
- **Dopo il gratta si dice se il premio è stato mangiato** (`🍽️ Mangiato !!!`, che scrive
  `consumed_on`): il premio resta vinto — la stellina non si spegne — ma il cibo sbiadisce col ✓ e
  il biglietto scrive quando. È **reversibile** (`↺ Non l'ho ancora mangiato`), perché un tocco
  per sbaglio non deve costare un cannolo. ⚠️ Stesso pulsante nell'app nativa, sulla stessa riga
  del database.

### `calorie.html` — Calorie
- Il **diario alimentare** e il target di calorie che ne discende. Si apre dalla voce 🍽️ **Calorie**
  nella barra di `weight-quest.html` (in tutt'e due le barre: quella a icone e quella laterale).
- **Non decide niente sull'obiettivo**: quello sta in «Ti pisasti?» e qui si legge — nessun numero
  da riscrivere di qua, i traguardi si spostano di là. Senza obiettivo attivo il target è il
  semplice **mantenimento**, e la pagina lo dice invece di far finta che ci sia un piano.
- **Il riquadro spiega sempre da dove esce il target**, per intero (basale × attività − deficit,
  coi chili che mancano e i giorni che restano): un numero calcolato da quattro grandezze che
  nessuno vede è un numero di cui non ci si fida, e alla prima sorpresa si smette di seguirlo.
- **La navigazione è una barra di icone, come in «Ti pisasti?»**, non un menù ☰. Sul telefono le
  quattro pagine si vedono tutte insieme e ci si sposta con **un tocco solo** invece di tre (apri
  il cassetto, scegli, il cassetto si chiude); sopra i 768 px la barra sparisce e resta la
  sidebar, dove le voci hanno il posto per il loro nome. La voce accesa si aggiorna in tutt'e due
  da `navigate()`, con un selettore solo: chi disegna non deve sapere quale delle due è a schermo.
  ⚠️ La barra **scorre di lato e non stringe le icone**: coi caratteri di sistema grandi le voci
  su 360 px ci stanno appena, e schiacciarle sotto il polpastrello le renderebbe non toccabili — è
  la stessa regola delle righe di pulsanti che non vanno mai a capo.
  ⚠️ **Barra e sidebar elencano le sole quattro pagine di Calorie** (v1.17.2): i due collegamenti
  in coda — ⚖️ Ti pisasti?, 🏠 AppSphere, col loro separatore — sono stati tolti da tutt'e due.
  Erano navigazione che porta *fuori* mescolata a quella che porta *dentro*, e il 🏠 ripeteva
  quello che la barra blu, che sta sopra ed è in ogni app, fa già. A «Ti pisasti?» si va da
  AppSphere o dai collegamenti scritti dove si parla dell'obiettivo — che è il punto in cui serve
  andarci. Rimettendone uno, va rimesso in tutt'e due: sono la stessa navigazione, mostrata in due
  modi a seconda della larghezza.
  ⚠️ `min-width` è in `em`, quindi si misura sul font dell'**icona** e non su quello della pagina:
  a 3.2em faceva 74 px a voce e la barra scorreva già a carattere normale. Resta in `em` di
  proposito — il bersaglio da toccare deve crescere col testo di sistema — ma il valore va provato
  a 360 px, non calcolato a mente.
- Quattro schede: 📊 **Dashboard** (la prima che si apre), 📓 **Diario** (giorno per giorno, coi
  pasti configurati), 🍎 **Alimenti**, ⚙️ **Impostazioni** (dati anagrafici, attività, pasti, stima
  delle calorie, obiettivo). ⚠️ **📈 Andamento non esiste più**: il suo contenuto — i quattro KPI,
  le colonne delle calorie contro la linea del target, il peso con la curva del piano e il giorno
  per giorno — è dentro la Dashboard.
- La **📊 Dashboard** è in cinque sezioni, nell'ordine delle domande che ci si fa aprendo l'app:
  ⚖️ **le pesate** (peso minimo di oggi, peso che il piano chiede oggi, quanto manca al peso
  finale), 🔥 **le calorie di oggi** (target, mangiate, restano), 📐 **le calorie per tratto**,
  📈 **come sta andando**, e il **giorno per giorno**.
  ⚠️ **Tutto qui dentro copre l'arco della dieta, non una finestra di N giorni**: il selettore
  «ultimi 30 giorni» dell'Andamento è sparito perché il periodo *è* il piano, e due archi diversi
  sulla stessa pagina sarebbero due risposte diverse alla stessa domanda. `arcoDellaDieta()` tira
  però il primo giorno in avanti fino alla finestra di caricamento, per la stessa ragione del
  fondo del diario, e quando lo fa la pagina lo dice: una riga «niente segnato» per un giorno di
  cui non si sono lette le righe è una bugia.
- ⚠️ **Il giorno per giorno arriva fino alla FINE del piano, e i giorni futuri hanno un target
  previsto**: si parte da quello del **tratto** (`targetTrattoAl`, il valore «se stai sul piano»,
  senza recupero) e ci si porta dietro lo **scarto del giorno prima** — sforato ieri, oggi ce n'è
  di meno; rimasto sotto, oggi ce n'è di più. Il riporto si mostra fra parentesi accanto al
  numero. ⚠️ Il saldo accumulato si **spalma su TUTTI i giorni che restano** fino alla fine del
  piano, e ogni giorno futuro prende la stessa fetta — che quindi non si ricalcola giorno per
  giorno, così il numero si controlla a mente («900 in 80 giorni, undici al giorno»). Fino alla
  v1.12.1 si scaricava tutto sul giorno dopo: uno sforo di 900 kcal faceva scendere il giorno
  seguente a 288 kcal, cioè un target che non si può seguire — e un target che non si può seguire
  non lo si segue. Un giorno **senza righe non entra nel saldo** — non è un digiuno, è un giorno
  non segnato — ed è la stessa regola delle colonne mancanti nel grafico.
  ⚠️ **Da OGGI entra nel saldo solo lo sforo, mai il risparmio.** Oggi è l'unico giorno chiuso a
  metà: alle sette di sera il diario non è finito, e leggere quel che non è ancora stato segnato
  come un risparmio regalava calorie che nessuno si era guadagnato — un picco visibile nel
  grafico, corretto in v1.12.1. Lo sforo invece è già successo e non si disfa. È la stessa regola
  del «non lo so ≠ zero» che governa tutta la pagina: un diario a metà non è un digiuno.
  ⚠️ **Il saldo del KPI e quello che si spalma sono lo STESSO numero**, calcolato una volta sola
  in `giorniDellaDieta()` e restituito con la fetta al giorno: due conti separati sarebbero due
  verità sullo stesso dato — «−932 in meno» in alto e una fetta che non ci corrisponde sotto — e
  chi legge non saprebbe quale credere.
  ⚠️ Nel futuro il **peso non si trascina**: `pesoAl()` ripiegherebbe sull'ultima pesata nota, e
  una linea piatta fino alla fine del piano sembrerebbe una previsione che nessuno ha fatto.
- ⚠️ **La tabella dei tratti vive in `tabellaTratti()`, un posto solo**, perché la disegnano due
  schermate (Dashboard e Impostazioni): due copie sarebbero due tabelle che divergono il giorno
  che una colonna cambia — lo stesso difetto della vista Spese Famiglia duplicata in due pagine.
- ⚠️ **`drawChart()` sopravvive a Chart.js che non arriva**: la libreria viene dal CDN, e prima
  l'eccezione partiva e basta. Era sopportabile con i grafici in una pagina secondaria; ora sono
  nella Dashboard, cioè la prima cosa che si apre, e un'eccezione lì fermerebbe il disegno di
  tutto quel che viene dopo. Al posto del grafico si scrive perché manca: i numeri stanno già
  tutti nelle tabelle, quindi senza la libreria si perde la forma, non il dato.
- ⚠️ **Il diario non va più indietro del primo giorno della dieta** (`primoGiornoDiario()`): il ‹
  si spegne, l'input date porta il `min`, e la stretta è ripetuta nei gestori perché il pulsante
  spento è un suggerimento e da tastiera ci si arriva lo stesso. Prima il ‹ scendeva all'infinito
  e **dal 121° giorno indietro ogni giornata compariva vuota** — non perché lo fosse, ma perché
  `GIORNI_STORICO` non le aveva caricate: un archivio che sembra svuotarsi da solo è peggio di un
  pulsante spento. Il fondo è `max(inizio dell'obiettivo attivo, finestra di caricamento)`: il
  `max` garantisce che non si finisca mai su un giorno di cui non si hanno le righe, e con una
  dieta più corta di `GIORNI_STORICO` — il caso normale — non morde mai. Senza obiettivo attivo
  resta la sola finestra, che è tutto quel che si sa.
- **📋 Ricopia da ieri** porta nel giorno aperto tutte le righe del giorno prima, e **compare solo
  se ieri ha davvero qualcosa** (col conteggio sul pulsante): uno che c'è sempre e quasi mai fa
  qualcosa si smette di guardarlo. Resta offerto anche se oggi ha già delle righe — mangiare due
  volte la stessa cosa capita — ma allora **aggiunge**, e la conferma lo dice invece di lasciarlo
  scoprire dal totale. ⚠️ Si copiano i valori **congelati sulla riga di partenza**, non quelli di
  `al_foods` di adesso: la riga è il dato, l'alimento è solo da dove veniva — così la copia
  funziona anche per un alimento cancellato nel frattempo (`food_id` già NULL, valori sulla riga)
  e non cambia di nascosto perché qualcuno ha corretto una scheda. `times_used` non si tocca: è
  il contatore che ordina i «più usati», e un tocco che lo alza di cinque lo farebbe diventare la
  classifica di chi ricopia invece di chi mangia.
- ⚠️ **I riquadri dei pasti ci sono in ogni giorno, anche vuoti e anche nel passato.** Fino alla
  v1.9.1 un pasto senza righe si nascondeva nei giorni passati, per non fare rumore: ma il
  riquadro non è solo un riepilogo, porta il ➕ con cui si aggiunge a *quel* pasto. In un giorno
  passato ancora tutto da segnare — cioè proprio il caso in cui un giorno passato si apre — non
  ne restava nemmeno uno e la pagina finiva dopo la scritta «I pasti», che si legge come «qui non
  si può scrivere». Segnare ieri quel che si è mangiato ieri è il caso normale, non quello strano.
- **Quanti pasti al giorno lo decide l'utente** (⚙️ Impostazioni → 🍽️ I pasti della giornata):
  si spuntano i momenti in cui si mangia davvero e si rinominano. La configurazione sta in
  `cm_settings`, chiave `al_pasti`, come JSON — così è la stessa dal PC e dal telefono, dove
  `localStorage` sarebbe di quel browser e basta. ⚠️ **Gli id dei sei momenti sono fissi**
  (`colazione`, `spuntino_mattina`, `pranzo`, `spuntino_pomeriggio`, `cena`, `fuori_pasto`):
  `al_log.meal` ha un CHECK con esattamente quei valori, quindi si configura *quali* dei sei si
  usano e *come* si chiamano, non l'insieme dei valori possibili — per un settimo serve una
  migration. ⚠️ **Togliere un pasto non cancella niente**: le righe già segnate lì dentro restano,
  contano nel totale della giornata e si vedono marcate «pasto tolto» (senza il ➕), ed è la stessa
  regola della *misura tolta* nei diari di Memo — sparire dallo schermo restando nel conto sarebbe
  il modo peggiore di nasconderle. Il pasto già scelto compare sempre nella tendina anche se
  spento, o riaprire una riga vecchia la sposterebbe di pasto al primo salvataggio.
- Gli alimenti arrivano da tre parti, in **una tabella sola**: le voci generiche di partenza
  (`'base'`, seminate dalla migration), i prodotti confezionati letti da **Open Food Facts** col
  codice a barre o cercandoli per nome, e quelli scritti a mano. È lo stesso alimento visto da tre
  parti: tre tabelle vorrebbero dire tre ricerche per rispondere a «quante calorie ha».
- ⚠️ **La pagina non parla più con le banche dati: passa tutto dalla Edge Function
  `al-food-search`.** Quattro cose che dal browser non si potevano avere — CORS smette di essere
  un problema (quindi si possono usare servizi che gli header giusti non li mandano, e quali li
  mandino non lo decidiamo noi), le chiavi stanno nei Secrets invece che in chiaro nell'HTML, più
  fonti restituiscono **una forma sola**, e ogni fonte torna col suo esito. ⚠️ **La
  normalizzazione vive SOLO nella Edge Function**: la pagina non ha più una copia di `daOFF()` —
  due implementazioni della stessa conversione sono due valori diversi per lo stesso prodotto il
  giorno che una delle due cambia.
- ⚠️ **La ricerca per nome e la lettura di un codice a barre passano da due servizi diversi**, e
  si rompono una per volta:

  | Cosa | Dove | Stato |
  |---|---|---|
  | Prodotto per codice a barre | `it.openfoodfacts.org/api/v2/product/<code>.json` | in servizio |
  | Ricerca per nome | `search.openfoodfacts.org/search` (Search-a-licious) | in servizio |
  | Ricerca per nome, vecchia | `/cgi/search.pl` | ⚠️ **deprecata, 503 globale da aprile 2026** |

  Fino alla v1.3.0 la ricerca per nome passava dalla vecchia, ed è la ragione per cui non
  trovava mai niente. Ora si prova Search-a-licious e **solo se non risponde** si ripiega sulla
  vecchia: costa una richiesta in più e solo quando la prima è già fallita, e serve a coprire il
  caso in cui sia il servizio nuovo a essere giù.
- ⚠️ **La forma della risposta non si dà per scontata**: Search-a-licious risponde con `hits`
  (è Elasticsearch sotto), l'API vecchia con `products` — `prodottiDaRisposta()` accetta tutt'e
  due più l'array nudo, perché un cambio di chiave deve dare un errore, non «nessun risultato».
  Per la stessa ragione i campi di testo passano da `testoOff()`: su Search-a-licious
  `product_name` è indicizzato **per lingua** e arriva come oggetto (`{it, en, main}`), dall'API
  dei prodotti come stringa.
- Una stringa di **sole cifre lunga come un codice a barre** non viene cercata come testo: si
  legge il prodotto. Copre l'incollare un codice nel campo di ricerca.
- In ⚙️ Impostazioni c'è **🔌 Banche dati alimenti → Prova la connessione**. ⚠️ Le prove le fa la
  **Edge Function**, non il browser: è lei a parlare coi servizi, quindi è il suo esito che conta —
  provare dal browser direbbe se il browser ci arriva, che non è più la domanda. Un servizio
  esterno che smette di rispondere si vede altrimenti solo come «non trova niente», che è
  indistinguibile da «quel prodotto non c'è».
- Da 🍎 Alimenti si può **importare una tabella di composizione** (📄 Importa una tabella): un
  foglio .xlsx/.csv entra nel catalogo come alimenti `'base'`. ⚠️ Le colonne si riconoscono dalle
  **intestazioni** e non dalla posizione — un foglio di composizione ha decine di colonne in un
  ordine che cambia da edizione a edizione, e leggere «la terza» importa il fosforo al posto dei
  grassi senza che nessuno se ne accorga; l'anteprima dice quali colonne ha usato e quali no.
  ⚠️ Le **parentesi dell'intestazione non si buttano via**: «Energia (kcal)» e «Energia (kJ)» si
  distinguono solo per quelle, e togliendole l'energia veniva letta in kJ come se fossero kcal —
  un alimento con quattro volte le calorie che ha. ⚠️ In un CSV il **separatore si riconosce
  contando** (`;` nelle tabelle italiane, dove la virgola è il decimale): dando per scontata la
  virgola ogni riga finisce in una cella sola e le colonne non si riconoscono più. E «tr»
  (tracce), «-» e «n.d.» tornano **null e non zero**, come ogni casella vuota di questa app.
- ⚠️ **Open Food Facts limita le ricerche testuali a una decina al minuto** (la lettura di un
  singolo prodotto molto meno): cercare a ogni tasto premuto fa **bandire l'indirizzo IP**, quindi
  la chiamata parte solo dopo una pausa di digitazione (`RITARDO_RICERCA`). Da browser lo
  `User-Agent` non si può impostare e OFF chiede di identificarsi: si usano `app_name` /
  `app_version` / `app_uuid` in query string, che è il sostituto previsto apposta.
- ⚠️ **Le calorie non sono sempre dove ci si aspetta**: `energy-kcal_100g` c'è quasi sempre, ma
  dove l'etichetta è stata inserita in kJ si converte, e `energy_100g` senza suffisso è ambiguo —
  si guarda `energy_unit` invece di dare per scontato che siano kJ. Un prodotto senza nessuna delle
  tre **non è un prodotto a zero calorie**: torna `null` e la pagina lo dice.
- **Quel che il catalogo non ha, la pagina va a prenderlo.** Dal 📓 Diario si cerca un alimento e,
  passata la pausa di digitazione, ai risultati locali si aggiungono quelli di Open Food Facts;
  scegliendone uno il prodotto entra in `al_foods` **insieme alla riga del diario**, non appena lo
  si apre — aprire un risultato per sbaglio riempirebbe il catalogo di prodotti mai mangiati. Da
  🍎 **Alimenti** la stessa ricerca ha invece un *➕ Aggiungi* che archivia e basta: lì si mette in
  dispensa prima di mangiarlo. Aggiungendone uno gli altri risultati **restano** — da una ricerca
  sola se ne prendono spesso due o tre, e azzerarla costerebbe una chiamata in più a OFF, che le
  ricerche le conta.
- ⚠️ **Da dove viene un alimento si vede prima di sceglierlo, e la distinzione che conta è una
  sola**: è già nel tuo catalogo, oppure sta arrivando adesso da una banca dati pubblica? Il primo
  è un dato tuo, già guardato e correggibile; il secondo l'ha scritto qualcun altro, può mancare,
  può essere sbagliato e quando lo scegli entra in archivio. La porta l'**icona in testa alla
  riga** (📗 catalogo / 🌐 in rete), che è dove cade l'occhio per primo, e il **colore del badge**
  la ripete (ambra per la rete, l'unico colore che nessuna fonte interna usa); il badge dice
  invece *quale* fonte, che è l'informazione di secondo livello. Nell'elenco dei risultati le due
  provenienze stanno sotto **due intestazioni appiccicate in cima** (`.search-group`) e non
  concatenate: una fila unica in cui il catalogo sfuma nella rete le fa sembrare la stessa cosa.
  Fino alla v1.5.0 la fonte era una parola in coda alla riga dei valori, dopo le calorie e con lo
  stesso grigio: si leggeva solo andandola a cercare.
  ⚠️ Il nome della fonte di rete si legge da **`a.source`** e non è scritto a mano: `al-food-search`
  risponde `'off'` o `'usda'`, e la stringa fissa «Open Food Facts» attribuiva a Open Food Facts
  anche i risultati USDA — cioè diceva il falso proprio nel punto che esiste per dire da dove
  viene un numero. Tutto passa da `fonteDi()` / `iconaFonte()` / `badgeFonte()`, in un posto solo.
- ⚠️ **`FONTI_SALVABILI` è lo specchio del CHECK su `al_foods.source` e va tenuto uguale**: una
  fonte che il vincolo non ammette non entra in archivio, e l'insert lo rifiuta il database.
  `20260830120100_al_foods_source_usda.sql` ci ha aggiunto `'usda'`, che prima restava fuori — un
  prodotto USDA si poteva mangiare ma non mettere in dispensa, e andava ricercato ogni volta. La
  costante **decide insieme il messaggio e il salvataggio**: la finestra della porzione scrive «lo
  aggiungo al catalogo» solo dove succede davvero, e altrove dice che i valori restano sulla sola
  riga del diario. Due condizioni scritte separatamente sono la finestra che promette una cosa e
  il pulsante che ne fa un'altra, il giorno che una delle due cambia.
  ⚠️ `salvaAlimentoDiRete()` archivia la fonte **com'è arrivata** e non riscritta a `'off'`: fino
  alla v1.7.0 era una costante, e un prodotto USDA finiva in archivio dichiarato Open Food Facts —
  un dato falso proprio nella colonna che esiste per dire da dove viene un numero, e senza nessun
  modo, poi, di sapere quali righe correggere.
- ⚠️ **Nel form dell'alimento si dice se i valori sono per 100 g o per una porzione**, perché
  moltissime etichette li danno per confezione e ricopiarli dividendo a mente è il modo migliore
  per sbagliare: la «Pizza condita» stava in archivio a **1225 kcal per 100 g** — impossibile, il
  grasso puro ne fa 899 — perché quei numeri erano dell'intera pizza, e la riga sembrava una riga
  qualunque. ⚠️ **In `al_foods` i valori restano SEMPRE per 100 g**: la scelta non cambia dove
  finiscono, cambia come si leggono quelli scritti nel form, e la conversione si fa una volta
  sola al salvataggio. Una colonna «unità» sulla riga vorrebbe dire che ogni conto della pagina
  (diario, target, grafico, Edge Function) deve ricordarsi di guardarla, e il giorno che uno se
  ne dimentica il totale è sbagliato senza nessun errore.
  ⚠️ La scelta è una **dichiarazione sui numeri, non una trasformazione**: le caselle restano
  come sono. Per questo sotto c'è l'**anteprima** di quel che finirà in archivio — senza, un
  tocco per sbaglio su «1 porzione» dividerebbe valori già giusti e lo si scoprirebbe settimane
  dopo. E per la stessa ragione riaprendo un alimento si parte **sempre da «100 g»**, che è quel
  che c'è davvero in archivio: ricordare «porzione» sulla riga farebbe dividere una seconda volta
  al primo salvataggio. Senza i grammi della porzione l'opzione è spenta e il salvataggio si
  ferma — non c'è niente per cui dividere. `KCAL_IMPOSSIBILI` (900) è il **tetto fisico**, non un
  vincolo di gusto: sopra quel valore si chiede conferma, perché è un errore di unità e non un
  alimento insolito.
- ⚠️ **La porzione abituale si vede e si preme, non è più un campo precompilato in silenzio.**
  `al_foods.default_grams` (un uovo 55 g, una pizza 300 g, un cucchiaio d'olio 10 g) c'è nel
  catalogo da sempre, e la finestra della porzione ci si apriva sopra senza dirlo: il campo
  arrivava già scritto «55» e niente diceva che quei 55 g sono un uovo. Un numero che compare da
  solo si legge come un valore di ripiego, quindi lo si riscriveva a mano ogni volta — cioè
  esattamente il lavoro che la colonna esiste per togliere. Ora la finestra scrive la porzione e
  offre **½ / 1 / 2** (`porzioniDi()`): sono i tagli che si usano davvero — «due uova», «mezza
  pizza» — e una scaletta di multipli più fitta chiederebbe di leggere invece di far scegliere.
  Col nome della porzione si legge **«1 uovo · 55 g»**, «2 uova · 110 g», «½ pizza · 150 g»:
  singolare e plurale vengono dalle due colonne (vedi lo schema `al_`), e il **½ si scrive col
  simbolo** perché non ha genere — vale per l'uovo come per la pizza senza dover archiviare anche
  quello. Il nome si scrive da 🍎 Alimenti → ✏️ e senza di esso si legge «1 porzione».
  ⚠️ **Ogni pulsante SI SOMMA a quel che c'è nel campo, non lo sostituisce**, e accanto al campo
  c'è un **↺** per ricominciare da zero. Tre uova si segnano toccando «1 uovo» tre volte: non
  esiste un pulsante per ogni quantità che si possa voler mangiare, e sostituendo il secondo
  tocco non faceva niente di visibile — si leggeva come un tocco non passato. Il campo parte
  comunque dalla porzione abituale, che è il caso di gran lunga più frequente e non costa nessun
  tocco. La somma si arrotonda a un decimale: gli scalini sono interi, ma il campo si scrive
  anche a mano (12,5 g d'olio) e senza arrotondare verrebbero fuori le code binarie.
  ⚠️ La **scaletta fissa** (`GRAMMI_RAPIDI`) si toglie i valori che le porzioni già coprono: due
  pulsanti «150 g» uno accanto all'altro sembrano due scelte diverse e non lo sono. Un alimento
  **senza** porzione non se ne inventa una — una porzione inventata chi la legge se la crede, e
  sono le calorie della giornata: si parte da 100 g e la finestra dice dove scriverla.
- ⚠️ **Esiste anche in nativo** (`android-app/appsphere-native/app/.../calorie/`), ma solo per
  📊 Dashboard e 📓 Diario più il ➕ che segna un alimento: 🍎 Alimenti e ⚙️ Impostazioni stanno
  qui e basta, e di là si leggono. Il conto del target è duplicato in `CalorieRegole.kt` e **va
  cambiato nelle due implementazioni insieme** — dettagli in *AppSphere nativa → Calorie nativo*.
- Il **codice a barre** porta a due posti diversi a seconda di dove si parte (`S.scanPer`): dal
  diario finisce sulla porzione, dal catalogo archivia la voce e basta. La finestra dello scanner
  è la stessa.
- Lo **scanner del codice a barre** usa `BarcodeDetector` dove c'è (Chrome e la WebView di
  Android) e altrove chiede il codice a mano — nessuna libreria dal CDN: mezzo megabyte per una
  funzione che non tutti i browser possono usare non vale il peso della pagina.
- Nel grafico un giorno **senza colonna è un giorno non segnato, non un giorno a zero**, e per la
  stessa ragione il saldo del periodo somma i soli giorni segnati: contarci i giorni saltati come
  digiuni darebbe un deficit enorme e falso.
- Ha una **bolla in AppSphere** (`20260826120000_calorie_app_bolla.sql`, ambra `#d97706`). Il suo
  numero è la **striscia di giorni di fila chiusi dentro il target**, e ⚠️ **non è un punteggio**:
  sta in `APP_SENZA_PUNTI` / `AppSenzaPunti`, quindi non si scrive sotto il nome e non entra nel
  totale che paga i premi — un giorno sforato che *abbassasse* il saldo spendibile sarebbe un
  premio che va e viene da sé. Dimensiona però la bolla, che è il punto: cresce finché il diario
  regge e si sgonfia al primo sforo. **Non «le calorie che restano oggi»**: `sizeOf()` normalizza
  sul punteggio più alto fra tutte le app, e un numero sulle migliaia schiaccerebbe ogni altra
  bolla al minimo di 6 cm². La striscia **si ferma a ieri** — alle nove del mattino si è dentro
  il target per forza, e contarlo direbbe che è andata bene una giornata che deve ancora andare —
  e un giorno senza righe la interrompe come un giorno sforato.

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

### `forziere.html` — Forziere
- I file che devono stare al sicuro: cifrati **sul computer di Salvatore** prima di partire, e
  archiviati su Google Drive. Si apre dalla bolla in AppSphere, che però ha `riservato = true`:
  **si vede solo in modalità nascosta**, come Finanza. Un forziere annunciato in home a chiunque
  guardi lo schermo da sopra la spalla è metà del lavoro buttato.
- **Due segreti, non uno.** Le **24 parole** (che le sceglie l'utente, non l'app) sono la chiave
  vera: con loro si cifra e si decifra. La **passphrase** è solo una scorciatoia per l'uso
  quotidiano. ⚠️ **Non è la passphrase a cifrare i file** — se lo fosse, cambiarla vorrebbe dire
  ricifrare l'archivio; invece si rifà solo `scorciatoia.gpg`, una manciata di byte, e i file non
  si toccano nemmeno con 10 GB dentro.
- ⚠️ **Le 24 parole non si possono cambiare**: sono la password OpenPGP di ogni file già scritto.
  Si scelgono una volta sola. Il form controlla quello che conta davvero — **almeno 12 parole,
  almeno 10 diverse, nessuna ripetuta più di due volte** — e **non** la lunghezza delle singole
  parole, che non c'entra quasi niente: 24 parole casuali valgono ~250 bit anche se sono tutte da
  tre lettere. Quel che toglie forza è il senso compiuto (indovinata una, la successiva viene da
  sé), la ripetizione, e l'essere ricavate da nomi o date di famiglia.
- ⚠️ **La normalizzazione dev'essere rifacibile a mente davanti a un terminale**, perché le stesse
  parole si riscrivono dentro gpg, che non normalizza niente e prende i byte che digiti. Quindi la
  forma canonica è la più semplice che esista — **tutto minuscolo, una parola dopo l'altra separate
  da uno spazio solo** — e la pagina la **mostra** alla creazione perché sia quella che si ricorda.
  Qualsiasi regola più furba sarebbe irripetibile. `normParole()` vive in due posti,
  `forziere.html` e `APRI-QUESTO.html`, e **vanno cambiati insieme**.
- ⚠️ **`openpgp.config.aeadProtect = false` non è un ripiego: è la compatibilità.** OpenPGP.js v6
  userebbe di suo SEIPDv2 (AEAD, RFC 9580), che GnuPG legge solo dalla **2.5** in poi — cioè non la
  versione installata oggi sulla gran parte dei computer. Con SEIPDv1 il file lo apre qualunque gpg
  degli ultimi vent'anni, ed è tutto il punto di aver scelto questo formato. **Verificato**: file
  prodotto dalla pagina, aperto da **GnuPG 2.4.4** con `--use-embedded-filename`, byte identici.
- ⚠️ **QUESTA PAGINA NON CARICA NIENTE DA TERZI** (v1.2.0), ed è la regola che la protegge più di
  ogni altra: mentre è aperta tiene in memoria le 24 parole, quindi **qualunque** script che gira
  qui dentro può leggerle e spedirle senza che niente lo dica. Un `<script>` servito da un CDN è
  codice che qualcun altro può cambiare quando vuole.
  ⚠️ Fino alla v1.1.0 la regola era **scritta e non rispettata**: accanto a OpenPGP e 7-Zip
  vendorizzati c'era `supabase-js@2` da jsDelivr — per giunta non fissato a una versione, cioè
  «l'ultima 2.x che servono oggi». Al suo posto c'è ora un **client PostgREST minimale** scritto
  nella pagina, con la stessa forma di chiamata di supabase-js (`db('tab').select().eq(…)`,
  risposta `{data, error}`) così chi arriva dalle altre app non impara un secondo dialetto.
  ⚠️ `esegui()` **rifiuta una PATCH o una DELETE senza filtri**: qui non capita, ma il giorno che
  una riga si scrivesse senza `.eq()` il danno sarebbe silenzioso e completo. È lo stesso
  ragionamento dei DELETE col `user_id` ridondante in Obiettivi.
  ⚠️ Via anche **Google Fonts**: un CSS non legge una variabile JavaScript, quindi il rischio era
  di un altro ordine — ma «da terzi non si carica niente» è una regola che regge solo senza
  eccezioni, e i caratteri di sistema qui non costano niente.
- **Le due librerie stanno in `/vendor/` sul nostro dominio**, ferme e verificabili. ⚠️ È una
  **deroga** alla regola «ogni app è un file HTML solo», ed è l'unica ragione per cui la si
  accetta. 7-Zip si carica **solo premendo Esporta**, così l'uso quotidiano resta leggero.
- **Gli scomparti** (v1.2.0): una barra di pillole sopra l'elenco — `Tutti · 📁 nome · Senza
  scomparto · ➕`. Il ➕ carica **nello scomparto aperto**, il 📁 su una scheda sposta un file,
  e l'export continua a fare quel che l'elenco mostra, quindi esportare un solo scomparto viene
  gratis. ⚠️ Da «Tutti» il file nasce **fuori** da ogni scomparto invece che nel primo della
  lista: una scelta presa dall'app al posto di chi carica si scopre cercando il file altrove.
  ⚠️ «Senza scomparto» compare **solo se serve**: senza nessuno scomparto sarebbe un doppione di
  «Tutti», e a zero file una pillola che non apre niente. Dettagli nello schema `frz_*`.
- ⚠️ **F12 non si disabilita, e non servirebbe.** Da una pagina web non si può, e provarci copre
  solo un tasto: restano il menù del browser, le altre scorciatoie, gli strumenti già aperti,
  `view-source:`, un proxy — e il codice che blocca il tasto è a sua volta JavaScript, che si
  mette in pausa dagli strumenti stessi. Ma soprattutto **chi preme F12 è già seduto al computer
  col forziere aperto**: può leggere lo schermo. Il forziere protegge da chi ha il database, il
  Drive o un backup; per il resto valgono il blocco automatico e il blocco schermo del sistema.
  Il rischio vero della stessa famiglia era il CDN, ed è quello che si è chiuso.
- **La chiave dell'indice è una `CryptoKey` con `extractable: false`**: esiste nel browser ma da
  JavaScript non se ne leggono i byte. Le 24 parole invece devono stare in una stringa in memoria
  (servono a OpenPGP per cifrare i file nuovi) — ⚠️ e per questo il forziere si **chiude da sé**
  dopo `MINUTI_BLOCCO`, alla chiusura della scheda e all'uscita da AppSphere (`LOGOUT` sul
  `BroadcastChannel`). Niente `localStorage`, niente `sessionStorage`, mai.
- ⚠️ **Il timer da solo non basta: si chiude anche quando la scheda se ne va o passa in secondo
  piano** (v1.2.1, `pagehide` + `visibilitychange`). Una scheda che se ne va può **tornare
  indietro dalla cache di navigazione del browser** (bfcache) col forziere ancora aperto — e in
  quello stato i timer sono congelati, quindi i dieci minuti non passano mai: era l'unico stato
  in cui il forziere restava aperto per ore senza che nessuno lo guardasse. Rientrare costa la
  passphrase, cioè due secondi.
  ⚠️ **`pagehide` e non `beforeunload`**: il secondo non scatta affatto su iOS e su Android
  quando la scheda viene semplicemente messa via, che è il caso per cui questo serve.
  ⚠️ **Un'operazione lunga in corso non si interrompe a metà** (`S.occupato`, alzato da
  caricamento, apertura, eliminazione ed export): chiudendo il forziere mentre un file sta
  salendo, le 24 parole e la chiave dell'indice sparirebbero a caricamento avviato — il file
  resterebbe su Drive **senza la sua riga**, cioè un pacchetto cifrato che nessuno sa più cos'è
  e che dall'app non si può più togliere. Nascosta la scheda si aspetta la fine (un
  `setInterval`, che in una scheda nascosta il browser rallenta a uno scatto al minuto: qui va
  bene, non si sta contando niente). Su `pagehide` invece si chiude comunque — la pagina se ne
  sta andando e con lei tutto quello che c'era da salvare.
- ⚠️ **Un file si APRE nella pagina, non si scarica** (v1.1.0): il tocco sull'anteprima o su
  👁 Apri lo decifra e lo mostra in un visore — immagini, PDF, video, audio e testo. Scaricare
  è rimasto, ma dentro il visore e come **seconda** scelta dichiarata: un documento sceso nella
  cartella Download **esce dal forziere** e resta lì in chiaro finché qualcuno non se ne
  ricorda, che è l'opposto di quel che questa app fa. Il file decifrato vive in un blob in
  memoria e si butta chiudendo.
  ⚠️ **Il blob si revoca in `chiudiModale()`** e non in un pulsante: da quella finestra si esce
  anche col ✕, con l'indietro di Android e col blocco automatico, e un blob non revocato resta
  raggiungibile per tutta la vita della pagina.
  ⚠️ **Il tipo con cui si costruisce il blob lo decide `comeMostrare()`, non `meta.tipo`**: un
  file dichiarato `text/html` dentro un `<iframe>` girerebbe **nella nostra origine**, cioè
  nella pagina che in quel momento tiene le 24 parole in memoria. L'iframe si usa quindi solo
  per il PDF, forzando `application/pdf`; un SVG passa da `<img>`, dove gli script non partono;
  tutto il resto che il browser non sa mostrare **lo dice e offre lo scaricamento**, invece di
  restare un riquadro nero.
- ⚠️ **Scaricare costa la passphrase** (v1.2.2): il ⬇️ dentro il visore la chiede **ogni
  volta**, prima di far uscire il file. È l'unico gesto che porta un documento **fuori** dal
  forziere, in chiaro e per sempre, e la domanda serve a dire che al computer ci sei tu
  *adesso* — ricordarla anche solo per pochi minuti toglierebbe la protezione proprio nel
  caso per cui esiste, la scrivania lasciata un momento.
  ⚠️ **Si verifica APRENDO la scorciatoia** (`passphraseGiusta`), non confrontandola con
  qualcosa in memoria: in memoria non c'è — allo sblocco serve ad aprire `scorciatoia.gpg`
  e viene buttata subito, e deve restare così. Se ne escono **esattamente le 24 parole che
  il forziere sta usando**, è quella: è lo stesso controllo dello sblocco, e distingue
  perfino la scorciatoia di un *altro* forziere, che si aprirebbe benissimo portando però
  parole diverse. Un confronto con una variabile sarebbe un controllo che passa sempre.
  ⚠️ **La scorciatoia cifrata si tiene in memoria** (`S.scorc`, riempita allo sblocco, alla
  creazione e al cambio passphrase): non è un segreto — su Drive sta esattamente così — e
  senza di lei la verifica chiederebbe la rete, cioè col Drive irraggiungibile la pagina
  mostrerebbe il file e non lo farebbe scaricare.
  ⚠️ **La domanda sta DENTRO il visore**, al posto della riga dei pulsanti, e non in una
  seconda finestra: `#modalBody` è uno solo, quindi un `apriModale` lì sopra cancellerebbe
  il visore e `chiudiModale` revocherebbe il blob — cioè proprio il file che si sta per
  scaricare.
  ⚠️ **Le 24 parole restano una via**, come nella schermata di sblocco: chi è entrato con
  loro e ha risposto «Dopo» al cambio passphrase ha su Drive una scorciatoia che si apre
  con quella **vecchia e dimenticata**, e senza questa via lo scaricamento gli resterebbe
  chiuso per sempre senza che niente glielo spieghi.
  ⚠️ Il controllo sta in `scaricaDalVisore()` e **non** in `salvaSulDisco()`, che è
  condivisa con l'export `.7z` — che una password la chiede già di suo — e con
  📄 Scarica APRI-QUESTO, che di segreti non ne porta nessuno.
- **Export `.7z`** (💾): i documenti dentro stanno **in chiaro**, protetti dalla cifratura AES-256
  del 7z stesso — un archivio pieno di `.gpg` sarebbe una scatola dentro una scatola e non
  risparmierebbe niente a chi lo apre. ⚠️ **`-mhe=on` cifra anche l'elenco dei nomi**: senza, la
  lista dei file si legge senza password, e i nomi sono metà del segreto. La password sono le 24
  parole, ma se ne può mettere un'altra per dare quella copia a qualcuno senza consegnare anche il
  forziere. **Verificato** con 7-Zip vero: senza password nemmeno l'elenco si apre.
- ⚠️ **L'export è la risposta a «perdo l'account Google»**, che il progetto inizialmente non
  copriva: i file stanno su Drive, quindi perso l'account sono persi — le 24 parole aprirebbero
  benissimo qualcosa che non c'è più. La pagina dice da quanti giorni non lo si fa, e lo dice a
  gran voce finché non se n'è fatto **nemmeno uno**.
- **`APRI-QUESTO.html`** è la via d'uscita che non dipende da questa app: apre i `.gpg` **offline**,
  con OpenPGP.js **incorporato dentro** (nessuna `fetch`, nessun CDN — si può staccare la rete e
  controllare). Si genera con `scripts/build-apri-questo.py` da `vendor/openpgp.min.js`
  e va **rigenerato quando la libreria si aggiorna**.
- **Il collaudo del primo giorno**: appena creato, la pagina fa riscrivere le 24 parole e le prova
  **come le proverebbe un recupero vero** (aprendo `indice.gpg`), non confrontandole con quelle che
  ha in memoria — un collaudo che si confronta con sé stesso passerebbe sempre. Sapere che il
  recupero funziona costa un minuto oggi; scoprire che non funziona costa il forziere fra dieci
  anni. ⚠️ Quando le parole non aprono, la pagina **non dice quale** è sbagliata — non può, o
  vedrebbe le parole — ma **dice che è successo**, ed è tutta la differenza fra un errore di
  battitura e credere di aver perso tutto.
- ⚠️ **Una riga di `frz_files` che non si decifra non fa saltare l'elenco**: si mostra e si dice
  che c'è. Sparire sarebbe il modo peggiore di segnalare un problema — un file in meno e nessuno
  che spieghi perché. Stessa regola per una miniatura illeggibile.
- ⚠️ **Cancellando, prima il file su Drive e poi la riga.** Al contrario, una rete che cade
  lascerebbe su Drive un file cifrato che nessuno sa più cos'è e che dall'app non si può più
  togliere. È la stessa scelta delle foto di Memo.
- ⚠️ **Il numero della bolla è un conteggio di file e non un punteggio**: sta in `APP_SENZA_PUNTI`
  (`index.html`), `AppSenzaPunti` (`PortedApps.kt`) e `SENZA_PUNTI` (`scripts/backup-report.mjs`).
- **Non esiste in nativo** (fase 1 solo web). La biometria sul telefono è la fase 2: la chiave
  maestra avvolta nel Keystore di Android, così le 24 parole si scrivono una volta sola.

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
- ⚠️ **Da agosto 2026 Spese Ada è allineata a `spese-personali.html`**: import da Excel/CSV,
  riconoscimento dei doppioni fra le due fonti, pagina 🧠 **Attribuzione**, jolly `%`, popup della
  regola quando si associa, «tutti gli anni», assegnazione in blocco e zona pericolosa **sono le
  stesse e vanno lette nella sezione di Spese Personali qui sotto** — il codice è lo stesso a meno
  dei nomi delle tabelle. Le differenze che restano sono tre: qui si archiviano **le sole spese**
  (le entrate entrano solo spuntando *Mostra anche le entrate*, e la casella vale sia per l'import
  dal conto sia per quello da file), il **filtro per carta non lascia passare i movimenti senza
  carta** (là sono le entrate che servono al saldo, qui si cerca una carta e basta), e non c'è
  nessun saldo del periodo.
- Le regole si scrivono associando una categoria a un movimento, e da lì in poi valgono in tre
  momenti — sulle righe proposte in anteprima, sugli altri movimenti già in archivio
  (`applyLearnedMerchants`) e col pulsante *✨ Applica i negozi imparati*.
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
- Il confronto è **«la descrizione contiene la chiave»** (con `%` come jolly, vedi Spese
  Personali), non un'uguaglianza, e la chiave si ricava da **quello che UniCredit scrive dopo
  l'importo**, che è l'esercente:
  `PAGAMENTO POS … del 15/07/2026 CARTA 31342819 DI EUR 1,50 SAN DONATO ALIMENTARI. BOLOGNA`
  → chiave `SAN DONATO ALIMENTARI. BOLOGNA` (`POS_MERCHANT_RE` in `merchantText()`). Tutto quello
  che precede l'importo — modalità di pagamento, data, numero di carta, importo — cambia a ogni
  movimento. Se il pezzo non c'è (addebiti, bonifici, altre banche) si tiene la descrizione
  intera e la regola si **accorcia a mano** da Categorie → Negozi imparati, dove il campo mostra
  in tempo reale quanti movimenti aggancerebbe.
  Fra più regole che agganciano **vince la più pesante**, cioè la più specifica (i caratteri al
  netto dei jolly): senza quella precedenza una regola corta imparata prima si prenderebbe i
  movimenti di una più specifica.
- La stessa causale porta il numero di carta (`CARTA 31342819`): `cardFromDescription()` lo usa
  come ripiego quando l'API non espone la carta nei campi strutturati, altrimenti il filtro per
  carta — l'unico modo per isolare le spese di Ada su un conto condiviso — resterebbe vuoto.
- L'import da file (📊 Importa da Excel) è arrivato con la v1.2.0: la banca via PSD2 espone solo
  gli ultimi mesi, e lo storico più vecchio sta solo nell'estratto conto scaricato dal sito. Da lì
  discende `ada_merchant_map.source` (`20260818120000_ada_merchant_map_source.sql`): **due elenchi
  di regole separati**, perché le due fonti scrivono la stessa spesa in modo diverso.
- **Nessuna categorizzazione da MCC**: `enable-banking-transactions` non restituisce il
  `merchant_category_code` (lo estrae solo `enable-banking-sync`, per Spese Famiglia). Qui
  l'automatismo è tutto nei negozi imparati.

### `spese-personali.html` — Spese Personali
- Il conto personale di Salvatore: import dal conto, categorie a due livelli, negozi imparati e
  dashboard. Si apre dal collegamento *💳 Spese Personali* nella sidebar di `finanza.html`
  (sezione **Salvatore**).
- **Gemello di `spese-ada.html`**, che dall'agosto 2026 ha le stesse funzioni (import da file,
  Attribuzione, jolly, popup della regola, «tutti gli anni», assegnazione in blocco, zona
  pericolosa) — ⚠️ **se modifichi una delle due, guarda anche l'altra**. Restano diverse per tre
  cose:
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
     falserebbe il totale.
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
- ⚠️ **Le regole di attribuzione sono due elenchi separati, uno per fonte, e non si parlano**
  (`20260817160000_sal_merchant_map_source.sql`): un movimento consulta solo le regole della
  propria `sal_transactions.import_source`. È la conseguenza diretta del punto qui sopra — le due
  fonti scrivono la stessa spesa in modo diverso — quindi una chiave imparata di qua non vale di
  là, e tenerle insieme voleva dire che ogni correzione fatta da una parte sballava l'altra. Lo
  stesso negozio si insegna **due volte, una per elenco**: sono due scritture, non un doppione.
  `merchantRule`, `merchantCategory`, `merchantMatchCount`, `learnMerchant` e `saveMerchantRule`
  vogliono tutte la fonte, e `applyLearnedMerchants` confronta ogni movimento con l'elenco della
  **sua**. In Impostazioni le due cancellazioni sono **indipendenti**, una per elenco
  (`source=eq.<fonte>` nella DELETE): rifare le regole da Excel non deve far ricominciare da capo
  anche quelle del conto. Le regole nate prima della colonna sono `'bank'`, che è quello che sono davvero: non
  sono state copiate nel set `'excel'` di proposito, perché contengono pezzi di testo che in un
  foglio non compaiono e riempirebbero l'elenco nuovo di regole inerti.
- In **Categorie** il nome di una voce (e di una super-categoria) è **cliccabile** e apre la
  statistica nel tempo: KPI, *per anno* e *per mese-anno*, ciascuno con grafico e tabella. I due
  tagli sono quelli — **non un taglio per «mese» qualunque**: gennaio 2024 e gennaio 2026 sommati
  sarebbero lo stesso errore delle dodici colonne della dashboard con «tutti gli anni». I mesi
  senza movimenti non compaiono (una fila di zeri direbbe solo che il calendario ha dodici mesi) e
  la *media al mese* divide per i soli mesi in cui la voce è comparsa. ⚠️ Una voce di **sole
  entrate** (uno stipendio, un rimborso) grafica le entrate invece della spesa, con l'etichetta
  che lo dice: disegnarne la spesa vorrebbe dire una fila di zeri chiamata statistica.
  Il blocco `openStatCategoria` è **identico nelle due app** — gli importi si leggono con due
  helper locali invece che con quelli della pagina, che lì hanno nomi diversi (`uscitaDi`/
  `entrataDi` contro `spesaDi`): se lo modifichi in una, riportalo nell'altra.
- ⚠️ **La regola si scrive quando si associa, in un popup, e non più di nascosto.** Assegnare una
  categoria a un movimento apre `openRegolaDaMovimento`, che mostra la descrizione per intero,
  propone la chiave (la descrizione normalizzata) e la lascia **accorciare col conteggio dal
  vivo** di quanti movimenti aggancerebbe; *Solo questo movimento* non scrive niente.
  `setTxCategory` ora **assegna e basta**. Prima imparava da sé con la descrizione intera, che è
  esattamente la regola che non aggancerà mai più niente — la banca infila in ogni acquisto un
  riferimento diverso — e la si scopriva inerte settimane dopo, andandola a cercare. Il popup non
  si apre quando una regola manda già quella descrizione in quella categoria (non c'è niente da
  imparare) né quando si toglie la categoria. ⚠️ **Nell'anteprima di import resta l'apprendimento
  silenzioso** a conferma (`confirmImport`): lì si categorizza a blocchi e una finestra per riga
  sarebbe inservibile.
- ⚠️ **`%` è il jolly della chiave**, come nel `LIKE` di SQL: `AMAZON%IT` prende *AMAZON.IT*,
  *AMAZON EU IT* e *AMAZON DE ... IT* con una regola sola (`matcherRegola`, con cache per chiave —
  `merchantRule` gira su ogni movimento per ogni regola). Il confronto di base resta «contiene»,
  quindi i `%` agli estremi non aggiungono niente. **Il jolly non è `*`**: l'asterisco compare per
  davvero nelle causali (`AMAZON.IT *MK7HG2LO3`, `ESSELUNGA *12345`) e prenderlo come jolly
  spaccherebbe di colpo ogni regola che ce l'ha dentro sul serio; `%` in una causale non si vede
  mai. Ne discende che **la precedenza non è più la lunghezza ma il peso** (`pesoRegola`: i
  caratteri al netto dei jolly), o `%A%` scavalcherebbe `ABC` pur non chiedendo quasi niente — per
  una chiave senza jolly peso e lunghezza coincidono, quindi le regole vecchie si ordinano come
  prima. La soglia minima di 3 caratteri è sul peso.
- La pagina **🧠 Attribuzione** (`renderRegole`) è dove le regole si leggono e si tarano: la
  spiegazione in sei passi di come si sceglie la categoria, un **banco di prova** che su una
  descrizione incollata mostra chiave, regole che agganciano e quale vince senza toccare niente, e
  l'elenco delle regole con quello che ciascuna fa davvero all'archivio. I due elenchi si aprono
  con le linguette 🏦/📊 (`S.ruleSource`), e **tutto quello che c'è nella pagina segue la linguetta
  aperta**: conteggi, banco di prova, filtri e la regola creata con *+ Nuova regola*. Una regola
  però **non cambia mai elenco**, nemmeno modificandola: una chiave scritta per una fonte, messa a
  valere su descrizioni fatte in un altro modo, è una regola che non aggancia più niente. Due colonne che non vanno
  confuse: **aggancia** sono i movimenti la cui descrizione contiene la chiave, **vince** quelli
  che la regola si prende davvero — una regola che aggancia e non vince mai è *coperta* da una più
  specifica, e senza la distinzione sembrerebbe funzionante. **In conflitto** sono i movimenti che
  la regola vince ma a cui è stata data un'altra categoria a mano: restano come sono — le regole
  non sovrascrivono mai — e ⚖️ li riallinea **solo su richiesta esplicita**, con la conferma che
  dice quanti e verso quale voce.
- Nei **Movimenti** l'anno è un **filtro a sé** (`S.filters.anno`) e non `S.selectedYear` della
  dashboard: i due sono indipendenti di proposito, così si guarda tutto l'archivio nell'elenco
  tenendo la dashboard sull'anno in corso. Tutt'e due ammettono **«tutti gli anni»** (`''`;
  `null` = mai impostato, e allora si parte dall'anno più recente).
- ⚠️ Con «tutti gli anni» la dashboard **cambia grafico**: *Andamento mensile* diventa *Andamento
  per anno*, una colonna per annata invece di dodici mesi. Le dodici colonne sommerebbero il luglio
  2024 e il luglio 2026 nella stessa barra — un numero che non vuol dire niente e che a guardarlo
  sembra vero. Il resto (KPI, ciambelle, tabelle) attraversa gli anni senza bisogno di niente, e
  *uscita media mensile* resta giusta perché `mesiConDati` conta i mesi distinti, non dodici.
- **🏷️ Assegna i N senza categoria** nei Movimenti dà la stessa categoria a tutti i movimenti
  senza categoria **fra quelli che i filtri stanno mostrando** — il filtro è la selezione. ⚠️ **Non
  è una regola e non ne scrive nessuna**: tocca quelle righe una volta sola, e i movimenti futuri
  degli stessi negozi continueranno ad arrivare senza categoria. Sistemare un arretrato e insegnare
  un negozio sono due operazioni diverse, e la seconda passa dal popup di `openRegolaDaMovimento`.
  La scrittura va in blocchi da 100 (`patchCategorie`, usata anche da `applyLearnedMerchants`): gli
  id sono uuid da 36 caratteri e PostgREST li vuole nell'URL con `in.(…)`, che oltre qualche
  centinaio di righe sfonda la lunghezza massima e fa fallire tutto il giro.
- La **zona pericolosa** in Impostazioni ha **tre comandi distinti**, che passano dalla stessa
  finestra di conferma (`openCancellazione`): *cancella tutti i movimenti* — categorie,
  super-categorie e regole restano — e **una cancellazione per elenco di regole**, indipendenti
  fra loro, che invece non toccano i movimenti: le categorie già assegnate restano dove sono,
  perché le regole valgono sui movimenti che una categoria non ce l'hanno. Si fa scrivere
  `CANCELLA` invece di un `confirm()` con l'OK a portata di clic: di qui non si torna indietro, e
  la banca ripropone solo il periodo che espone ancora.
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

### 📈 Portafoglio Conto Risparmio in `situazione-teresa.html` — logica portata da Finanza

La voce 📈 **Portafoglio** mostra a Teresa il portafoglio **CONTO RISPARMIO** di Finanza — valore,
liquidità, investito, P&L, le posizioni titolo per titolo e le quote del fondo collegato — con un
selettore `👥 Tutto · Teresa · Salvatore` che rifà ogni importo sulla quota scelta.

⚠️ **Le percentuali sono le QUOTE DEL FONDO, non `fnz_portfolios.ownership_percentage`**: quel
campo lo riscrive `syncFundOwnership()` con la sola quota del partecipante di riferimento, e
usarlo qui la conterebbe due volte. «Tutto» è quindi il portafoglio **intero**, che in Finanza non
si legge da nessuna parte — lì il valore è già in quota.

⚠️ **Quantità e prezzi restano interi, solo gli importi vanno in quota** — è la stessa scelta di
`renderPortafoglioDetail()` in `finanza.html`: il prezzo di un titolo è quello che è, e mostrarne
il 55 % sarebbe un numero che non esiste da nessuna parte.

⚠️ **Terza copia della logica del portafoglio** (dopo `finanza.html` e la Edge Function
`save-snapshot`): `pfHoldings` è `computeHoldings`, `pfNetSpent`/`pfCash` sono
`portfolioNetSpent`/`computePortfolioCash`, `pfQuote` è `computeFundShares`. Cambiando una regola
di là va cambiata anche qui, o le due pagine diranno due numeri diversi sullo stesso conto. È la
stessa duplicazione voluta della vista Spese Famiglia, un blocco più in su.

⚠️ **I prezzi si leggono da `fnz_price_cache` e non da `fnz_price_history`**: è una riga per
simbolo e porta già la chiusura precedente, quindi dice le stesse cifre di Finanza (dove
`computePricesFromHistory` ricava lo storico da quella stessa cache, via trigger) senza scaricare
mesi di quotazioni su una pagina che le userebbe per una riga sola.

⚠️ **Niente colonna delle tasse**: sarebbe una terza copia delle aliquote, e qui nessuno la chiede.

⚠️ **La RLS è ristretta al solo Conto Risparmio**
(`20260905120000_guest_teresa_portafoglio_conto_risparmio.sql`): il perno è
`teresa_cr_portfolios()`, che torna i portafogli con `name ILIKE '%conto risparmio%'`;
da lì discendono movimenti, prodotti, simboli dei prezzi, il fondo collegato e i suoi versamenti.
`fnz_foi_index` si legge intera perché è l'indice ISTAT, un dato pubblico. ⚠️ **Rinominando quel
portafoglio senza la parola «risparmio» la sezione si svuota senza nessun errore**: non è un
difetto della pagina, è il filtro che non aggancia più.

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
  ⚠️ **Sono facoltative**, e la stecca si salva senza. Il tasto *Aggiungi* è un `btn-ghost`
  **spento finché non c'è una data** e non un `btn-primary` che a data vuota rispondeva
  `alert('Errore: scegli la data della giornata chiave.')`: sta a due righe dal *Salva* ed era
  identico a lui, quindi premuto per sbaglio al posto di quello quell'errore si leggeva come
  «non mi fa salvare se non metto una giornata chiave».
  ⚠️ **Una giornata chiave fuori dal periodo si salva e poi NON C'È**: la griglia disegna i
  giorni di `giorniDelPeriodo()`, e quel giorno lì dentro non compare — non si può spuntare e i
  fuochi non partiranno mai, senza che niente lo dica. `perchePuoNonEsserci()` la rifiuta
  all'aggiunta, spiegando quale dei due motivi è (fuori dall'intervallo, oppure un sabato o una
  domenica su una stecca che salta i fine settimana: la conseguenza è la stessa), e
  `inKeyDate` porta `min`/`max` così il calendarietto si apre già sul periodo. Il periodo si
  legge dai **campi del form** e non da `S`: lì si sta ancora scegliendo, e `S` porta quello di
  prima. Il periodo però si può accorciare **dopo**, e allora una chiave già salvata resta fuori:
  nell'elenco delle Impostazioni quella riga si marca in rosso invece di sparire in silenzio.
  ⚠️ **`.btn:disabled` non aveva nessuno stile**: un pulsante spento era identico a uno acceso —
  si premeva, non succedeva niente, e si leggeva come un tocco andato a vuoto. Riguardava anche
  *Chiudi la stecca* e *Cancella il traguardo*.
- **Memoria delle stecche** (`sp_stecche`, migration `20260810150000_sp_stecche.sql`): una stecca
  finita non sparisce più. Quando non c'è più niente da spuntare — tutti i giorni fatti *oppure*
  l'ultimo giorno passato — il pulsante della hero diventa *🏁 Chiudi la stecca* e parte una
  cerimonia in tre passi: **l'ultima spunta** (un bersaglio grosso che si preme una volta sola e
  lascia la sua emoji come sigillo), la **barra della soddisfazione da 1 a 100**, la **nota**.
  Solo allora la stecca finisce in archivio e il messaggio finale — pescato per fascia da
  `msgChiusura` dell'umore, dal consolatorio (`.toast.dolce`) al complimento vivo — arriva con i
  fuochi d'artificio di `fuochiFinali()`, da 8 a 24 scoppi più il gran finale a seconda di quanto
  si è soddisfatti. Le stecche chiuse si rileggono nella card *🏅 Le stecche chiuse*.
- **L'umore della stecca** (`sp_settings.mood`, prima scelta nelle Impostazioni): ⏳ **Attesa** o
  🌅 **Bei giorni**. La griglia, le spunte, i traguardi e la chiusura restano identici — cambia
  **con che voce l'app li commenta**, perché il tempo che passa avvicina qualcosa oppure lo porta
  via, e sono due letture opposte dello stesso numero. Seguono l'umore: le 30 frasi della spunta,
  le emoji che restano sul giorno, l'etichetta sotto il numero grande (*giorni che mancano* /
  *giorni che restano*), il badge in cima alla card, le due etichette del form (*Cosa stai
  aspettando?* / *Cosa stai vivendo?*), il banner di oggi, i quattro traguardi 25/50/75/100 % e i
  sei messaggi di chiusura.
  ⚠️ **Tutto passa da `MOODS`, una tabella di testi e non due rami di codice**: nessuna delle due
  voci è scritta a mano da un'altra parte, e un terzo umore sarebbe una voce lì dentro. `mood()`
  legge quello della stecca in corso; `moodValido()` riporta ad `'attesa'` un valore che non
  conosce — ed è per questo che **l'elenco dei due id è scritto dentro quella funzione** e non in
  una costante accanto a `MOODS`: la chiama anche l'inizializzazione di `S`, che gira prima che
  quelle `var` siano assegnate.
  ⚠️ **Al 100 % i fuochi d'artificio dipendono dall'umore** (`fuochiAl100`): su una stecca di bei
  giorni l'ultimo giorno è un addio, e festeggiarlo coi fuochi suonerebbe come una presa in giro —
  i coriandoli restano, che sono un saluto.
  ⚠️ **`festeggiaChiusura(sodd, voce)` riceve l'umore come parametro e non da `mood()`**: gira
  dopo che la stecca è stata archiviata, quando `S` descrive già il campo libero, e salutare dei
  bei giorni con la voce dell'attesa sarebbe il modo peggiore di finirli.
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
  dalla migration `sp_`) conta i **giorni che mancano**, quindi la bolla nel launcher è proporzionata.
  ⚠️ Quel numero **dimensiona la bolla ma non si scrive più sotto il nome e non fa punti**: è un
  conteggio, ed è la voce capofila di `APP_SENZA_PUNTI` — vedi *AppSphere nativa → Non tutti i
  numeri di `score_query` sono punti*

### `sos.html` — SOS
- **Non è l'app: è la sua configurazione.** Il SOS si preme sul telefono
  (`android-app/sos/`); qui si decide che cosa dice, quanto dura e quanto vale.
- Tre schede: **🆘 I miei SOS** (creazione e modifica), **📱 Telefoni** (i codici di
  accoppiamento dell'APK), **📊 Storico** (KPI e l'elenco dei giri).
- Un SOS ha un nome, che cosa si sta fronteggiando, una durata **di partenza** e due estremi.
  Nella stessa finestra si scrivono le **risposte** alla domanda finale — emoji, testo, punti,
  percentuale — e le **frasi** che scorrono sotto il countdown, una per riga.
- ⚠️ **Modificare un SOS non tocca `current_seconds`**: è dove l'hanno portato gli esiti, e
  riscriverlo dal form butterebbe via la storia dei giri fatti. Si riporta alla partenza col
  pulsante *↺ Riporta a N min*, che lo dice e chiede conferma. Se però il nuovo minimo/massimo
  esclude il valore corrente, quello viene riportato dentro gli estremi — altrimenti il CHECK
  della tabella rifiuterebbe il salvataggio.
- Le durate si scrivono **in minuti interi** e si archiviano in secondi: mm:ss in un form si
  sbaglia, e i secondi servono solo perché le percentuali li producono (−10 % di 10 minuti = 9:00).
- Il **codice del telefono si vede per intero una volta sola**, alla creazione. Dopo restano le
  ultime quattro lettere: è quanto basta per riconoscere *quale* codice è, che è la sola domanda
  a cui serve rispondere guardando l'elenco. Un telefono perso si chiude revocando il codice.
- Nello **Storico ogni giro si cancella** (🗑 in fondo alla riga, `eliminaGiro`). ⚠️ Non è pulizia
  di schermo: la `score_query` di SOS somma i punti dei giri chiusi, quindi la riga che sparisce
  si porta via i suoi punti dalla bolla in home e dal totale che paga i premi — la conferma lo
  dice con la cifra davanti. ⚠️ **La durata del prossimo giro non torna indietro**: la percentuale
  della risposta è stata applicata a `sos_types.current_seconds` quando il giro si è chiuso, e
  quel valore è dove l'hanno portato *tutti* i giri, non solo quello. Rifarne il conto
  all'indietro vorrebbe dire riapplicare in ordine tutta la storia; il tempo si riporta alla
  partenza col pulsante *↺ Riporta a N min*, che esiste apposta. La cancellazione è per riga: si
  vedono (e si cancellano) i soli giri **chiusi**, non le sessioni rimaste aperte per una app
  chiusa a metà countdown.
- ⚠️ **Il numero di SOS è un punteggio vero**, non un conteggio: sta fuori da `APP_SENZA_PUNTI`,
  quindi si scrive nella bolla e fa parte del totale (vedi *AppSphere nativa → Non tutti i numeri
  di `score_query` sono punti*). È la ragione per cui cancellare un giro ha un prezzo.

---

## Backup settimanale — il dump e la relazione

`.github/workflows/backup.yml` gira **ogni domenica** (cron `0 2 * * 0`, cioè le 04:00 in ora
legale) e si può lanciare a mano da Actions. Fa due cose diverse:

| Cosa | Come | A che serve |
|---|---|---|
| **Dump** | `supabase db dump` in tre file gzippati: `schema.sql.gz`, `dati.sql.gz`, `ruoli.sql.gz` | Rimettere in piedi il database |
| **Relazione** | `scripts/backup-report.mjs` → `relazione.html` | Sapere **cosa c'era dentro** quella settimana |

### ⚠️ Nel repository non resta niente, e la ragione è che è PUBBLICO

`garsal1971/garsal-apps` è un repository **pubblico**. Patrimonio, spese, reddito e task non ci
vanno **né su master** — dove per giunta `netlify.toml` pubblica la radice (`publish = "."`),
quindi ogni file è anche scaricabile dal sito, e un redirect non lo impedisce perché un file
fisico ha la precedenza — **né su un ramo a parte**, che chiunque apra la pagina del repo legge
lo stesso. Un ramo `backups` era stato scritto e ritirato prima di girare una sola volta: è la
soluzione che sembra prudente e non lo è.

Dump e relazione salgono quindi su **Google Drive** (`scripts/backup-drive.py`), in una cartella
dell'account di Salvatore. ⚠️ **Senza i segreti di Drive il job si ferma e non c'è nessun
ripiego**: lasciarli «per intanto» nel repo sarebbe esattamente la cosa che questo passo esiste
per impedire.

⚠️ **Un refresh token, non un account di servizio.** Un service account non ha spazio proprio su
Drive: caricando in una cartella condivisa da un account Google personale il file resterebbe di
sua proprietà e la richiesta fallisce con `storageQuotaExceeded`. Funziona solo su un Drive
condiviso, che è roba di Workspace. Col refresh token dell'account personale i file nascono
**suoi**, nel suo spazio. È la stessa scelta già fatta per `YT_OAUTH_TOKEN`.

⚠️ **I nomi sono piatti** (`2026-09-06-relazione.html`), non una cartella per data: la data si
legge dal nome, l'elenco arriva già ordinato e la rotazione è un confronto di stringhe. Si
tengono le ultime 12 **date** (`QUANTE_TENERE`) — le date e non i file, o un giro che ha
prodotto un file solo farebbe sparire una fotografia intera.

⚠️ **Un dump fallito non porta giù la relazione.** Il passo del dump non fa morire il job: segna
`DUMP_ESITO=ko`, la relazione si scrive lo stesso, su Drive sale quel che c'è, e il verdetto
arriva nell'ultimo passo — così il run è rosso *e* i dati leggibili ci sono comunque.

### Come si legge: `relazione.html` + `backup-drive`

`relazione.html` (voce **🗄️ Backup** nel ☰ di AppSphere) elenca le fotografie e apre la relazione
scelta. Non parla con Drive: passa dalla Edge Function **`backup-drive`**.

⚠️ **La pagina non può parlare con Drive da sé**, e non è una complicazione gratuita: servirebbe
un token Google con lo scope `drive.readonly` — il permesso di leggere **tutto** il Drive di
Salvatore, chiesto al login di ogni app — per arrivare a una cartella sola. Con la Edge Function
la credenziale sta nei Secrets e la pagina presenta il suo JWT Supabase.

⚠️ **Il JWT si verifica contro Supabase** (`/auth/v1/user`), non si decodifica e basta: un JWT si
scrive a mano in dieci secondi, e questa funzione apre il patrimonio di famiglia. Passa il solo
`BACKUP_EMAIL` — Teresa, Rosa e Ada hanno un login valido e qui non c'entrano.

⚠️ **Si legge e basta**: nessuna scrittura, nessuna cancellazione. La rotazione la fa il workflow,
che è l'unico posto dove qualcosa si cancella — una funzione raggiungibile dal browser che sa
cancellare i backup è un backup che un giorno non c'è più. E il download controlla che il file
stia **in quella cartella**: senza il controllo sul padre, un id qualsiasi aprirebbe qualunque
file del Drive, cioè proprio quello che si è evitato non chiedendo `drive.readonly`.

⚠️ **Dalla funzione passa solo la relazione, non i dump**: un gzip restituito come testo sarebbe
una stringa rotta e un megabyte di JSON per niente. I dump si scaricano da Drive, e la pagina ci
mette il collegamento. La relazione si apre in un `iframe` con `srcdoc` e `sandbox`: porta il suo
CSS e non si mescola con quello della pagina.

⚠️ **Dentro `srcdoc` ci vuole `<base href="about:srcdoc">`, o l'indice non scorre: naviga.** Un
documento in `srcdoc` non ha un indirizzo proprio e **eredita quello della pagina che lo
contiene**, quindi un `#tasks` dell'indice si risolve in `…/relazione.html#tasks` e l'iframe va
lì — cioè ricarica `relazione.html` dentro sé stessa, dove la sandbox non concede JavaScript:
resta su «Carico…» per sempre, e si legge come una sezione che non finisce di caricare. La base
la scrive `backup-report.mjs` dalla v1.0.2, e `conBase()` in `relazione.html` la aggiunge alle
relazioni **già su Drive**, che non ce l'hanno: senza, una fotografia vecchia resterebbe non
sfogliabile per sempre.

### ⚠️ La relazione NON contiene i riservati

Le schede di Memo, i gruppi di Events Log e i task marcati `riservato` si **contano e non si
elencano**. La modalità nascosta esiste perché quella roba non si legga di sfuggita. Nel **dump**
ci sono per forza — quello è un backup, non una lettura.

⚠️ Il filtro è nella **query** e non nel disegno (`filtroRiservato()`), come in `memo.html` e in
`events-log.html`, e va scritto `or=(riservato.eq.false,riservato.is.null)`: la colonna è arrivata
dopo le righe, quindi le vecchie hanno NULL e con la sola uguaglianza sparirebbero tutte.

⚠️ **`cm_apps.riservato` è un'altra cosa e non filtra niente qui**: quella spunta nasconde la
*bolla* nel launcher, non i dati — Finanza è marcata così — e la relazione di Finanza è
esattamente ciò per cui questa relazione esiste.

### ⚠️ Lo schema si legge, non si indovina

Metà di queste tabelle non sta in nessuna migration (`ts_*`, `hb_*`, `el_*`, `ps_*`,
`cm_categories`, `cm_priorities`, `cm_rewards`…): sono nate a mano in produzione, e i nomi delle
colonne non sono verificabili dal repo. Lo script legge quindi lo **spec OpenAPI di PostgREST**
(`GET /rest/v1/`) e chiede solo colonne che esistono davvero; dove il nome può variare passa da
`campo(riga, 'title', 'name', 'nome')`. Una tabella che non c'è dà una **riga che lo dice** nella
sezione *⚠️ Cosa non è stato letto*, non una relazione che si interrompe a metà.

Per la stessa ragione c'è la sezione **📊 Inventario**, che conta le righe di **ogni** tabella
dello schema: è la rete che impedisce a una tabella nuova di restare invisibile finché qualcuno
non la aggiunge a mano alle sezioni.

⚠️ **Ogni sezione è indipendente**: una che salta non porta giù le altre. Una relazione parziale
che dice cosa manca vale più di nessuna relazione.

### ⚠️ I punti passano da `backup_scores`, non da una seconda formula

`cm_apps.score_query` è SQL scritto in tabella che parla di `auth.uid()`. Il backup gira con la
**service key**: lì `auth.uid()` è NULL — ogni conteggio tornerebbe zero — e `run_score_query`
rifiuta comunque chi non presenta un JWT con l'email di Salvatore.
`backup_scores(p_user)` (`20260904120000_backup_scores.sql`) esegue **la stessa** query di
`cm_apps` sostituendo `auth.uid()` con l'uuid passato: non è un secondo modo di calcolare i punti,
che sarebbero due punteggi diversi il giorno che uno dei due cambia.

⚠️ **EXECUTE al solo `service_role`**: la funzione esegue SQL arbitrario preso da
`cm_apps.score_query`, esattamente come `run_score_query_unrestricted`, e la guardia non è un
controllo dentro la funzione ma il permesso. Da `anon` e `authenticated` non si raggiunge affatto.

⚠️ **`APP_SENZA_PUNTI` è duplicato una terza volta** dentro `backup-report.mjs`, accanto alle due
già note (`index.html` e `home/PortedApps.kt`): se divergono, il totale della relazione non è
quello che la home mostra. Vale la stessa regola — non tutti i numeri di `score_query` sono punti.

### I segreti, e i due posti in cui vanno

| Segreto | GitHub | Supabase | Serve a |
|---|---|---|---|
| `SUPABASE_ACCESS_TOKEN` | ✔ | — | `supabase db dump` (già usato da `deploy.yml`) |
| `SUPABASE_SERVICE_KEY` | ✔ | — | Leggere i dati per la relazione (già usato da `ytp-download-audio.yml`) |
| `GDRIVE_CLIENT_ID` | ✔ | ✔ | Client OAuth di Google Cloud |
| `GDRIVE_CLIENT_SECRET` | ✔ | ✔ | idem |
| `GDRIVE_REFRESH_TOKEN` | ✔ | ✔ | Caricare e leggere **come Salvatore** |
| `GDRIVE_FOLDER_ID` | ✔ | ✔ | La cartella dei backup (dall'indirizzo di Drive) |

⚠️ **Gli stessi quattro valori vanno in tutt'e due i posti**: il workflow carica, la Edge Function
legge. Se divergono, i backup si scrivono in una cartella e la pagina ne guarda un'altra — e non
lo dice nessuno, perché l'elenco torna semplicemente vuoto.

Il refresh token si ottiene **una volta sola** con `scripts/drive-refresh-token.py`, da
eseguire sul proprio PC: chiede id e segreto del client *Applicazione desktop*, apre il browser
per il consenso e stampa il token, dopo averlo **provato**. Non scrive niente da nessuna parte.

⚠️ **L'app va portata in Produzione** (Google Auth Platform → Pubblico → *Pubblica app*): in
stato «Test» Google fa scadere i refresh token dopo **7 giorni**, e il backup fallirebbe la
seconda domenica con `invalid_grant` senza che il codice c'entri niente.

⚠️ `drive.file` basta e avanza — dà accesso ai **soli file creati da quel client**, cioè i backup,
e non a tutto il Drive; ed è non sensibile, quindi pubblicare non chiama in causa la verifica di
Google, che `drive.readonly` invece richiederebbe.

⚠️ Nello script `access_type=offline` **e** `prompt=consent` ci sono tutt'e due: senza il primo
Google non manda nessun refresh token, senza il secondo non lo manda **dalla seconda volta in
poi** — e si guarda una risposta che sembra riuscita e non ha il campo che serve. Il redirect è
un **loopback su porta a caso**, che per un client desktop non va registrato: il vecchio
`urn:ietf:wg:oauth:2.0:oob` è dismesso e risponde 400.

Nessuna password del database: come in `deploy.yml`, la CLI si crea da sola un ruolo di login
temporaneo, e **non si usa `supabase link`** — chiama `/v1/projects/{ref}/api-keys`, che dal
7 agosto 2026 risponde con un errore del suo stesso schema e porterebbe giù tutto il job. I due
file che `link` metteva in `supabase/.temp` si scrivono a mano.

`backup.sh` e `backup.ps1` restano quello che erano: il dump **a mano** dal proprio PC, che chiede
la password. Non c'entrano con questo giro e non vanno tenuti allineati.

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

⚠️ **Il push su master si riprova, perché può essere rifiutato senza nessun conflitto.** Dallo
stesso push partono anche le due build APK, che a lavoro finito committano il pacchetto **su
master**: su un commit che tocca insieme le pagine e il codice Android arrivano mentre il deploy
è ancora al checkout, e il suo push trova un master più avanti — `! [rejected] (fetch first)`.
È successo il 24 agosto 2026 (run #1400): il merge era riuscito, il push no, e **tutti i passi
successivi sono stati saltati**, migration comprese — il codice non era in produzione e l'unico
posto dove si vedeva era Actions. Il passo *Merge branch into master and push* rilegge quindi
master e rifà il merge fino a cinque volte. **Un conflitto vero non si riprova**: `git merge`
esce diverso da zero, e con `bash -e` il passo muore lì — un conflitto lo risolve una persona.

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

### ⚠️ Il tasto «indietro» di Android dentro un popup

Su Android l'indietro è il gesto con cui si chiude qualunque cosa si sia aperta. In una pagina web
che non fa niente per governarlo, dentro un popup **esce dalla pagina**: la WebView non ha niente
in cronologia e torna ad AppSphere, buttando via quel che si stava scrivendo.

Il rimedio è il blocco `guardiaIndietroPopup`, **identico in tutte e dieci le app** che hanno dei
popup (`index`, `calorie`, `finanza`, `obiettivi`, `casarosa`, `conto-risparmio-teresa`,
`conto-spese-teresa`, `spese-ada`, `spese-personali`, `youtube-player`) — in fondo al loro script,
e l'unica cosa che cambia è l'elenco dei popup. ⚠️ **Se lo correggi in una, portalo nelle altre**:
è la stessa duplicazione voluta dello snapshot del patrimonio e della vista Spese Famiglia.

Quattro cose che *sono* il funzionamento:

- ⚠️ **L'apertura non si intercetta avvolgendo le funzioni che aprono i popup.** In queste pagine
  sono decine, alcune sono `onclick` scritti nell'HTML e altre non hanno nemmeno un nome: si
  osserva invece quando l'**overlay diventa visibile** (`MutationObserver` su `class` e `style`),
  che è l'unica cosa che tutti i popup hanno in comune comunque siano stati aperti.
- ⚠️ **Visibile si decide dallo stile calcolato**, non dal nome della classe: nelle varie pagine è
  ora `hidden`, ora `active`, ora `open`. Tutte nascondono con `display:none`, quindi
  `getComputedStyle(el).display !== 'none'` è il test che vale ovunque. Nell'elenco va però
  l'**overlay** e non la scheda interna: in `casarosa` e `conto-risparmio-teresa` la classe
  `.modal` è la scheda dentro l'overlay, e lo stile calcolato di un figlio non sa che il padre è
  nascosto — un `.modal` messo lì risulterebbe sempre visibile. In `obiettivi` e
  `youtube-player`, invece, `.modal` *è* l'overlay ed è giusto usarla.
- ⚠️ **Anche il ✕ consuma la voce di cronologia**, altrimenti ogni apri-e-chiudi ne lascerebbe una
  dietro di sé e dopo cinque popup servirebbero cinque indietro per uscire dalla pagina. Il
  consumo controlla prima `history.state`: se nel frattempo la pagina ha spinto una voce sua (un
  cambio di vista) la nostra non è più in cima, e un `history.back()` cieco tornerebbe indietro di
  una schermata invece di togliere la voce del popup.
- ⚠️ **La voce si spinge una volta sola** anche con più popup aperti uno sopra l'altro (dalla
  ricerca alla porzione, dall'elenco al form): sono contenuti nella stessa finestra, non
  schermate, e due voci vorrebbero dire due indietro per chiudere una cosa sola. L'indietro
  chiude l'ultimo aperto.

⚠️ **In `calorie.html` da un popup si esce solo col ✕, con un pulsante o con l'indietro** — il
tocco sul velo scuro e l'Escape non chiudono più (v1.14.0). Su un telefono la scheda occupa quasi
tutto lo schermo, il velo è la striscia ai bordi, e il dito ci finisce sopra mentre si scorre un
elenco o si mira a un campo in alto: il popup spariva portandosi via i grammi appena scritti, e
sembrava un tocco andato a vuoto più che una chiusura. Il ✕ sta nell'HTML della finestra e non
dipende da cosa ci si mette dentro, quindi togliendo quelle due vie non si resta mai chiusi
dentro. ⚠️ **Le altre app chiudono ancora col tocco sul velo**: chi le tocca valuti se portare
anche questo.

Restano **fuori dall'elenco** il velo di caricamento e il cassetto del menù (`loading-overlay`,
`mobile-nav-overlay`), più il fumetto degli avvisi di `index.html`: sono tendine e non finestre.
Le quattro pagine che avevano già un `popstate` (`conto-spese-teresa`, `finanza`, `spese-ada`,
`spese-personali`) lo governava per le **sole viste** e continua a farlo: la guardia esce subito
quando non c'è nessun popup aperto, così l'indietro fa quel che ha sempre fatto.

### ⚠️ ✏️ e 🗑 stanno a SINISTRA del record

In un elenco le icone di modifica ed eliminazione vanno **in testa alla riga**, mai in coda. In
una tabella che scorre di lato — cioè ogni tabella su un telefono — l'ultima colonna sta oltre il
bordo destro: i pulsanti esistono, ma per raggiungerli bisogna già sapere che c'è dell'altro da
trascinare. La prima colonna è l'unica che si vede sempre, comunque sia largo lo schermo.

Ordine dentro il gruppo: **✏️ prima, 🗑 dopo** — la più usata per prima, e la distruttiva non sul
bordo, dov'è più facile prenderla di striscio.

Applicato in `calorie.html` (la tabella 🍎 Alimenti e le righe del 📓 Diario). ⚠️ **Le altre app
hanno ancora le icone in coda**: chi le tocca le sposti.

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
8b. **Il portafoglio ha una terza copia**: `situazione-teresa.html` porta `computeHoldings` / `computePortfolioCash` / `computeFundShares` come `pfHoldings` / `pfCash` / `pfQuote` per la vista 📈 Portafoglio. Cambiarne una in `finanza.html` e non qui fa divergere in silenzio le due pagine — dettagli in *App Details → 📈 Portafoglio Conto Risparmio*.
9. **Snapshot solo all'apertura di Finanza**: `fnz_dashboard_snapshots` viene scritto da `autoSaveSnapshot` quando si apre l'app, e dal job delle 23:00. Chi legge lo snapshot come "valore attuale" durante il giorno ottiene un dato fermo alla notte precedente: per il valore aggiornato bisogna ricalcolarlo sui prezzi correnti.
10. **`APP_SENZA_PUNTI` vive in tre posti**: `index.html`, `home/PortedApps.kt` e `scripts/backup-report.mjs`. Se divergono, home web, home nativa e relazione settimanale mostrano tre totali diversi — dettagli in *Backup settimanale*.
11. **Otto app esistono anche in Kotlin**: Spuntiamola, Obiettivi, Events Log, Ta Firi?, Ti pisasti? (Weight Quest), Memo, Abituati e Calorie hanno un gemello nativo in `android-app/appsphere-native/` che scrive sulle stesse tabelle (Tasks pure, con la sua sezione a parte). Cambiare le regole in uno solo dei due li fa divergere in silenzio — dettagli in *AppSphere nativa*.

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
