"""
Pure-Python PMTiles Offline Map Server (Replaces `survival-station`'s PowerShell server)
Supports HTTP/1.1 `Range: bytes=start-end` (`206 Partial Content`) required by MapLibre GL JS
and Protomaps PMTiles readers on both Android (Termux) and Desktop OSes.
"""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re


def extract_byte_range(
    file_path: Path, range_header: str | None
) -> tuple[int, dict[str, str], bytes]:
    """
    Extracts a byte slice from a `.pmtiles` archive in response to an HTTP Range header.
    """
    if not file_path.exists() or not file_path.is_file():
        return 404, {"Content-Type": "text/plain"}, b"PMTiles archive not found"

    total_size = file_path.stat().st_size
    base_headers = {
        "Accept-Ranges": "bytes",
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "Range, Content-Type",
        "Access-Control-Expose-Headers": "Content-Range, Content-Length, Accept-Ranges",
        "Content-Type": "application/vnd.pmtiles",
    }

    if not range_header:
        data = file_path.read_bytes()
        base_headers["Content-Length"] = str(len(data))
        return 200, base_headers, data

    match = re.match(r"bytes=(\d+)-(\d*)", range_header.strip())
    if not match:
        return 416, base_headers, b"Invalid Range header"

    start = int(match.group(1))
    end = int(match.group(2)) if match.group(2) else total_size - 1
    end = min(end, total_size - 1)

    if start > end or start >= total_size:
        base_headers["Content-Range"] = f"bytes */{total_size}"
        return 416, base_headers, b"Requested Range Not Satisfiable"

    length = end - start + 1
    with file_path.open("rb") as fh:
        fh.seek(start)
        chunk = fh.read(length)

    base_headers["Content-Range"] = f"bytes {start}-{end}/{total_size}"
    base_headers["Content-Length"] = str(len(chunk))
    return 206, base_headers, chunk


def create_pmtiles_server(maps_dir: Path, host: str = "127.0.0.1", port: int = 8082) -> ThreadingHTTPServer:
    """Creates a lightweight local HTTP server serving `.pmtiles` files with Range support."""

    class PMTilesHandler(BaseHTTPRequestHandler):
        def do_OPTIONS(self) -> None:
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Range, Content-Type")
            self.end_headers()

        def do_GET(self) -> None:
            rel_path = self.path.lstrip("/").split("?")[0] or "region.pmtiles"
            target = (maps_dir / rel_path).resolve()
            if not str(target).startswith(str(maps_dir.resolve())):
                self.send_Error(403, "Forbidden")
                return

            status, headers, body = extract_byte_range(target, self.headers.get("Range"))
            self.send_response(status)
            for k, v in headers.items():
                self.send_header(k, v)
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format: str, *args: object) -> None:
            return

    return ThreadingHTTPServer((host, port), PMTilesHandler)

