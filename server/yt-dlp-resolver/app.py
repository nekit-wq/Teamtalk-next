import os
import time
import logging
import subprocess
import threading
import uuid
from urllib.parse import urlparse

from flask import Flask, jsonify, request
import yt_dlp

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
TOKEN = os.environ.get("YT_DLP_RESOLVER_TOKEN", "").strip()
MAX_URL_LENGTH = 2048
STREAMS = {}
STREAM_LOCK = threading.Lock()


def authorized():
    if not TOKEN:
        return True
    return request.headers.get("Authorization", "") == f"Bearer {TOKEN}"


def valid_url(value):
    try:
        parsed = urlparse(value)
        host = (parsed.hostname or "").lower()
        return parsed.scheme in {"http", "https"} and host in {
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
            "music.youtube.com",
        }
    except ValueError:
        return False


@app.get("/health")
def health():
    return jsonify(ok=True)


@app.get("/resolve")
def resolve():
    if not authorized():
        return jsonify(error="unauthorized"), 401

    source = request.args.get("url", "").strip()
    if len(source) > MAX_URL_LENGTH or not valid_url(source):
        return jsonify(error="only a valid YouTube HTTP(S) URL is accepted"), 400

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": "bestaudio[protocol^=http]/bestaudio",
        "skip_download": True,
    }
    try:
        with yt_dlp.YoutubeDL(options) as downloader:
            info = downloader.extract_info(source, download=False)
        stream_url = info.get("url")
        if not stream_url:
            return jsonify(error="yt-dlp returned no playable URL"), 502
        expires = int(info.get("expiry") or (time.time() + 3600))
        app.logger.info("Resolved source=%s title=%r url_length=%d expires=%d", source, info.get("title", ""), len(stream_url), expires)
        stream_id = uuid.uuid4().hex
        with STREAM_LOCK:
            STREAMS[stream_id] = {"url": stream_url, "created": time.time()}
        return jsonify(url=f"{request.scheme}://{request.host}/stream/{stream_id}", title=info.get("title", ""), expires=expires)
    except Exception as exc:
        app.logger.exception("yt-dlp resolution failed for %s: %s", source, exc)
        return jsonify(error="failed to resolve media URL"), 502


@app.get("/stream/<stream_id>")
def stream(stream_id):
    if not authorized():
        return jsonify(error="unauthorized"), 401
    with STREAM_LOCK:
        item = STREAMS.get(stream_id)
    if not item or time.time() - item["created"] > 3600:
        return jsonify(error="stream expired"), 404
    command = [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-i", item["url"],
        "-vn", "-ac", "2", "-ar", "48000", "-c:a", "libmp3lame", "-b:a", "128k", "-f", "mp3", "-flush_packets", "1", "pipe:1",
    ]
    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except OSError:
        return jsonify(error="ffmpeg is not installed"), 503

    def generate():
        try:
            while True:
                chunk = process.stdout.read(16 * 1024)
                if not chunk:
                    break
                yield chunk
        finally:
            process.kill()
            process.wait()
            with STREAM_LOCK:
                STREAMS.pop(stream_id, None)

    return app.response_class(generate(), mimetype="audio/mpeg", direct_passthrough=True)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
