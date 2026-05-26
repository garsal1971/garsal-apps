#!/usr/bin/env python3
"""
YTP Downloader v2.0 — Supabase Realtime
Riceve notifiche istantanee quando un video viene messo in coda,
scarica l'audio con yt-dlp e carica l'MP3 su Supabase Storage.

Requisiti:  pip install supabase yt-dlp
Avvio:      python ytp-downloader.py
            (oppure doppio clic su ytp-downloader.bat su Windows)
"""
import asyncio, json, os, subprocess, sys, tempfile, time, platform
import urllib.request

# ── Configurazione ────────────────────────────────────────────────────────────
SUPABASE_URL         = "https://jajlmmdsjlvzgcxiiypk.supabase.co"
SUPABASE_SERVICE_KEY = ""   # ← incolla qui la service_role key di Supabase

BUCKET  = "ytp-audio"
BROWSER = "chrome"   # "chrome" | "firefox" | "edge" | "brave" | "chromium"
# ─────────────────────────────────────────────────────────────────────────────

def ts():
    return time.strftime('%H:%M:%S')

# ── REST helpers (stdlib, nessuna dipendenza extra) ───────────────────────────
def _h():
    return {"Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
            "apikey": SUPABASE_SERVICE_KEY, "Accept": "application/json"}

def rest_get(path, qs=""):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{path}?{qs}", headers=_h())
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read())

def rest_patch(path, qs, body):
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        f"{SUPABASE_URL}/rest/v1/{path}?{qs}", data=data, method="PATCH",
        headers={**_h(), "Content-Type": "application/json"})
    urllib.request.urlopen(req, timeout=15)

def rest_upload(obj_path, file_path):
    with open(file_path, "rb") as f:
        raw = f.read()
    req = urllib.request.Request(
        f"{SUPABASE_URL}/storage/v1/object/{obj_path}", data=raw, method="POST",
        headers={"Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
                 "Content-Type": "audio/mpeg", "x-upsert": "true"})
    urllib.request.urlopen(req, timeout=120)

# ── Download ──────────────────────────────────────────────────────────────────
def download_item(item):
    # Se il payload Realtime ha solo l'id, recupera il record completo
    if not item.get("youtube_id") and item.get("id"):
        rows = rest_get("ytp_playlist_items",
                        f"id=eq.{item['id']}&select=id,youtube_id,title,audio_status")
        if not rows:
            print(f"  ✗  record {item['id']} non trovato")
            return
        item = rows[0]

    if item.get("audio_status") != "pending":
        return

    ytid = item["youtube_id"]
    iid  = item["id"]
    print(f"\n  ▶  {item.get('title') or ytid}  ({ytid})")

    rest_patch("ytp_playlist_items", f"id=eq.{iid}", {"audio_status": "processing"})

    with tempfile.TemporaryDirectory() as d:
        mp3 = os.path.join(d, "dl.mp3")
        r = subprocess.run([
            "yt-dlp", "-x", "--audio-format", "mp3", "--audio-quality", "5",
            "--no-playlist", "--cookies-from-browser", BROWSER,
            "--extractor-retries", "3",
            "-o", os.path.join(d, "dl.%(ext)s"),
            f"https://www.youtube.com/watch?v={ytid}",
        ], capture_output=True, text=True)

        if r.returncode != 0 or not os.path.exists(mp3):
            print(f"  ✗  {(r.stderr or r.stdout)[-500:]}")
            rest_patch("ytp_playlist_items", f"id=eq.{iid}", {"audio_status": "error"})
            return

        sz = os.path.getsize(mp3) / 1_048_576
        print(f"  ↑  upload {sz:.1f} MB…")
        rest_upload(f"{BUCKET}/{ytid}.mp3", mp3)
        rest_patch("ytp_playlist_items", f"youtube_id=eq.{ytid}", {
            "audio_url":    f"{SUPABASE_URL}/storage/v1/object/public/{BUCKET}/{ytid}.mp3",
            "audio_status": "ready",
        })
        print("  ✅  pronto!")

def flush_pending():
    """Elabora i pending accumulati prima dell'avvio o dopo una riconnessione."""
    try:
        items = rest_get("ytp_playlist_items",
                         "audio_status=eq.pending&select=id,youtube_id,title")
        if items:
            print(f"[{ts()}] {len(items)} download in coda (arretrati):")
            for it in items:
                try:
                    download_item(it)
                except Exception as e:
                    print(f"  ✗  {e}")
        else:
            print(f"[{ts()}] Nessun arretrato.")
    except Exception as e:
        print(f"[{ts()}] Errore verifica arretrati: {e}")

# ── Realtime ──────────────────────────────────────────────────────────────────
async def main():
    try:
        from supabase import acreate_client
    except ImportError:
        print("ERRORE: installa le dipendenze con:\n  pip install supabase yt-dlp")
        sys.exit(1)

    if not SUPABASE_SERVICE_KEY:
        print("ERRORE: inserisci SUPABASE_SERVICE_KEY nello script.")
        sys.exit(1)

    print("=" * 55)
    print(" YTP Downloader v2.0  —  Supabase Realtime")
    print(f" Browser: {BROWSER}   OS: {platform.system()}")
    print(" Ctrl+C per uscire")
    print("=" * 55)

    # Processa eventuali pending accumulati prima dell'avvio
    flush_pending()

    client = await acreate_client(SUPABASE_URL, SUPABASE_SERVICE_KEY)

    def on_change(payload):
        new = payload.get("new") or {}
        if new.get("audio_status") == "pending":
            print(f"\n[{ts()}] ▼ Realtime: nuovo download!")
            try:
                download_item(new)
            except Exception as e:
                print(f"  ✗  {e}")

    channel = client.realtime.channel("ytp-downloads")
    channel.on_postgres_changes(
        event="UPDATE", schema="public",
        table="ytp_playlist_items", callback=on_change)
    channel.on_postgres_changes(
        event="INSERT", schema="public",
        table="ytp_playlist_items", callback=on_change)

    await channel.subscribe()
    print(f"[{ts()}] ✅ In ascolto su Realtime — nessun polling, zero consumo\n")

    # Tieni vivo il loop; la riconnessione è gestita automaticamente dal client
    while True:
        await asyncio.sleep(3600)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nUscita.")
