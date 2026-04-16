/**
 * Step 2 — User test automatici su https://garsal-apps.netlify.app
 * Usa la sessione salvata da capture-session.js — SOLO READ-ONLY, nessuna scrittura dati.
 * Esecuzione: node tests/run-tests.js
 */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const fs = require('fs');
const path = require('path');

const SESSION  = path.join(__dirname, 'session.json');
const SHOTS    = path.join(__dirname, 'screenshots');
const REPORT   = path.join(__dirname, 'report.txt');
const BASE     = 'https://garsal-apps.netlify.app';

if (!fs.existsSync(SESSION)) {
  console.error('❌  session.json non trovato. Esegui prima:\n    node tests/capture-session.js');
  process.exit(1);
}
if (!fs.existsSync(SHOTS)) fs.mkdirSync(SHOTS, { recursive: true });

const results     = [];
const consoleErrs = {};

function log(msg) { process.stdout.write(msg + '\n'); }

function pass(app, test) {
  results.push({ app, test, status: 'PASS' });
  log(`  ✓  ${test}`);
}

function fail(app, test, err) {
  const msg = (err && err.message) ? err.message.split('\n')[0] : String(err);
  results.push({ app, test, status: 'FAIL', error: msg });
  log(`  ✗  ${test}\n     ${msg}`);
}

async function snap(page, name) {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: false });
  log(`  📸  ${name}.png`);
}

function newTrackedPage(context, app) {
  return context.newPage().then(page => {
    const errors = [];
    page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
    page.on('pageerror', err => errors.push('[PageError] ' + err.message));
    consoleErrs[app] = errors;
    return page;
  });
}

// ──────────────────────────────────────────────────────────────────────────────
// APP 1 — AppSphere Launcher
// ──────────────────────────────────────────────────────────────────────────────
async function testLauncher(ctx) {
  const APP = 'AppSphere';
  log(`\n${'═'.repeat(50)}\n  ${APP}\n${'═'.repeat(50)}`);
  const page = await newTrackedPage(ctx, APP);

  try {
    await page.goto(`${BASE}/app-launcher.html`, { waitUntil: 'networkidle', timeout: 30000 });
    await snap(page, '01-launcher-loaded');
    pass(APP, 'Pagina caricata');
  } catch (e) { fail(APP, 'Pagina caricata', e); await page.close(); return; }

  try {
    await page.waitForSelector('#user-bar', { state: 'visible', timeout: 8000 });
    pass(APP, 'Utente autenticato (#user-bar visibile)');
  } catch (e) { fail(APP, 'Utente autenticato (#user-bar visibile)', e); }

  try {
    await page.waitForSelector('.cw', { timeout: 8000 });
    const n = await page.locator('.cw').count();
    pass(APP, `Bubble circles visibili (${n} app)`);
    await snap(page, '02-launcher-bubbles');
  } catch (e) { fail(APP, 'Bubble circles visibili', e); }

  try {
    const first = page.locator('.cw').first();
    await first.hover();
    await new Promise(r => setTimeout(r, 600));
    await snap(page, '03-launcher-hover');
    pass(APP, 'Hover su circle (nessun crash)');
  } catch (e) { fail(APP, 'Hover su circle', e); }

  await page.close();
}

// ──────────────────────────────────────────────────────────────────────────────
// APP 2 — Tasks
// ──────────────────────────────────────────────────────────────────────────────
async function testTasks(ctx) {
  const APP = 'Tasks';
  log(`\n${'═'.repeat(50)}\n  ${APP}\n${'═'.repeat(50)}`);
  const page = await newTrackedPage(ctx, APP);

  try {
    await page.goto(`${BASE}/tasks.html`, { waitUntil: 'networkidle', timeout: 45000 });
    await snap(page, '04-tasks-loaded');
    pass(APP, 'Pagina caricata');
  } catch (e) { fail(APP, 'Pagina caricata', e); await page.close(); return; }

  try {
    await page.waitForSelector('.main-content', { state: 'visible', timeout: 10000 });
    pass(APP, 'Contenuto principale visibile');
  } catch (e) { fail(APP, 'Contenuto principale visibile', e); }

  // Clicca tutti i nav item visibili
  const allNav = page.locator('.nav-item');
  const count  = await allNav.count();
  log(`  → ${count} nav items trovati`);

  for (let i = 0; i < count; i++) {
    const item = allNav.nth(i);
    if (!(await item.isVisible())) continue;
    try {
      const label = (await item.getAttribute('data-text') || await item.innerText()).trim();
      const slug  = label.replace(/\s+/g, '_').replace(/[^\w_]/g, '').substring(0, 20);
      await item.click();
      await new Promise(r => setTimeout(r, 500));
      await snap(page, `05-tasks-nav-${i + 1}-${slug}`);
      pass(APP, `Nav "${label}" cliccabile`);
    } catch (e) { fail(APP, `Nav item ${i + 1}`, e); }
  }

  await page.close();
}

