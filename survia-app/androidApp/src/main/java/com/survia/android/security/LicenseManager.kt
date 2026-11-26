package com.survia.android.security

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Moteur de licence Ed25519 (Phase 2.2) — 100% hors-ligne.
 *
 * L'app embarque uniquement la clé publique Ed25519.
 * À chaque démarrage, elle vérifie le fichier local license.json :
 *   { "hardware_id": "...", "issued_at": 0, "tier": "...", "signature": "..." }
 *
 * Si la signature Ed25519 est valide ET que hardware_id correspond à l'appareil,
 * l'accès est accordé sans aucune connexion réseau.
 *
 * NOTE : remplacer PUBLIC_KEY_B64 par la vraie clé publique Ed25519 en Base64
 * générée côté backend lors du déploiement Phase 3.
 */
object LicenseManager {

    // ⚠️ Remplacer par la clé publique Ed25519 (Base64 DER X.509) du serveur de licence
    // Laisser vide pendant les phases de développement : la vérification sera "bypass dev"
    private const val PUBLIC_KEY_B64 = ""

    // En mode développement (clé absente) : ne verrouille pas l'app
    private const val DEV_MODE = true  // Passer à false en production

    private const val LICENSE_FILENAME = "license.json"

    sealed class LicenseStatus {
        object Valid : LicenseStatus()
        object DevMode : LicenseStatus()
        object NotFound : LicenseStatus()
        object HardwareMismatch : LicenseStatus()
        object SignatureInvalid : LicenseStatus()
    }

    fun check(context: Context): LicenseStatus {
        // Mode développement : toujours valide
        if (DEV_MODE || PUBLIC_KEY_B64.isBlank()) return LicenseStatus.DevMode

        val file = java.io.File(context.filesDir, LICENSE_FILENAME)
        if (!file.exists()) return LicenseStatus.NotFound

        return try {
            val json = JSONObject(file.readText())
            val licenseHardwareId = json.getString("hardware_id")
            val issuedAt = json.getLong("issued_at")
            val tier = json.getString("tier")
            val signatureHex = json.getString("signature")

            // Vérifier que l'empreinte matérielle correspond à cet appareil
            val deviceHardwareId = HardwareBinding.getHardwareId(context)
            if (licenseHardwareId != deviceHardwareId) return LicenseStatus.HardwareMismatch

            // Vérifier la signature Ed25519
            val payload = """{"hardware_id":"$licenseHardwareId","issued_at":$issuedAt,"tier":"$tier"}"""
            val valid = verifyEd25519(payload.toByteArray(Charsets.UTF_8), signatureHex)
            if (!valid) return LicenseStatus.SignatureInvalid

            LicenseStatus.Valid
        } catch (_: Throwable) {
            LicenseStatus.SignatureInvalid
        }
    }

    fun saveLicenseJson(context: Context, licenseJson: String) {
        java.io.File(context.filesDir, LICENSE_FILENAME).writeText(licenseJson)
    }

    private fun verifyEd25519(payload: ByteArray, signatureHex: String): Boolean {
        return try {
            val pubKeyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
            val pubKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(pubKeyBytes))
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(pubKey)
            sig.update(payload)
            val sigBytes = signatureHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            sig.verify(sigBytes)
        } catch (_: Throwable) {
            false
        }
    }
}
