# SurvIA License Backend

Backend de licence pour **SurvIA**, déployable sur **Leapcell** (free tier).

## Stack
- **Runtime :** Node.js 20
- **Base de données :** PostgreSQL (Leapcell Free Tier)
- **Paiement :** Stripe Checkout
- **Cryptographie :** Ed25519 (`@noble/ed25519`)

## Déploiement Leapcell

1. Crée un nouveau service sur [leapcell.io](https://leapcell.io) → **New Service** → **GitHub** → repo `SurvIA`, dossier `survia-backend/`.
2. **Build Command :** `npm install`
3. **Start Command :** `node index.js`
4. **Port :** `8080`
5. Active la **base PostgreSQL gratuite** dans Leapcell et copie le `DATABASE_URL`.

## Configuration (Variables d'environnement)

Copie `.env.example` → `.env` et remplis :

| Variable | Description |
|---|---|
| `DATABASE_URL` | URL PostgreSQL Leapcell |
| `STRIPE_SECRET_KEY` | `sk_test_...` ou `sk_live_...` |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...` (depuis le dashboard Stripe) |
| `STRIPE_PRICE_ID` | ID du produit Stripe |
| `ED25519_PRIVATE_KEY_HEX` | Clé privée (générer ci-dessous) |

## Génération des clés Ed25519

```bash
npm install
npm run generate-keys
```

→ Copie `PRIVATE_KEY_HEX` dans les variables d'env Leapcell.
→ Copie `PUBLIC_KEY_B64` dans `LicenseManager.kt` → `PUBLIC_KEY_B64`.

## Migration de la base de données

```bash
npm run db:migrate
```

## Endpoints

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/webhook/stripe` | Webhook Stripe (checkout.session.completed) |
| `POST` | `/api/activate` | Activation token → HardwareID binding + certificat Ed25519 |
| `GET` | `/api/health` | Health check |

## Webhook Stripe

Dans le [dashboard Stripe](https://dashboard.stripe.com/webhooks), ajoute un webhook vers :
```
https://<ton-service>.leapcell.dev/webhook/stripe
```
Événement : `checkout.session.completed`

## Flux d'activation

```
Achat Stripe → webhook → token PENDING_ACTIVATION
     ↓
App SurvIA scanne QR Code (survia://activate?token=srv_live_...)
     ↓
POST /api/activate { token, hardware_id }
     ↓
Première activation → token BOUND + certificat Ed25519 retourné
     ↓
App sauvegarde license.json → LicenseManager vérifie offline ✅
```