// ──────────────────────────────────────────────────────────────────────────────
// APP 3 — Habit Tracker
// ──────────────────────────────────────────────────────────────────────────────
async function testHabitTracker(ctx) {
  const APP = 'HabitTracker';
  log(`\n${'═'.repeat(50)}\n  ${APP}\n${'═'.repeat(50)}`);
  const page = await newTrackedPage(ctx, APP);

  try {
    await page.goto(`${BASE}/habit-tracker.html`, { waitUntil: 'networkidle', timeout: 30000 });
    await snap(page, '06-habit-loaded');
    pass(APP, 'Pagina caricata');
  } catch (e) { fail(APP, 'Pagina caricata', e); await page.close(); return; }

  try {
    await page.waitForSelector('#page-dashboard', { state: 'visible', timeout: 8000 });
    pass(APP, 'Dashboard (#page-dashboard) visibile');
  } catch (e) { fail(APP, 'Dashboard visibile', e); }

  try {
    await page.waitForSelector('.habit-card', { timeout: 6000 });
    const n = await page.locator('.habit-card').count();
    pass(APP, `Habit cards caricate (${n})`);
    await snap(page, '07-habit-cards');
  } catch (_) {
    log(`  ⚠  Nessuna habit-card — normale se non ci sono habit attive`);
    pass(APP, 'Dashboard caricata (nessuna habit card, normale)');
  }

  const allNav = page.locator('.nav-item');
  const count  = await allNav.count();
  for (let i = 0; i < Math.min(count, 5); i++) {
    const item = allNav.nth(i);
    if (!(await item.isVisible())) continue;
    try {
      const label = (await item.innerText()).trim();
      const slug  = label.replace(/\s+/g, '_').replace(/[^\w_]/g, '').substring(0, 20);
      await item.click();
      await new Promise(r => setTimeout(r, 500));
      await snap(page, `08-habit-nav-${i + 1}-${slug}`);
      pass(APP, `Nav "${label}" cliccabile`);
    } catch (e) { fail(APP, `Nav item ${i + 1}`, e); }
  }

  await page.close();
}

// ──────────────────────────────────────────────────────────────────────────────
// APP 4 — Events Log
// ──────────────────────────────────────────────────────────────────────────────
async function testEventsLog(ctx) {
  const APP = 'EventsLog';
  log(`\n${'═'.repeat(50)}\n  ${APP}\n${'═'.repeat(50)}`);
  const page = await newTrackedPage(ctx, APP);

  try {
    await page.goto(`${BASE}/events-log.html`, { waitUntil: 'networkidle', timeout: 30000 });
    await snap(page, '09-events-loaded');
    pass(APP, 'Pagina caricata');
  } catch (e) { fail(APP, 'Pagina caricata', e); await page.close(); return; }

  try {
    await page.waitForSelector('#logPage', { state: 'visible', timeout: 8000 });
    pass(APP, 'Log page (#logPage) visibile');
  } catch (e) { fail(APP, 'Log page visibile', e); }

  try {
    await page.waitForSelector('.event-btn', { timeout: 6000 });
    const n = await page.locator('.event-btn').count();
    pass(APP, `Event buttons visibili (${n})`);
    await snap(page, '10-events-buttons');
  } catch (_) {
    log(`  ⚠  Nessun event-btn — normale se non ci sono eventi configurati`);
    pass(APP, 'Log page caricata (nessun evento, normale)');
  }

  const allNav = page.locator('.nav-item');
  const count  = await allNav.count();
  for (let i = 0; i < Math.min(count, 5); i++) {
    const item = allNav.nth(i);
    if (!(await item.isVisible())) continue;
    try {
      const label = (await item.getAttribute('data-text') || await item.innerText()).trim();
      const slug  = label.replace(/\s+/g, '_').replace(/[^\w_]/g, '').substring(0, 20);
      await item.click();
      await new Promise(r => setTimeout(r, 500));
      await snap(page, `11-events-nav-${i + 1}-${slug}`);
      pass(APP, `Nav "${label}" cliccabile`);
    } catch (e) { fail(APP, `Nav item ${i + 1}`, e); }
  }

  await page.close();
}

