# Script per aggiornare l'UUID nel file di migrazione
param(
    [Parameter(Mandatory=$true)]
    [string]$OldId,
    
    [Parameter(Mandatory=$true)]
    [string]$NewId,
    
    [string]$FilePath = "dati_migrazione.sql"
)

Write-Host "--- Aggiornamento UUID nel file $FilePath ---" -ForegroundColor Cyan

if (!(Test-Path $FilePath)) {
    Write-Host "Errore: File $FilePath non trovato!" -ForegroundColor Red
    return
}

Write-Host "Sostituzione di $OldId con $NewId..." -ForegroundColor Yellow

# Legge il file, sostituisce la stringa e salva
(Get-Content $FilePath) -replace [regex]::Escape($OldId), $NewId | Set-Content $FilePath

Write-Host "Aggiornamento completato con successo!" -ForegroundColor Green
Write-Host "Ora puoi lanciare il comando di importazione psql." -ForegroundColor Cyan
