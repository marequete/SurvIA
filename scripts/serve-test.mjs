import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');
const PORT = process.env.PORT || 8080;

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.db': 'application/octet-stream',
  '.zim': 'application/octet-stream'
};

const server = http.createServer((req, res) => {
  // CORS pour les tests locaux
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  let reqPath = req.url.split('?')[0];
  if (reqPath === '/' || reqPath === '') {
    reqPath = '/landing-page/index.html';
  }

  let filePath = path.join(ROOT_DIR, reqPath);

  // Sécurité anti directory traversal
  if (!filePath.startsWith(ROOT_DIR)) {
    res.writeHead(403);
    res.end('Accès refusé');
    return;
  }

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    // Repli vers index.html si non trouvé
    filePath = path.join(ROOT_DIR, 'landing-page', 'index.html');
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  try {
    const data = fs.readFileSync(filePath);
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(data);
    console.log(`[HTTP 200] ${req.method} ${req.url} -> ${path.relative(ROOT_DIR, filePath)}`);
  } catch (err) {
    res.writeHead(500);
    res.end(`Erreur serveur : ${err.message}`);
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`================================================================`);
  console.log(`  SERVEUR DE TEST BOUT EN BOUT SURVIA ACTIF                      `);
  console.log(`================================================================`);
  console.log(`  Local PC      : http://localhost:${PORT}`);
  console.log(`  Réseau Mobile : http://192.168.1.18:${PORT}`);
  console.log(`  QR Code cible : http://192.168.1.18:${PORT}`);
  console.log(`================================================================`);
  console.log(`Scannez le QR Code avec votre téléphone connecté au même Wi-Fi !`);
});
