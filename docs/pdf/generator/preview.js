'use strict';
// Developer helper: renders selected PDF pages to PNG using the installed Edge (headed, because the PDF viewer needs it).
// Usage: node preview.js <pdf> <outDir> <page> [page...]   (requires frontend/node_modules/@playwright/test)
const path = require('path');
const fs = require('fs');
const { chromium } = require(path.resolve(__dirname, '../../../frontend/node_modules/@playwright/test'));
(async () => {
  const [pdf, outDir, ...pages] = process.argv.slice(2);
  fs.mkdirSync(outDir, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge', headless: false, args: ['--window-size=1100,1500'] });
  const page = await browser.newPage({ viewport: { width: 1000, height: 1400 } });
  for (const p of pages) {
    await page.goto('about:blank');
    await page.goto('file:///' + path.resolve(pdf).replace(/\\/g, '/') + `#page=${p}&zoom=100&toolbar=0&navpanes=0`);
    await page.waitForTimeout(4000);
    await page.screenshot({ path: path.join(outDir, `p${p}.png`) });
  }
  await browser.close();
})();
