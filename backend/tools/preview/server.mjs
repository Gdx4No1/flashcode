import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 独立源预览：无需安装 npm 包；默认只监听本机。
const root = await fs.realpath(process.env.FLASHCODE_PREVIEW_ROOT || 'D:/tmp/flashcode-build/local-runtime/user-preview');
const publishedRoot = process.env.FLASHCODE_PUBLISHED_ROOT ? await fs.realpath(process.env.FLASHCODE_PUBLISHED_ROOT) : null;
const parentOrigin = new URL(process.env.FLASHCODE_PARENT_ORIGIN || 'http://127.0.0.1:5173').origin;
const port = Number(process.env.FLASHCODE_PREVIEW_PORT || 5175);
const bridge = await fs.readFile(fileURLToPath(new URL('./selection-bridge.js', import.meta.url)));
const mime = {'.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8', '.json':'application/json', '.svg':'image/svg+xml', '.png':'image/png', '.jpg':'image/jpeg', '.webp':'image/webp', '.woff2':'font/woff2'};
const escapeAttribute = value => value.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');
http.createServer(async (req,res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Security-Policy', "default-src 'self' data:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors " + parentOrigin);
  if (!['GET','HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
  try {
    const pathname = decodeURIComponent(new URL(req.url,'http://localhost').pathname);
    if (pathname === '/__flashcode/selection-bridge.js') {
      res.writeHead(200,{'Content-Type':mime['.js']}); res.end(req.method === 'HEAD' ? undefined : bridge); return;
    }
    const published = pathname.startsWith('/published/');
    const selectedRoot = published ? publishedRoot : root;
    const relativePath = published ? pathname.slice('/published'.length) : pathname;
    if (!selectedRoot || !/^\/\d+\//.test(relativePath) || pathname.includes('\\') || pathname.includes('\0')) throw Error();
    const candidate = path.resolve(selectedRoot,'.' + relativePath,pathname.endsWith('/') ? 'index.html' : '');
    const real = await fs.realpath(candidate);
    if (!real.startsWith(selectedRoot + path.sep) || !(await fs.stat(real)).isFile()) throw Error();
    let content = await fs.readFile(real);
    if (!published && path.extname(real) === '.html') {
      const script = '<script src="/__flashcode/selection-bridge.js" data-parent-origin="' + escapeAttribute(parentOrigin) + '"></script>';
      const html = content.toString('utf8');
      content = Buffer.from(/<head\b[^>]*>/i.test(html) ? html.replace(/<head\b[^>]*>/i, match => match + script) : script + html);
    }
    res.writeHead(200,{'Content-Type':mime[path.extname(real)] || 'application/octet-stream', 'Content-Length':content.length});
    res.end(req.method === 'HEAD' ? undefined : content);
  } catch { res.writeHead(404).end('Not found'); }
}).listen(port,'127.0.0.1',()=>console.log('Preview listening on http://127.0.0.1:' + port));
