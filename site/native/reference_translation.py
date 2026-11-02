"""Apply one intentional catalog translation to historical HTML test oracles.

The old generated references predate this catalog correction. This helper only
adapts that known title, not arbitrary page content or translation quality.
"""
from html import escape
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KEY = 'e01d4e101e1c7895d762666ebdc4b5afcdd6371cd2114cc03183dbad5d96d93c'


def reference_title(html, tag):
    old = json.loads((ROOT / 'site/i18n/en.json').read_text())[KEY]
    new = json.loads((ROOT / 'site/i18n' / (tag + '.json')).read_text())[KEY]
    return html.replace(escape(old, quote=True), escape(new, quote=True))