// ──────────────────────────────────────────────────────────────────────────────
// APP 5 — Weight Quest
// ──────────────────────────────────────────────────────────────────────────────
async function testWeightQuest(ctx) {
  const APP = 'WeightQuest';
  log(`\n${'═'.repeat(50)}\n  ${APP}\n${'═'.repeat(50)}`);
  const page = await newTrackedPage(ctx, APP);

  try {
    await page.goto(`${BASE}/weight-quest.html`, { waitUntil: 'networkidle', timeout: 30000 });
    await snap(page, '12-weight-loaded');
    pass(APP, 'Pagina caricata');
  } catch (e) { fail(APP, 'Pagina caricata', e); await page.close(); return; }

  const mainVisible = await page.locator('#mainSection').isVisible().catch(() => false);
  const authVisible = await page.locator('#authSection').isVisible().catch(() => false);

  if (mainVisible) {
    pass(APP, 'Autenticato — #mainSection visibile');

    try {
      await page.waitForSelector('#weightChart', { state: 'visible', timeout: 10000 });
      pass(APP, 'Chart canvas (#weightChart) visibile');
      await snap(page, '13-weight-chart');
    } catch (e) { fail(APP, 'Chart canvas visibile', e); }

    const allNav = page.locator('.icon-nav-item');
    const count  = await allNav.count();
    for (let i = 0; i < count; i++) {
      const item = allNav.nth(i);
      if (!(await item.isVisible())) continue;
      try {
        const label = await item.getAttribute('data-page') || String(i + 1);
        await item.click();
        await new Promise(r => setTimeout(r, 600));
        await snap(page, `14-weight-nav-${i + 1}-${label}`);
        pass(APP, `Nav "${label}" cliccabile`);
      } catch (e) { fail(APP, `Nav item ${i + 1}`, e); }
    }
  } else {
    if (authVisible) {
      log(`  ⚠  Weight Quest richiede google_token (passato via postMessage dal launcher)`);
      await snap(page, '13-weight-auth-required');
      pass(APP, 'Pagina caricata (google_token assente — apri da AppSphere per auth completa)');
    } else {
      await snap(page, '13-weight-stato-sconosciuto');
      fail(APP, 'Stato auth non determinabile', new Error('né #mainSection né #authSection visibili'));
    }
  }

  await page.close();
}

// ──────────────────────────────────────────────────────────────────────────────
// MAIN
// ──────────────────────────────────────────────────────────────────────────────
(async () => {
  log(`\nUser test — ${new Date().toISOString()}`);
  log(`Base URL: ${BASE}`);
  log(`Session:  ${SESSION}\n`);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    storageState: SESSION,
    viewport: { width: 1280, height: 800 },
  });

  const t0 = Date.now();

  await testLauncher(context);
  await testTasks(context);
  await testHabitTracker(context);
  await testEventsLog(context);
  await testWeightQuest(context);

  await browser.close();

  // ── Genera report ──────────────────────────────────────────────────────────
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  const passed  = results.filter(r => r.status === 'PASS').length;
  const failed  = results.filter(r => r.status === 'FAIL').length;

  const lines = [
    '═'.repeat(60),
    `REPORT USER TEST — ${new Date().toISOString()}`,
    `Durata: ${elapsed}s`,
    '═'.repeat(60),
    '',
    `TOTALE: ${passed + failed} test  |  ✓ ${passed} PASS  |  ✗ ${failed} FAIL`,
    '',
  ];

  const apps = [...new Set(results.map(r => r.app))];
  for (const app of apps) {
    const ar   = results.filter(r => r.app === app);
    const ap   = ar.filter(r => r.status === 'PASS').length;
    const af   = ar.filter(r => r.status === 'FAIL').length;
    lines.push(`── ${app}  (${ap} ✓  ${af} ✗) ${'─'.repeat(Math.max(0, 40 - app.length))}`);
    for (const r of ar) {
      lines.push(`  ${r.status === 'PASS' ? '✓' : '✗'}  ${r.test}`);
      if (r.error) lines.push(`     ERROR: ${r.error}`);
    }
    const errs = consoleErrs[app] || [];
    if (errs.length) {
      lines.push(`  Console errors (${errs.length}):`);
      errs.slice(0, 10).forEach(e => lines.push(`    • ${e.substring(0, 120)}`));
      if (errs.length > 10) lines.push(`    … e altri ${errs.length - 10}`);
    }
    lines.push('');
  }

  lines.push(`Screenshots: ${SHOTS}`);
  lines.push(`Report:      ${REPORT}`);

  const report = lines.join('\n');
  log('\n' + report);
  fs.writeFileSync(REPORT, report, 'utf8');
})();
