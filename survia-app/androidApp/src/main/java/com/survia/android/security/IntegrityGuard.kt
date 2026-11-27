package com.survia.android.security

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.survia.android.BuildConfig
import java.io.File
import java.security.MessageDigest

/**
 * Garde d'intégrité runtime (Phase 1.2) :
 * - Vérification de la signature Keystore (anti-repackaging)
 * - Détection root / instrumentation (su, Magisk, Frida)
 * - Détection d'émulateur (QEMU, Goldfish, Bluestacks)
 */
object IntegrityGuard {

    // Injecté automatiquement par Gradle depuis la variable d'env CI RELEASE_CERT_SHA256
    private val EXPECTED_RELEASE_CERT_SHA256: String
        get() = BuildConfig.EXPECTED_RELEASE_CERT_SHA256

    fun enforceIntegrity(activity: Activity): Boolean {
        if (EXPECTED_RELEASE_CERT_SHA256.isNotBlank() &&
            !verifySigningCertificate(activity, EXPECTED_RELEASE_CERT_SHA256)
        ) {
            activity.finishAffinity()
            return false
        }
        if (isRootedOrInstrumented() || isEmulator()) {
            activity.finishAffinity()
            return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    fun verifySigningCertificate(context: Context, expectedSha256Hex: String): Boolean {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                info.signatures
            } ?: return false

            val md = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                val hash = md.digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                hash.equals(expectedSha256Hex, ignoreCase = true)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isRootedOrInstrumented(): Boolean {
        val suspiciousPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/sbin/.magisk",
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server"
        )
        if (suspiciousPaths.any { File(it).exists() }) return true

        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) return true

        return false
    }

    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("vbox86p")
    }
}
