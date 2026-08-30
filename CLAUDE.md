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
| `al_foods` | Gli alimenti conosciuti. `source` `'base'` (voci generiche di partenza) \| `'off'` (Open Food Facts, col `barcode`) \| `'usda'` \| `'manuale'`; valori **per 100 g**. `default_grams` è la porzione abituale e `portion_label`/`portion_label_plural` come si chiama |
| `al_log` | Le righe del diario: `day`, `meal`, `grams`, e i valori per 100 g **congelati sulla riga** |
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
| `sp_settings` | Una riga per utente: traguardo, emoji, periodo (`start_date` → `end_date`), `skip_weekend` |
| `sp_checks` | Una riga per giorno spuntato (`UNIQUE (user_id, day)`); `emoji` è quella pescata a caso alla spunta |
| `sp_key_days` | Giornate chiave (`UNIQUE (user_id, day)`); `label` è l'etichetta libera mostrata alla spunta |
| `sp_stecche` | Archivio delle stecche chiuse: traguardo e periodo com'erano, `total_days`/`done_days`, `satisfaction` (1-100), `note`, e la fotografia jsonb di `checks` e `key_days` |

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
| `ob_action_history` | Storico e punti delle azioni. Gemella di `ts_history` |
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

| Cosa | Dove |
|---|---|
| Home a bolle, avvisi, riquadro del totale, login, biometria | `home/`, `MainActivity.kt`, `core/` |
| Catalogo premi (riscossione, gestione, cronologia) | `premi/` |
| App portate | `spuntiamola/`, `eventslog/`, `tasks/`, `tafiri/`, `peso/`, `memo/`, `abituati/` — più `obiettivi/`, **sospesa in home** (riga commentata in `PortedApps.kt`, schermate intatte) |

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
restano le due curve che si guardano davvero — peso e target — perché un grafico fitto di etichette
su uno schermo di telefono è illeggibile prima ancora di essere utile. La spezzata del target è
quella dei **traguardi presi diretti**, non i valori interpolati giorno per giorno che restano
nella tabella: fra un traguardo e l'altro serve la retta vera, non tanti segmenti arrotondati a
due decimali che sembrano seghettati.

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
- **Obiettivi** — il progresso resta in `ob_objective_progress` e la scrittura in
  `ob_record_measurement`, che è anche l'unica a dire se un voto sta dentro la scala. Gli estremi
  di una metrica si leggono da `ob_metric_scale`, ricalcata in `scaleOf()` e in `ObMetrica.scala`:
  se cambia il modo di ricavarli, va cambiato in tutt'e tre. Le due barre non si fondono mai in una
  media.
  ⚠️ **Le azioni esistono solo nel web**: il gemello Kotlin non legge `ob_actions` e non le mostra.
  La sua barra dell'esecuzione le conta lo stesso — il numero arriva dalla RPC — ma le etichette
  sotto la barra parlano ancora dei soli sotto-obiettivi e milestone. Le due chiavi nuove della
  risposta (`actions_total`, `actions_done`) non rompono il decoder perché `ignoreUnknownKeys` è
  attivo nel serializer di supabase-kt. È una divergenza **nota e temporanea**: portare le azioni
  in nativo è il pezzo che manca.
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
- **Memo** — note, liste e diari esistono in tutt'e due. Il contenuto è **HTML**: il nativo lo
  converte in marcatori per modificarlo e lo riconverte salvando (`MemoHtml`), quindi ogni voce
  della barra del web deve avere il suo marcatore di qua. Le altre regole da tenere allineate:
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
- **✅ Azioni** è una voce di menù a sé, oltre alla sezione dentro il dettaglio di ogni obiettivo.
  L'elenco è raggruppato come la panoramica dei task — ⚠️ Scadute, 🎯 Oggi, 📅 Prossime,
  🔄 A libera ripetizione, 🏁 Concluse — con i filtri per obiettivo, tipo, priorità, categoria,
  stato e testo.
- **Quali pulsanti compaiono**: *Completa* sempre, *Salta* solo su ciò che ha una prossima volta a
  cui rimandare (`single`, `recurring`, `simple_recurring`, `multiple`). ⚠️ Un `workflow` mostra
  **Step** al posto di *Completa*: si chiude dai suoi step, e un pulsante che lo chiudesse di forza
  salterebbe quelli ancora aperti.
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
  numero. ⚠️ Lo scarto si scarica **tutto sul giorno dopo e lì si esaurisce**: il secondo giorno
  futuro torna al target del suo tratto. È la regola scelta perché è l'unica che si controlla a
  mente («ieri +300, oggi −300»); spalmarlo su tutti i giorni che restano darebbe un numero che
  nessuno può rifare. Un giorno **senza righe non riporta niente** — non è un digiuno, è un giorno
  non segnato — ed è la stessa regola del saldo e delle colonne mancanti nel grafico.
  ⚠️ **Da OGGI si riporta solo lo sforo, mai il risparmio.** Oggi è l'unico giorno «precedente»
  che può ancora cambiare: alle sette di sera il diario è a metà, e leggere quel che non è ancora
  stato segnato come un risparmio faceva comparire un target di domani gonfiato di ottocento
  calorie che nessuno si era guadagnato — un picco visibile nel grafico, corretto in v1.12.1. Lo
  sforo invece è già successo e non si disfa, quindi quello si riporta subito. È la stessa regola
  del «non lo so ≠ zero» che governa tutta la pagina: un diario a metà non è un digiuno.
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
