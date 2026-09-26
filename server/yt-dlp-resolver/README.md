# yt-dlp stream resolver

Small HTTP service used by TeamTalk Next to resolve YouTube URLs into temporary direct media URLs.

## Run

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
YT_DLP_RESOLVER_TOKEN=change-me python3 app.py
```

`GET /health` returns `{"ok": true}`.

`GET /resolve?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D...`

Optional header: `Authorization: Bearer <YT_DLP_RESOLVER_TOKEN>`.

Response:

```json
{"url":"https://...","title":"...","expires":1770000000}
```

Only public HTTP(S) YouTube URLs are accepted. The service returns an audio-only URL suitable for TeamTalk media-file streaming; the URL is temporary and must not be persisted.
