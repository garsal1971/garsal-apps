# Script di Migrazione Dati da garsal-finanza a garsal-apps
# Assicurati di avere pg_dump e psql installati nel PATH

$SRC_HOST = "db.coaxstavjpiqfqofsenv.supabase.co"
$DST_HOST = "db.jajlmmdsjlvzgcxiiypk.supabase.co"
$DB_NAME = "postgres"
$USER = "postgres"

Write-Host "--- Inizio Migrazione Dati con Aggiornamento UUID (Versione Ottimizzata) ---" -ForegroundColor Cyan

# 1. Richiesta Password e UUID
$src_pass = Read-Host "Inserisci la password del DB SORGENTE (garsal-finanza)" -AsSecureString
$dst_pass = Read-Host "Inserisci la password del DB DESTINAZIONE (garsal-apps)" -AsSecureString

$old_uuid = Read-Host "Inserisci il VECCHIO UUID (quello di garsal-finanza)"
$new_uuid = Read-Host "Inserisci il NUOVO UUID (quello di garsal-apps)"

$src_pass_plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($src_pass))
$dst_pass_plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($dst_pass))

# 2. Esportazione Dati
Write-Host "`n[1/3] Esportazione dati da $SRC_HOST..." -ForegroundColor Yellow
$env:PGPASSWORD = $src_pass_plain
pg_dump -h $SRC_HOST -U $USER -d $DB_NAME --data-only --column-inserts `
  -t portfolios -t products -t transactions -t dossiers -t loans `
  -t tag_categories -t other_asset_types -t other_assets -t acct_transactions `
  -t dashboard_snapshots -t cntrs_transactions -t cntrs_categories -t cntrs_saldi `
  -t cntrs_legacy_data -t price_cache -t price_history -t fn_logs `
  -f dati_migrazione.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Errore durante l'esportazione!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "✅ Esportazione completata con successo." -ForegroundColor Green

# 3. Aggiornamento UUID nel file SQL (Versione ad alte prestazioni)
Write-Host "`n[2/3] Sostituzione UUID nel file SQL ($old_uuid -> $new_uuid)..." -ForegroundColor Yellow
$tmpFile = "dati_migrazione_tmp.sql"
$oldIdEscaped = [regex]::Escape($old_uuid)

try {
    $reader = [System.IO.File]::OpenText("$(Get-Location)\dati_migrazione.sql")
    $writer = [System.IO.File]::CreateText("$(Get-Location)\$tmpFile")

    $lineCount = 0
    while ($null -ne ($line = $reader.ReadLine())) {
        $writer.WriteLine($line -replace $oldIdEscaped, $new_uuid)
        $lineCount++
        
        # Mostra avanzamento ogni 5000 righe
        if ($lineCount % 5000 -eq 0) {
            Write-Host "   -> Processate $lineCount righe..." -ForegroundColor Gray
        }
    }

    $reader.Close()
    $writer.Close()

    Remove-Item "dati_migrazione.sql" -Force
    Move-Item $tmpFile "dati_migrazione.sql" -Force
    Write-Host "✅ Sostituzione completata! Totale righe elaborate: $lineCount" -ForegroundColor Green
} catch {
    Write-Host "❌ Errore durante la manipolazione del file: $_" -ForegroundColor Red
    if ($reader) { $reader.Close() }
    if ($writer) { $writer.Close() }
    exit 1
}

# 4. Importazione Dati
Write-Host "`n[3/3] Importazione dati su $DST_HOST..." -ForegroundColor Yellow
Write-Host "   (Questa operazione può richiedere tempo a seconda del volume dei dati)" -ForegroundColor Gray
$env:PGPASSWORD = $dst_pass_plain
psql -h $DST_HOST -U $USER -d $DB_NAME -f dati_migrazione.sql

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Errore durante l'importazione!" -ForegroundColor Red
} else {
    Write-Host "`n--- ✅ Migrazione completata con successo! ---" -ForegroundColor Green
}

# Pulizia password dall'ambiente
$env:PGPASSWORD = ""
Write-Host "`nOperazione terminata." -ForegroundColor Cyan
