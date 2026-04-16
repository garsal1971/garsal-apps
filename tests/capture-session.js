/**
 * Step 1 — Cattura sessione Google OAuth
 * Apre un browser visibile, aspetta il login manuale, salva cookies + localStorage in session.json
 * Esecuzione: node tests/capture-session.js
 */
const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const path = require('path');

(async () => {
  console.log('Avvio browser per cattura sessione...');

  const browser = await chromium.launch({ headless: false, slowMo: 50 });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();

  console.log('Navigo su https://garsal-apps.netlify.app/app-launcher.html');
  console.log('Completa il login con Google nel browser aperto, poi attendo...\n');

  await page.goto('https://garsal-apps.netlify.app/app-launcher.html');

  console.log('In attesa che #user-bar diventi visibile (login completato)...');
  try {
    await page.waitForSelector('#user-bar', { state: 'visible', timeout: 120000 });
    console.log('✓ Login rilevato!');
  } catch (e) {
    console.log('⚠ Timeout dopo 2 minuti. Salvo la sessione comunque...');
  }

  // Pausa per far stabilizzare tutti i token in localStorage/sessionStorage
  await new Promise(r => setTimeout(r, 2000));

  const sessionPath = path.join(__dirname, 'session.json');
  await context.storageState({ path: sessionPath });

  console.log(`\n✓ Sessione salvata in: ${sessionPath}`);
  console.log('Ora puoi eseguire: node tests/run-tests.js');

  await browser.close();
})();
