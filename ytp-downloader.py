#!/usr/bin/env python3
"""
YTP Downloader v2.5 — Supabase Realtime via websockets puro
Nessuna dipendenza C++. Requisiti: pip install websockets yt-dlp

Avvio: python ytp-downloader.py  (oppure doppio clic su ytp-downloader.bat)
Log:   ytp-downloader.log  (stessa cartella dello script)
"""
import asyncio, json, os, subprocess, sys, tempfile, time, platform
import urllib.request, urllib.error

# ── Configurazione ────────────────────────────────────────────────────────────
SUPABASE_URL         = "https://jajlmmdsjlvzgcxiiypk.supabase.co"
SUPABASE_SERVICE_KEY = ""   # ← incolla qui la service_role key di Supabase

BUCKET  = "ytp-audio"
BROWSER = "chrome"   # "chrome" | "firefox" | "edge" | "brave" | "chromium"
# ─────────────────────────────────────────────────────────────────────────────

_REF   = SUPABASE_URL.replace("https://", "").replace(".supabase.co", "")
WS_URL = f"wss://{_REF}.supabase.co/realtime/v1/websocket?apikey={SUPABASE_SERVICE_KEY}&vsn=1.0.0"

LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ytp-downloader.log")

def ts():
    return time.strftime('%Y-%m-%d %H:%M:%S')

def log(msg):
    line = f"[{ts()}] {msg}"
    print(line)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

# ── REST helpers ──────────────────────────────────────────────────────────────
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
    # Recupera sempre il record completo per avere tutti i campi aggiornati
    if item.get("id"):
        rows = rest_get("ytp_playlist_items",
                        f"id=eq.{item['id']}&select=id,youtube_id,title,audio_status")
        if not rows:
            log(f"✗  record {item['id']} non trovato")
            return
        item = rows[0]

    if item.get("audio_status") != "pending":
        return

    ytid = item["youtube_id"]
    iid  = item["id"]
    log(f"▶  {item.get('title') or ytid}  ({ytid})")

    rest_patch("ytp_playlist_items", f"id=eq.{iid}", {"audio_status": "processing"})

    with tempfile.TemporaryDirectory() as d:
        mp3 = os.path.join(d, "dl.mp3")
        r = subprocess.run([
            "yt-dlp", "-x", "--audio-format", "mp3", "--audio-quality", "5",
            "--no-playlist", "--cookies-from-browser", BROWSER,
            "--extractor-args", "youtube:player_client=ios,web",
            "--extractor-retries", "3",
            "-o", os.path.join(d, "dl.%(ext)s"),
            f"https://www.youtube.com/watch?v={ytid}",
        ], capture_output=True, text=True)

        if r.returncode != 0 or not os.path.exists(mp3):
            log(f"✗  yt-dlp error: {(r.stderr or r.stdout)[-400:]}")
            rest_patch("ytp_playlist_items", f"id=eq.{iid}", {"audio_status": "error"})
            return

        sz = os.path.getsize(mp3) / 1_048_576
        log(f"↑  upload {sz:.1f} MB…")
        rest_upload(f"{BUCKET}/{ytid}.mp3", mp3)
        rest_patch("ytp_playlist_items", f"youtube_id=eq.{ytid}", {
            "audio_url":    f"{SUPABASE_URL}/storage/v1/object/public/{BUCKET}/{ytid}.mp3",
            "audio_status": "ready",
        })
        log(f"✅  pronto! {ytid}")

def flush_pending():
    try:
        items = rest_get("ytp_playlist_items",
                         "audio_status=eq.pending&select=id,youtube_id,title,audio_status")
        if items:
            log(f"{len(items)} download in coda (arretrati):")
            for it in items:
                try:
                    download_item(it)
                except Exception as e:
                    log(f"✗  {e}")
        else:
            log("Nessun arretrato.")
    except Exception as e:
        log(f"Errore verifica arretrati: {e}")

# ── Supabase Realtime (Phoenix protocol) ──────────────────────────────────────
async def realtime_loop():
    try:
        import websockets
    except ImportError:
        log("ERRORE: installa websockets con:  pip install websockets")
        sys.exit(1)

    _ref_counter = 0

    def next_ref():
        nonlocal _ref_counter
        _ref_counter += 1
        return str(_ref_counter)

    while True:
        try:
            log("Connessione a Supabase Realtime…")
            async with websockets.connect(WS_URL, ping_interval=None) as ws:
                join_ref = next_ref()
                await ws.send(json.dumps([
                    join_ref, next_ref(),
                    "realtime:ytp-downloads",
                    "phx_join",
                    {
                        "config": {
                            "broadcast":        {"ack": False, "self": False},
                            "presence":         {"key": ""},
                            "postgres_changes": [
                                {"event": "*", "schema": "public",
                                 "table": "ytp_playlist_items"}
                            ]
                        },
                        "access_token": SUPABASE_SERVICE_KEY
                    }
                ]))

                async def heartbeat():
                    while True:
                        await asyncio.sleep(25)
                        try:
                            await ws.send(json.dumps(
                                [None, next_ref(), "phoenix", "heartbeat", {}]))
                        except Exception:
                            break

                hb_task = asyncio.create_task(heartbeat())
                log("✅ In ascolto su Realtime — nessun polling, zero consumo")
                log(f"   Log: {LOG_FILE}\n")

                async for raw in ws:
                    msg = json.loads(raw)
                    if len(msg) < 5:
                        continue
                    event   = msg[3]
                    payload = msg[4]

                    if event == "phx_reply":
                        status = payload.get("status", "?")
                        if status != "ok":
                            log(f"⚠ phx_reply {status}: {payload.get('response', '')}")
                        else:
                            log(f"  canale confermato (subscription ok)")
                        continue
                    if event == "system":
                        log(f"  system: {payload}")
                        continue
                    if event == "postgres_changes":
                        change = payload.get("data", {})
                        record = change.get("record", {})
                        if record.get("audio_status") == "pending":
                            log("▼ Realtime: nuovo download ricevuto!")
                            try:
                                download_item(record)
                            except Exception as e:
                                log(f"✗  {e}")

                hb_task.cancel()

        except Exception as e:
            log(f"Connessione persa: {e}")
            log("Riconnessione in 5s…")
            flush_pending()
            await asyncio.sleep(5)

# ── Entry point ───────────────────────────────────────────────────────────────
async def main():
    if not SUPABASE_SERVICE_KEY:
        log("ERRORE: inserisci SUPABASE_SERVICE_KEY nello script.")
        sys.exit(1)

    print("=" * 55)
    print(" YTP Downloader v2.5  —  Supabase Realtime")
    print(f" Browser: {BROWSER}   OS: {platform.system()}")
    print(f" Log: {LOG_FILE}")
    print(" Ctrl+C per uscire")
    print("=" * 55)
    log("=== Avvio YTP Downloader v2.5 ===")

    flush_pending()
    await realtime_loop()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log("Uscita.")
        print()
