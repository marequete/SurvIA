/**
 * SurvIA License Backend — index.js
 * Stack : Express + PostgreSQL (Leapcell Free) + Stripe + Ed25519 (@noble/ed25519)
 *
 * Endpoints :
 *   POST /webhook/stripe      — Webhook Stripe checkout.session.completed
 *   POST /api/activate        — Activation unique (token → hardware_id binding)
 *   GET  /api/health          — Health check Leapcell
 */

require('dotenv').config();
const express  = require('express');
const { Pool } = require('pg');
const Stripe   = require('stripe');
const ed       = require('@noble/ed25519');
const crypto   = require('crypto');

// ─── Configuration ───────────────────────────────────────────────────────────
const app    = express();
const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
const pool   = new Pool({ connectionString: process.env.DATABASE_URL, ssl: { rejectUnauthorized: false } });

const PRIVATE_KEY_HEX = process.env.ED25519_PRIVATE_KEY_HEX;
const PORT            = process.env.PORT || 8080;

// ─── Middleware ───────────────────────────────────────────────────────────────
// IMPORTANT : le webhook Stripe a besoin du body brut (raw Buffer)
app.use('/webhook/stripe', express.raw({ type: 'application/json' }));
app.use(express.json());

// ─── Health check ─────────────────────────────────────────────────────────────
app.get('/api/health', (_, res) => res.json({ status: 'ok', service: 'survia-license' }));

// ─── POST /webhook/stripe ─────────────────────────────────────────────────────
// Stripe envoie l'événement checkout.session.completed après un paiement validé.
// On génère un token d'activation à usage unique et on l'enregistre en base.
app.post('/webhook/stripe', async (req, res) => {
  let event;
  try {
    event = stripe.webhooks.constructEvent(
      req.body,
      req.headers['stripe-signature'],
      process.env.STRIPE_WEBHOOK_SECRET
    );
  } catch (err) {
    console.error('Stripe signature error:', err.message);
    return res.status(400).send(`Webhook Error: ${err.message}`);
  }

  if (event.type === 'checkout.session.completed') {
    const session = event.data.object;
    const token   = 'srv_live_' + crypto.randomBytes(32).toString('hex');

    await pool.query(
      `INSERT INTO licenses (token, stripe_session_id, status, bound_hardware_id, issued_at)
       VALUES ($1, $2, 'PENDING_ACTIVATION', NULL, NOW())
       ON CONFLICT (stripe_session_id) DO NOTHING`,
      [token, session.id]
    );

    console.log(`✅ License token created for session ${session.id}: ${token.slice(0, 20)}...`);
  }

  res.json({ received: true });
});

// ─── POST /api/activate ──────────────────────────────────────────────────────
// Corps attendu : { "token": "srv_live_...", "hardware_id": "sha256hex..." }
//
// Règles :
//   - bound_hardware_id == NULL → première activation : on bind et on signe
//   - bound_hardware_id == hardware_id → même appareil, réémission du certificat
//   - bound_hardware_id != hardware_id → 403 Forbidden
app.post('/api/activate', async (req, res) => {
  const { token, hardware_id } = req.body;

  if (!token || !hardware_id) {
    return res.status(400).json({ error: 'token and hardware_id required' });
  }

  const { rows } = await pool.query(
    'SELECT * FROM licenses WHERE token = $1',
    [token]
  );

  if (rows.length === 0) {
    return res.status(404).json({ error: 'Token not found' });
  }

  const license = rows[0];

  if (license.status === 'PENDING_ACTIVATION' || license.bound_hardware_id === null) {
    // Première activation — on bind le hardware_id
    await pool.query(
      `UPDATE licenses SET bound_hardware_id = $1, status = 'BOUND', activated_at = NOW()
       WHERE token = $2`,
      [hardware_id, token]
    );
    return res.json({ certificate: await signLicense(hardware_id, 'FULL_OFFLINE_PRO') });
  }

  if (license.bound_hardware_id === hardware_id) {
    // Même appareil — réinstallation autorisée
    return res.json({ certificate: await signLicense(hardware_id, 'FULL_OFFLINE_PRO') });
  }

  // Appareil différent — licence déjà consommée
  return res.status(403).json({ error: 'License already bound to another device' });
});

// ─── Signature Ed25519 ────────────────────────────────────────────────────────
async function signLicense(hardwareId, tier) {
  const issuedAt = Math.floor(Date.now() / 1000);
  const payload  = JSON.stringify({ hardware_id: hardwareId, issued_at: issuedAt, tier });
  // @noble/ed25519 v2 attend une clé brute 32 octets (pas PKCS#8)
  // La variable d'env contient uniquement les 32 octets bruts (hex 64 chars)
  const privKey  = Buffer.from(PRIVATE_KEY_HEX, 'hex');
  const msgBytes = Buffer.from(payload, 'utf8');
  const sigBytes = await ed.sign(msgBytes, privKey);
  const signature = Buffer.from(sigBytes).toString('hex');

  return JSON.stringify({ hardware_id: hardwareId, issued_at: issuedAt, tier, signature });
}

// ─── Démarrage ────────────────────────────────────────────────────────────────
app.listen(PORT, () => console.log(`SurvIA License Server running on port ${PORT}`));
