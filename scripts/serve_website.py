"""Serves website/ locally the way Cloudflare does: /privacy is privacy.html, /zh/ is zh/index.html."""
import functools
import http.server
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "website")


class Handler(http.server.SimpleHTTPRequestHandler):
    def send_head(self):
        path = self.path.split("?", 1)[0].split("#", 1)[0]
        local = os.path.join(ROOT, path.lstrip("/"))
        if not path.endswith("/") and not os.path.exists(local) and os.path.exists(local + ".html"):
            self.path = path + ".html"
        return super().send_head()


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8321
http.server.ThreadingHTTPServer(("", port), functools.partial(Handler, directory=ROOT)).serve_forever()
