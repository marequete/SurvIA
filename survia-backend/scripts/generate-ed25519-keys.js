/**
 * Génération de la paire de clés Ed25519 pour SurvIA.
 *
 * Usage : npm run generate-keys
 *
 * → Affiche PRIVATE_KEY_HEX (à mettre dans .env du backend)
 * → Affiche PUBLIC_KEY_HEX  (à encoder en Base64 DER et injecter dans LicenseManager.kt)
 *
 * ⚠️ La clé privée ne doit JAMAIS quitter le serveur backend.
 */
const { generateKeyPairSync } = require('crypto');

const { privateKey, publicKey } = generateKeyPairSync('ed25519');

const privHex = privateKey.export({ type: 'pkcs8', format: 'der' }).toString('hex');
const pubHex  = publicKey.export({ type: 'spki',  format: 'der' }).toString('hex');
const pubB64  = publicKey.export({ type: 'spki',  format: 'der' }).toString('base64');

console.log('\n========== SurvIA Ed25519 Key Pair ==========');
console.log('\n[.env backend] ED25519_PRIVATE_KEY_HEX=');
console.log(privHex);
console.log('\n[LicenseManager.kt] PUBLIC_KEY_B64 (Base64 DER X.509 / SPKI):');
console.log(pubB64);
console.log('\n=============================================');
console.log('⚠️  Conserve la clé privée de façon sécurisée. Ne la commite JAMAIS.');
