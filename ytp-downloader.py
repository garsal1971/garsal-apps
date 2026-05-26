#!/usr/bin/env python3
"""
YTP Downloader — scarica audio YouTube e carica su Supabase Storage.
Eseguilo sul tuo PC mentre usi l'app web.

Requisiti:
  pip install yt-dlp requests

Uso:
  python ytp-downloader.py
"""
import json, os, subprocess, sys, time, tempfile, platform
import urllib.request, urllib.error

# ── Credenziali Supabase (production) ────────────────────────────────────────
SUPABASE_URL         = "https://jajlmmdsjlvzgcxiiypk.supabase.co"
SUPABASE_SERVICE_KEY = ""   # ← incolla qui la service_role key

POLL_INTERVAL = 15   # secondi tra un controllo e l'altro
BUCKET        = "ytp-audio"

# ── Browser da cui leggere i cookie ──────────────────────────────────────────
# Opzioni: "chrome", "firefox", "edge", "brave", "chromium", "opera"
BROWSER = "chrome"

# ─────────────────────────────────────────────────────────────────────────────

def _headers():
    return {
        "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
        "apikey": SUPABASE_SERVICE_KEY,
        "Accept": "application/json",
    }

def supabase_get(path, params=""):
    url = f"{SUPABASE_URL}/rest/v1/{path}?{params}"
    req = urllib.request.Request(url, headers=_headers())
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read())

def supabase_patch(path, params, data):
    url = f"{SUPABASE_URL}/rest/v1/{path}?{params}"
    body = json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, method="PATCH", headers={
        **_headers(), "Content-Type": "application/json"
    })
    urllib.request.urlopen(req, timeout=15)

def supabase_upload(object_path, file_path):
    url = f"{SUPABASE_URL}/storage/v1/object/{object_path}"
    with open(file_path, "rb") as f:
        data = f.read()
    req = urllib.request.Request(url, data=data, method="POST", headers={
        "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
        "Content-Type": "audio/mpeg",
        "x-upsert": "true",
    })
    urllib.request.urlopen(req, timeout=120)

def download_item(item):
    ytid     = item["youtube_id"]
    item_id  = item["id"]
    title    = item.get("title") or ytid
    print(f"  ▶ {title} ({ytid})")

    supabase_patch("ytp_playlist_items", f"id=eq.{item_id}", {"audio_status": "processing"})

    with tempfile.TemporaryDirectory() as tmpdir:
        out_tmpl = os.path.join(tmpdir, "dl.%(ext)s")
        mp3_path = os.path.join(tmpdir, "dl.mp3")

        cmd = [
            "yt-dlp",
            "-x", "--audio-format", "mp3", "--audio-quality", "5",
            "--no-playlist",
            "--cookies-from-browser", BROWSER,
            "--extractor-retries", "3",
            "-o", out_tmpl,
            f"https://www.youtube.com/watch?v={ytid}",
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)

        if result.returncode != 0 or not os.path.exists(mp3_path):
            err = (result.stderr or result.stdout)[-600:]
            print(f"  ✗ Errore yt-dlp:\n{err}")
            supabase_patch("ytp_playlist_items", f"id=eq.{item_id}", {"audio_status": "error"})
            return

        size_mb = os.path.getsize(mp3_path) / 1_048_576
        print(f"  ↑ Upload ({size_mb:.1f} MB)...")
        supabase_upload(f"{BUCKET}/{ytid}.mp3", mp3_path)

        audio_url = f"{SUPABASE_URL}/storage/v1/object/public/{BUCKET}/{ytid}.mp3"
        supabase_patch("ytp_playlist_items", f"youtube_id=eq.{ytid}", {
            "audio_url": audio_url,
            "audio_status": "ready",
        })
        print(f"  ✅ Pronto!")

def check_config():
    if not SUPABASE_SERVICE_KEY:
        print("ERRORE: SUPABASE_SERVICE_KEY non configurata.")
        print("Apri ytp-downloader.py con un editor di testo e incolla la")
        print("service_role key nella variabile SUPABASE_SERVICE_KEY.")
        sys.exit(1)

def main():
    check_config()
    os_name = platform.system()
    print("=" * 55)
    print(" YTP Downloader — in ascolto per nuovi download")
    print(f" Browser: {BROWSER}  |  OS: {os_name}")
    print(f" Polling ogni {POLL_INTERVAL}s — Ctrl+C per uscire")
    print("=" * 55)

    while True:
        try:
            items = supabase_get(
                "ytp_playlist_items",
                "audio_status=eq.pending&select=id,youtube_id,title"
            )
            if items:
                print(f"\n[{time.strftime('%H:%M:%S')}] {len(items)} download in coda:")
                for item in items:
                    try:
                        download_item(item)
                    except Exception as e:
                        print(f"  ✗ Eccezione: {e}")
            else:
                print(f"[{time.strftime('%H:%M:%S')}] Nessun download in attesa", end="\r")
        except Exception as e:
            print(f"\n[{time.strftime('%H:%M:%S')}] Errore connessione: {e}")
        time.sleep(POLL_INTERVAL)

if __name__ == "__main__":
    main()
