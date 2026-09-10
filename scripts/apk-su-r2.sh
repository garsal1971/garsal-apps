#!/usr/bin/env bash
#
# Carica una o più APK sul bucket R2 che le serve, `apk.garsal.men`.
#
# ⚠️ Le APK NON stanno più sul sito: Cloudflare Pages rifiuta i file oltre i
# 25 MiB e il file troppo grosso non fallisce da solo — fallisce l'intero
# deploy (vedi `scripts/build-sito.sh`). Le schede `-latest.json` restano
# invece su garsal.men, che è dove ogni `Aggiornamento.kt` le va a leggere.
#
# Il nome sul bucket è il **nome del file e basta**, senza cartelle: gli APK
# già installati chiedono `https://apk.garsal.men/<Nome>-latest.apk`, e un
# prefisso qui vorrebbe dire un indirizzo diverso da quello che sanno.
#
# Vuole due variabili d'ambiente (segreti della repo):
#   CLOUDFLARE_API_TOKEN   token con «Workers R2 Storage: Edit»
#   CLOUDFLARE_ACCOUNT_ID  l'account su cui sta il bucket
#
#   bash scripts/apk-su-r2.sh /tmp/Sos-latest.apk

set -euo pipefail

BUCKET="${R2_BUCKET:-garsal-apk}"

if [ $# -eq 0 ]; then
    echo "uso: apk-su-r2.sh <file.apk> [altri.apk …]" >&2
    exit 2
fi

: "${CLOUDFLARE_API_TOKEN:?manca CLOUDFLARE_API_TOKEN}"
: "${CLOUDFLARE_ACCOUNT_ID:?manca CLOUDFLARE_ACCOUNT_ID}"

for f in "$@"; do
    [ -f "$f" ] || { echo "❌ non esiste: $f" >&2; exit 1; }
    nome=$(basename "$f")

    # ⚠️ `--remote` non è facoltativo: senza, wrangler scrive nella copia
    # locale simulata e il comando **riesce** senza che sul bucket vero
    # arrivi niente — cioè un caricamento che non si vede fallire.
    npx --yes wrangler@4 r2 object put "$BUCKET/$nome" \
        --file="$f" \
        --content-type=application/vnd.android.package-archive \
        --remote

    echo "↑ $nome — $(stat -c%s "$f") byte → https://apk.garsal.men/$nome"
done
