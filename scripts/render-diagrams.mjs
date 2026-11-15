#!/usr/bin/env node
/**
 * 把 docs/diagrams/*.mmd 渲染成同名的 .svg 与 .png。
 *
 * 依赖：playwright-core（用本机已安装的 Chrome）+ mermaid
 *   npm i -D playwright-core mermaid
 *
 * 用法：
 *   node scripts/render-diagrams.mjs                # 渲染全部
 *   node scripts/render-diagrams.mjs overview       # 只渲染名字包含 overview 的
 */
import fs from 'fs';
import path from 'path';
import { pathToFileURL } from 'url';
import { createRequire } from 'module';

// 依赖解析放到 createRequire 里，走 CJS 解析（NODE_PATH 也生效），
// 避免 ESM 的 bare specifier 只认 node_modules 顶层安装。
const require = createRequire(pathToFileURL(path.resolve('package.json')).href);
let chromium, MERMAID;
try {
  ({ chromium } = require('playwright-core'));
  MERMAID = require.resolve('mermaid/dist/mermaid.min.js');
} catch (e) {
  console.error('缺少依赖：npm i -D playwright-core mermaid（或设置 NODE_PATH 指向已安装的 node_modules）');
  console.error(String(e.message).split(/\r?\n/)[0]);
  process.exit(1);
}

const DIR = path.resolve('docs/diagrams');
const filter = process.argv[2] || '';

const files = fs.readdirSync(DIR).filter((f) => f.endsWith('.mmd') && f.includes(filter));
if (!files.length) { console.error('没有匹配的 .mmd 文件'); process.exit(1); }

const browser = await chromium.launch({ channel: 'chrome', headless: true });

for (const file of files) {
  const src = fs.readFileSync(path.join(DIR, file), 'utf8');
  const stem = file.replace(/\.mmd$/, '');
  const tmp = path.join(DIR, `.${stem}.render.html`);

  fs.writeFileSync(tmp, `<!DOCTYPE html><html><head><meta charset="utf-8">
<style>
  body{margin:0;padding:28px;background:#fff;display:inline-block;
       font-family:-apple-system,"Segoe UI","Microsoft YaHei",sans-serif}
  #c svg{max-width:none!important}
</style></head><body>
<div id="c"><pre class="mermaid">${src.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</pre></div>
<script src="${pathToFileURL(MERMAID).href}"></script>
<script>
  mermaid.initialize({startOnLoad:true, theme:'base', themeVariables:{
    fontFamily:'-apple-system,"Segoe UI","Microsoft YaHei",sans-serif', fontSize:'15px',
    primaryColor:'#EEF2FF', primaryBorderColor:'#6366F1', primaryTextColor:'#1E1B4B',
    lineColor:'#94A3B8', secondaryColor:'#F1F5F9', tertiaryColor:'#FFF7ED',
    clusterBkg:'#FAFAFA', clusterBorder:'#CBD5E1'
  }, flowchart:{curve:'basis', htmlLabels:true, nodeSpacing:42, rankSpacing:52}});
</script></body></html>`);

  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 }, deviceScaleFactor: 2 });
  await page.goto(pathToFileURL(tmp).href, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);

  const svg = await page.evaluate(() => document.querySelector('#c svg')?.outerHTML || null);
  if (!svg) { console.error(`✗ ${stem} 渲染失败`); await page.close(); fs.unlinkSync(tmp); continue; }
  fs.writeFileSync(path.join(DIR, `${stem}.svg`), '<?xml version="1.0" encoding="UTF-8"?>\n' + svg, 'utf8');

  // 按 viewBox 自然尺寸定格，再截图（否则会缩成容器宽度）
  const vb = await page.evaluate(() => {
    const s = document.querySelector('#c svg');
    const v = s.viewBox.baseVal;
    const w = Math.ceil(v.width), h = Math.ceil(v.height);
    s.setAttribute('width', w); s.setAttribute('height', h);
    s.style.maxWidth = 'none'; s.style.width = w + 'px'; s.style.height = h + 'px'; s.style.display = 'block';
    return { w, h };
  });
  await page.setViewportSize({ width: vb.w + 64, height: vb.h + 64 });
  await page.waitForTimeout(500);
  await page.locator('#c svg').screenshot({ path: path.join(DIR, `${stem}.png`) });

  console.log(`✓ ${stem}  ${vb.w}×${vb.h}`);
  await page.close();
  fs.unlinkSync(tmp);
}

await browser.close();
