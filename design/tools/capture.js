// Renders the Design Workbench with a theme draft loaded and captures static HTML of both mock
// screens in three Draft 2 states, plus component specimens, for the Design Outline page.
// Usage: node design/tools/capture.js design/workbench.html <draft.json> <out.json>
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

(async () => {
  const [workbenchPath, draftPath, outPath] = process.argv.slice(2);
  let html = fs.readFileSync(workbenchPath, "utf8");
  const draft = fs.readFileSync(draftPath, "utf8");
  const open = '<script type="application/json" id="proposal">';
  const start = html.indexOf(open) + open.length;
  const end = html.indexOf("</script>", start);
  html = html.slice(0, start) + JSON.stringify(JSON.parse(draft)) + html.slice(end);
  const file = path.join(path.dirname(outPath), "workbench-capture.html");
  fs.writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}[hidden]{display:none!important}</style></head><body>${html}</body></html>`);

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("file://" + file);
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
  await page.reload();
  await page.waitForTimeout(600);
  await page.evaluate(() => { document.getElementById("previewSound").checked = false; });
  const settle = () => page.waitForTimeout(1600);
  const keys = async (...ks) => { for (const k of ks) { await page.keyboard.press(k); await page.waitForTimeout(140); } };
  const grab = () => page.evaluate(() => ({ top: document.getElementById("topScreen").innerHTML, bottom: document.getElementById("bottomScreen").innerHTML }));

  // A: home, Jump back in focused.
  await settle();
  const a = await grab();
  const partsA = await page.evaluate(() => {
    const top = document.getElementById("topScreen"), q = (s) => top.querySelector(s);
    return {
      hero: q("[data-hero]").outerHTML,
      systemNds: q('[data-system="nds"]').outerHTML,
      systemAll: q('[data-system="all"]').outerHTML,
      recentRest: q(".m-track [data-game]").outerHTML,
      header: q(".m-header").outerHTML,
      caption: document.getElementById("mCaption").innerHTML,
      dock: document.querySelector("#bottomScreen .dock")?.outerHTML || "",
    };
  });

  // B: Nintendo DS system tile focused (found by data attribute, so system order doesn't matter).
  await page.evaluate(() => { const i = focusables.findIndex((el) => el.dataset.system === "nds"); setFocus(i, false); });
  await settle();
  const b = await grab();
  const captionB = await page.evaluate(() => document.getElementById("mCaption").innerHTML);

  // C: inside Nintendo DS, third game focused.
  await page.evaluate(() => activate());
  await page.waitForTimeout(500);
  await page.evaluate(() => { const i = focusables.findIndex((el) => el.dataset.game === "nds:Garden Days"); setFocus(i, false); });
  await settle();
  const c = await grab();
  const partsC = await page.evaluate(() => {
    const top = document.getElementById("topScreen");
    const tiles = [...top.querySelectorAll("[data-game]")];
    const focused = tiles.find((t) => t.style.zIndex === "3");
    return {
      gridFocused: focused ? focused.outerHTML : "",
      gridRest: tiles.find((t) => t !== focused && t.dataset.game === "nds:Sky Mail")?.outerHTML || "",
      noArt: tiles.find((t) => t.dataset.game === "nds:Pocket Atlas")?.outerHTML || "",
      caption: document.getElementById("mCaption").innerHTML,
    };
  });

  fs.writeFileSync(outPath, JSON.stringify({ a, b, c, parts: { ...partsA, captionB, ...partsC } }));
  console.log("captured A/B/C", a.top.length, b.top.length, c.top.length, "parts", Object.keys({ ...partsA, ...partsC }).length, "errors", errors);
  await browser.close();
})();
