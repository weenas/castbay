#!/usr/bin/env python3
"""Builds the website's pages (website/*.html) from site/.

Every page shares one shell, site/templates/page.html: the <head>, the header with its
navigation and language link, and the footer. Each page keeps only its own <main> in
site/pages/<lang>/<name>.html, after a comment giving its title and description:

    <!--
    title: User guide – CastBay
    description: How to use CastBay: ...
    structured-data: yes      (the home pages: schema.org data for search engines)
    -->
    <main class="wrap doc">...</main>

Words in the shell come from site/strings.json. The output is committed, so Cloudflare
publishes website/ as it is; CI runs this with --check to catch an edit made to website/
directly, or to site/ without rebuilding. Standard library only.

    python3 scripts/build_website.py           # write website/*.html
    python3 scripts/build_website.py --check   # exit 1 if they are out of date
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
OUT = ROOT / "website"
BASE_URL = "https://castbay.weenas.com"
LANGS = ["en", "zh"]
# Order of the pages in the footer (the page being shown is left out).
PAGES = ["index", "guide", "tech", "privacy"]


def page_path(lang, name, strings):
    """The page's URL path, e.g. "/", "/guide", "/zh/", "/zh/guide"."""
    prefix = strings[lang]["prefix"]
    return prefix if name == "index" else prefix + name


def read_page(path):
    text = path.read_text(encoding="utf-8")
    match = re.match(r"<!--\n(.*?)\n-->\n", text, re.S)
    if not match:
        sys.exit(f"{path}: expected a <!-- title: ... --> comment first")
    meta = dict(line.split(": ", 1) for line in match.group(1).splitlines())
    return meta, text[match.end():].rstrip("\n")


def structured_data(lang, meta, url, s):
    data = {
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        "name": "CastBay",
        "alternateName": "映湾",
        "url": url,
        "description": meta["description"],
        "inLanguage": s["html_lang"],
        "applicationCategory": "MultimediaApplication",
        "operatingSystem": "Android TV, Google TV (Android 8.0 or later)",
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
        "downloadUrl": "https://github.com/weenas/castbay/releases/latest/download/CastBay.apk",
        "softwareHelp": "https://github.com/weenas/castbay",
        "license": "https://www.gnu.org/licenses/gpl-3.0.html",
        "isAccessibleForFree": True,
        "image": BASE_URL + "/assets/og.jpg?v=2",
    }
    body = json.dumps(data, ensure_ascii=False, indent=2)
    return f'  <script type="application/ld+json">\n{body}\n  </script>\n'


def nav(lang, name, strings):
    s = strings[lang]
    if name == "index":
        # The home page links to its own sections.
        items = [("#features", s["features"]), ("#start", s["guide"]), ("#faq", s["faq"]),
                 (page_path(lang, "tech", strings), s["tech"])]
    else:
        items = [(page_path(lang, page, strings), s[label])
                 for page, label in [("index", "home"), ("guide", "guide"), ("tech", "tech")]]
    items.append(("https://github.com/weenas/castbay", "GitHub"))
    current = page_path(lang, name, strings)
    marked = ' aria-current="page"'
    return "".join(
        f'      <a href="{href}"{marked if href == current else ""}>{text}</a>\n'
        for href, text in items)


def footer_links(lang, name, strings):
    s = strings[lang]
    labels = {"index": "home", "guide": "guide", "tech": "tech", "privacy": "privacy"}
    items = [(page_path(lang, page, strings), s[labels[page]]) for page in PAGES if page != name]
    items += [("https://github.com/weenas/castbay/releases", s["releases"]),
              ("https://github.com/weenas/castbay", "GitHub")]
    return "".join(f'      <a href="{href}">{text}</a>\n' for href, text in items)


def render(lang, name, template, strings):
    s = strings[lang]
    other = next(l for l in LANGS if l != lang)
    meta, main = read_page(SITE / "pages" / lang / f"{name}.html")
    url = BASE_URL + page_path(lang, name, strings)
    values = {
        "html_lang": s["html_lang"],
        "title": meta["title"],
        "description": meta["description"],
        "url": url,
        "url_en": BASE_URL + page_path("en", name, strings),
        "url_zh": BASE_URL + page_path("zh", name, strings),
        "og_locale": s["og_locale"],
        "og_locale_alt": strings[other]["og_locale"],
        "structured_data": structured_data(lang, meta, url, s) if meta.get("structured-data") == "yes" else "",
        "home": s["prefix"],
        "nav": nav(lang, name, strings),
        "theme_title": s["theme_title"],
        "theme_to_light": s["theme_to_light"],
        "theme_to_dark": s["theme_to_dark"],
        "other_url": page_path(other, name, strings),
        "other_hreflang": strings[other]["html_lang"],
        "other_lang": other,
        "other_label": strings[other]["label"],
        "main": main,
        "copyright": s["copyright"],
        "trademarks": s["trademarks"],
        "footer_links": footer_links(lang, name, strings),
    }

    def fill(match):
        key = match.group(1)
        if key not in values:
            sys.exit(f"page.html: unknown slot {{{{{key}}}}}")
        return values[key]

    return re.sub(r"\{\{(\w+)\}\}", fill, template)


def main():
    check = "--check" in sys.argv[1:]
    strings = json.loads((SITE / "strings.json").read_text(encoding="utf-8"))
    # The template's first line is a comment about itself, not part of the pages.
    template = (SITE / "templates" / "page.html").read_text(encoding="utf-8").split("\n", 1)[1]
    stale = []
    for lang in LANGS:
        for name in PAGES:
            out = OUT / strings[lang]["prefix"].strip("/") / f"{name}.html"
            html = render(lang, name, template, strings)
            if out.exists() and out.read_text(encoding="utf-8") == html:
                continue
            if check:
                stale.append(out.relative_to(ROOT))
            else:
                out.write_text(html, encoding="utf-8")
                print(f"wrote {out.relative_to(ROOT)}")
    if stale:
        print("Out of date with site/ (run python3 scripts/build_website.py):")
        for path in stale:
            print(f"  {path}")
        sys.exit(1)


if __name__ == "__main__":
    main()
