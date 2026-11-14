"""Rebuild design/outline.html from a theme draft.

Usage (from the repo root, with Playwright's Chromium available):
  node design/tools/capture.js design/workbench.html <draft.json> /tmp/snapshots.json
  python3 design/tools/build_outline.py <draft.json> /tmp/snapshots.json
"""
import json
import sys

draft_path, snaps_path = sys.argv[1], sys.argv[2]
template = open("design/tools/outline.template.html").read()
workbench = open("design/workbench.html").read()
icons = workbench[workbench.index("const ICON_KIND = {"):workbench.index("const DOCK = [")]
# The mock launcher's styles come straight from the workbench, so captured screens always render the same.
css_start = workbench.index("/* ---------- Mock launcher (drawn in dp, scaled to fit) ---------- */")
mock_css = workbench[css_start:workbench.index("</style>", css_start)]
draft = json.load(open(draft_path))
proposal = json.load(open("core/designsystem/src/main/assets/theme/default.json"))
snaps = json.load(open(snaps_path))


def blob(obj):
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False).replace("</", "<\\/")


page = (template.replace("__DRAFT__", blob(draft)).replace("__PROPOSAL__", blob(proposal))
        .replace("__SNAPS__", blob(snaps)).replace("__ICONS__", icons).replace("/*__MOCK_CSS__*/", mock_css))
open("design/outline.html", "w").write(page)
print("wrote design/outline.html", len(page), "bytes")
