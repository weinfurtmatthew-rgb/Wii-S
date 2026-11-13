// Renders the Design Workbench with Draft 1 loaded and captures static HTML of both mock screens
// in two states, plus component specimens, for the Design Outline page.
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
  const page_html = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{margin:0}[hidden]{display:none!important}</style></head><body>${html}</body></html>`;
  const file = path.join(path.dirname(outPath), "workbench-draft.html");
  fs.writeFileSync(file, page_html);

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } });
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("file://" + file);
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
  await page.reload();
  await page.waitForTimeout(500);
  await page.evaluate(() => { document.getElementById("previewSound").checked = false; });

  const settle = () => page.waitForTimeout(1600);
  const grab = () => page.evaluate(() => ({
    top: document.getElementById("topScreen").innerHTML,
    bottom: document.getElementById("bottomScreen").innerHTML,
  }));

  // State A: Recently played, third tile focused.
  for (const k of ["ArrowRight", "ArrowRight"]) { await page.keyboard.press(k); await page.waitForTimeout(120); }
  await settle();
  const a = await grab();
  const parts = await page.evaluate(() => {
    const top = document.getElementById("topScreen");
    const q = (s) => top.querySelector(s);
    const tiles = [...top.querySelectorAll(".focusable[data-game]")];
    const focused = tiles.find((t) => t.querySelector(".tile-ring") && getComputedStyle(t.querySelector(".tile-ring")).opacity > 0.5);
    const rest = tiles.find((t) => t !== focused && t.dataset.game.startsWith("gc:Lantern"));
    const noArt = tiles.find((t) => t.dataset.game === "nds:Pocket Atlas");
    return {
      focused: focused ? focused.outerHTML : "",
      rest: rest ? rest.outerHTML : "",
      noArt: noArt ? noArt.outerHTML : "",
      tabActive: q('[data-tab="games"]').outerHTML,
      tabIdle: q('[data-tab="apps"]').outerHTML,
      shelfTitles: [...top.querySelectorAll(".m-shelf-title")].slice(0, 3).map((e) => e.outerHTML),
      hints: q(".m-hints") ? q(".m-hints").outerHTML : "",
      wordmark: q(".m-wordmark") ? q(".m-wordmark").outerHTML : "",
    };
  });

  // State B: Nintendo DS row, third tile focused (shows the centred row).
  for (const k of ["ArrowLeft", "ArrowLeft", "ArrowDown", "ArrowRight", "ArrowRight"]) { await page.keyboard.press(k); await page.waitForTimeout(120); }
  await settle();
  const b = await grab();

  fs.writeFileSync(outPath, JSON.stringify({ a, b, parts }, null, 0));
  console.log("captured", Object.fromEntries(Object.entries(parts).map(([k, v]) => [k, Array.isArray(v) ? v.length : v.length])), "A", a.top.length, "B", b.top.length, "errors", errors);
  await browser.close();
})();
