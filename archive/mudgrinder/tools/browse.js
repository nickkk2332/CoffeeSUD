// Browser side of grindsnap.py: logs in to the MUDGrinder, then answers one
// JSON request per input line with one JSON line:
//   {"cmd":"page","url":...}  -> {ok, html, info}   (info: forms and .cmvp references)
//   {"cmd":"get","url":...}   -> {ok, body}         (base64, for images and styles)
const { chromium } = require('playwright');
const readline = require('readline');
const FORM_JS = '(' + require('fs').readFileSync(process.argv[5], 'utf8') + ')()';

function out(o) { process.stdout.write(JSON.stringify(o) + '\n'); }

(async () => {
  const [base, login, password] = process.argv.slice(2);
  const browser = await chromium.launch();
  const ctx = await browser.newContext();
  const pg = await ctx.newPage();
  await pg.goto(base + '/index.cmvp');
  const loginHtml = await pg.content();
  await pg.fill('input[name=LOGIN]', login);
  await pg.fill('input[name=PASSWORD]', password);
  await Promise.all([pg.waitForNavigation(), pg.click('input[name=LOGINBUTTON]')]);
  out({ html: loginHtml, url: pg.url() });
  const rl = readline.createInterface({ input: process.stdin });
  for await (const line of rl) {
    const req = JSON.parse(line);
    if (req.cmd === 'quit') break;
    try {
      if (req.cmd === 'page') {
        await pg.goto(req.url, { waitUntil: 'load', timeout: 60000 });
        out({ ok: true, html: await pg.content(), info: await pg.evaluate(FORM_JS) });
      } else {
        const r = await ctx.request.get(req.url);
        out({ ok: r.ok(), body: (await r.body()).toString('base64') });
      }
    } catch (e) {
      out({ ok: false, error: String(e) });
    }
  }
  await browser.close();
})();
