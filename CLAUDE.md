# CLAUDE.md — garsal-apps

This file provides context for AI assistants working in this repository.

---

## Project Overview

**garsal-apps** is a collection of personal productivity web applications deployed to Netlify. Each app is a **single self-contained HTML file** with no build step, no package manager, and no external source files. All styling and JavaScript live inline within the HTML.

The suite is branded **AppSphere** and the UI language is **Italian**.

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
├── app-launcher.html    # AppSphere — main entry point / app launcher
├── tasks.html           # Tasks v19.17.12 — task management
├── habit-tracker.html   # Habit Stack Tracker — habit tracking with gamification
├── events-log.html      # Events Log v2.0 — event/activity logging
├── weight-quest.html    # Weight Quest v2.4.1 — weight tracking with charts
└── netlify.toml         # Netlify deployment config (root → app-launcher.html)
```

There is **no** `package.json`, `node_modules`, `build/`, or `dist/` directory. Every app ships as-is.

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
1. `app-launcher.html` handles Google OAuth via Supabase Auth
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

### Weight Quest (`ps_`)
| Table | Purpose |
|---|---|
| `ps_weight_tracking` | Weight measurement entries |

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

## App Details

### `app-launcher.html` — AppSphere
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

### `weight-quest.html` — Weight Quest
- Chart.js weight graph centred on today (30-day window, scrollable)
- Google Fit integration via OAuth token
- Minimal inline Supabase client (no CDN); milestone and objective tracking

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

[[redirects]]
  from = "/"
  to = "/app-launcher.html"
  status = 200
```

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
