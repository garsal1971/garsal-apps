/**
 * Alternativa a capture-session.js per ambienti senza display grafico.
 * Legge i dati di autenticazione esportati dal browser e crea session.json.
 *
 * Istruzioni:
 * 1. Apri https://garsal-apps.netlify.app nel tuo browser e fai il login
 * 2. Apri DevTools → Console
 * 3. Incolla ed esegui questo comando:
 *
 *    copy(JSON.stringify(Object.fromEntries([...Object.keys(localStorage)].map(k=>[k,localStorage.getItem(k)]))))
 *
 * 4. Il JSON è ora negli appunti. Esegui questo script e incollalo quando richiesto:
 *    node tests/import-session.js
 */
const fs   = require('fs');
const path = require('path');
const rl   = require('readline').createInterface({ input: process.stdin, output: process.stdout });

console.log('\n=== Import sessione da browser ===\n');
console.log('1. Apri https://garsal-apps.netlify.app e fai il login con Google');
console.log('2. Apri DevTools → Console (F12)');
console.log('3. Esegui questo comando nella console:\n');
console.log('   copy(JSON.stringify(Object.fromEntries([...Object.keys(localStorage)].map(k=>[k,localStorage.getItem(k)]))))');
console.log('\n4. Il JSON è copiato negli appunti.');
console.log('   Incollalo qui sotto e premi Invio, poi Ctrl+D:\n');

let raw = '';
process.stdin.on('data', chunk => { raw += chunk; });
process.stdin.on('end', () => {
  raw = raw.trim();
  if (!raw) { console.error('❌  Nessun input ricevuto.'); process.exit(1); }

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    console.error('❌  JSON non valido:', e.message);
    process.exit(1);
  }

  // Costruisce il formato storageState di Playwright
  const localStorageItems = Object.entries(parsed).map(([name, value]) => ({ name, value }));

  const session = {
    cookies: [],
    origins: [
      {
        origin: 'https://garsal-apps.netlify.app',
        localStorage: localStorageItems,
      },
    ],
  };

  const sessionPath = path.join(__dirname, 'session.json');
  fs.writeFileSync(sessionPath, JSON.stringify(session, null, 2), 'utf8');
  console.log(`\n✓ session.json salvato in: ${sessionPath}`);
  console.log('Ora puoi eseguire: node tests/run-tests.js\n');
  rl.close();
});
