# Script temporaneo di migrazione automatica (SILENZIOSO CORRETTO)
$SRC_PASS = '6z5WnY4E2n0kMCI5'
$DST_PASS = 'taDvFV/Kb!P-645'
$OLD_UUID = '12b34655-eb24-40a2-9f75-8fe58c745918'
$NEW_UUID = '94560122-d87e-4604-aa41-2a1292ea7b64'

$SRC_HOST = "db.coaxstavjpiqfqofsenv.supabase.co"
$DST_HOST = "db.jajlmmdsjlvzgcxiiypk.supabase.co"

Write-Host "--- Avvio Migrazione Automatica ---" -ForegroundColor Cyan

# 1. Export
Write-Host "[1/3] Esportazione dati da $SRC_HOST..." -ForegroundColor Yellow
$env:PGPASSWORD = $SRC_PASS
# NOTA: pg_dump NON ha l'opzione -q. La lasciamo standard.
pg_dump -h $SRC_HOST -U postgres -d postgres --data-only --column-inserts `
  -t portfolios -t products -t transactions -t dossiers -t loans `
  -t tag_categories -t other_asset_types -t other_assets -t acct_transactions `
  -t dashboard_snapshots -t cntrs_transactions -t cntrs_categories -t cntrs_saldi `
  -t cntrs_legacy_data -t price_cache -t price_history -t fn_logs `
  -f dati_migrazione_auto.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Errore durante l'esportazione!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "✅ Esportazione completata." -ForegroundColor Green

# 2. Fix UUID
Write-Host "`n[2/3] Sostituzione UUID ($OLD_UUID -> $NEW_UUID)..." -ForegroundColor Yellow
$tmpFile = "dati_migrazione_auto_tmp.sql"
$sourceFile = "$(Get-Location)\dati_migrazione_auto.sql"
$destFile = "$(Get-Location)\$tmpFile"

try {
    $reader = [System.IO.File]::OpenText($sourceFile)
    $writer = [System.IO.File]::CreateText($destFile)
    $count = 0
    while ($null -ne ($line = $reader.ReadLine())) {
        $newLine = $line.Replace($OLD_UUID, $NEW_UUID)
        $writer.WriteLine($newLine)
        $count++
        if ($count % 5000 -eq 0) { Write-Host "   -> Processate $count righe..." -ForegroundColor Gray }
    }
    $reader.Close(); $writer.Close()
    if (Test-Path $sourceFile) { Remove-Item $sourceFile -Force }
    Move-Item $destFile $sourceFile -Force
    Write-Host "✅ Sostituzione completata! Righe elaborate: $count" -ForegroundColor Green
} catch {
    Write-Host "❌ Errore durante la modifica del file: $_" -ForegroundColor Red
    if ($reader) { $reader.Close() }
    if ($writer) { $writer.Close() }
    exit 1
}

# 3. Import
Write-Host "`n[3/3] Importazione dati su $DST_HOST..." -ForegroundColor Yellow
Write-Host "   (Modalità silenziosa attiva)" -ForegroundColor Gray
$env:PGPASSWORD = $DST_PASS
# psql HA l'opzione -q per nascondere i messaggi di successo
psql -q -h $DST_HOST -U postgres -d postgres -f dati_migrazione_auto.sql

if ($LASTEXITCODE -ne 0) { 
    Write-Host "❌ Errore durante l'importazione!" -ForegroundColor Red
} else { 
    Write-Host "`n--- ✅ Migrazione Completata con Successo! ---" -ForegroundColor Green 
}

# Cleanup
$env:PGPASSWORD = ""
Write-Host "`nOperazione terminata." -ForegroundColor Cyan
