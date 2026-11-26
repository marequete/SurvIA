/**
 * Génération de la paire de clés Ed25519 pour SurvIA.
 *
 * Usage : npm run generate-keys
 *
 * → Affiche PRIVATE_KEY_RAW_HEX (32 octets bruts — pour @noble/ed25519 dans index.js)
 * → Affiche PUBLIC_KEY_B64       (DER SPKI X.509 en Base64 — pour LicenseManager.kt)
 *
 * ⚠️ La clé privée ne doit JAMAIS quitter le serveur backend.
 */
const { generateKeyPairSync } = require('crypto');

const { privateKey, publicKey } = generateKeyPairSync('ed25519');

// PKCS#8 DER pour Ed25519 : 16 octets d'en-tête + 32 octets clé brute
const pkcs8Der = privateKey.export({ type: 'pkcs8', format: 'der' });
const rawPrivHex = pkcs8Der.slice(16).toString('hex');  // 32 octets bruts pour @noble/ed25519

const pubB64 = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');

console.log('\n========== SurvIA Ed25519 Key Pair ==========');
console.log('\n[Fly.io secret] ED25519_PRIVATE_KEY_HEX= (32 octets bruts, pour @noble/ed25519)');
console.log(rawPrivHex);
console.log('\n[LicenseManager.kt] PUBLIC_KEY_B64 (Base64 DER X.509 / SPKI):');
console.log(pubB64);
console.log('\n=============================================');
console.log('⚠️  Conserve la clé privée de façon sécurisée. Ne la commite JAMAIS.');
