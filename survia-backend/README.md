# SurvIA License Backend

Backend de licence pour **SurvIA**, déployable sur **Fly.io** (free tier) + **Neon** (PostgreSQL permanent gratuit).

## Stack
- **Runtime :** Node.js 20 (Docker, Fly.io `shared-cpu-1x` — always-on)
- **Base de données :** PostgreSQL Neon (free tier permanent, sans expiration)
- **Paiement :** Stripe Checkout
- **Cryptographie :** Ed25519 (`@noble/ed25519`)

## Pourquoi Fly.io + Neon ?

| Critère | Avantage |
|---|---|
| **Always-on** | `auto_stop_machines = false` → 0 cold start |
| **PostgreSQL permanent** | Neon ne supprime jamais les données (contrairement à Render 30j) |
| **Coût réel** | ~0 €/mois (couvert par \$5 crédits Fly.io mensuels) |
| **Usage commercial** | Autorisé sur les deux plateformes |

## Déploiement Fly.io

### Prérequis
```bash
# Installer Fly CLI
curl -L https://fly.io/install.sh | sh
fly auth login
```

### 1. Créer l'app Fly.io
```bash
cd survia-backend
fly launch --name survia-license --region cdg --no-deploy
```

### 2. Base de données Neon (gratuit, permanent)
1. Créer un compte sur [neon.tech](https://neon.tech)
2. **New Project** → région `eu-central-1` → copier le `DATABASE_URL`

### 3. Variables d'environnement Fly.io
```bash
fly secrets set DATABASE_URL="postgresql://..."
fly secrets set STRIPE_SECRET_KEY="sk_live_..."
fly secrets set STRIPE_WEBHOOK_SECRET="whsec_..."
fly secrets set ED25519_PRIVATE_KEY_HEX="..."
```

### 4. Déployer
```bash
fly deploy
```

### 5. Migration de la base
```bash
fly ssh console -C "node scripts/migrate.js"
# ou localement avec le DATABASE_URL dans .env :
npm run db:migrate
```

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
