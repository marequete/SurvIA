package com.survia.android.security

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Empreinte matérielle unique (Phase 2.1) :
 * HardwareID = SHA-256(Build.FINGERPRINT + ANDROID_ID + Widevine.DRM_DEVICE_UNIQUE_ID)
 * Empêche le clonage d'une licence d'un appareil vers un autre.
 */
object HardwareBinding {

    private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    @SuppressLint("HardwareIds")
    fun getHardwareId(context: Context): String {
        val fingerprint = Build.FINGERPRINT ?: "unknown-fp"
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-aid"
        val widevineId = getWidevineDeviceId()

        val raw = "$fingerprint|$androidId|$widevineId"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getWidevineDeviceId(): String {
        var mediaDrm: MediaDrm? = null
        return try {
            mediaDrm = MediaDrm(WIDEVINE_UUID)
            val bytes = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            "no-widevine"
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    mediaDrm?.close()
                } else {
                    @Suppress("DEPRECATION")
                    mediaDrm?.release()
                }
            } catch (_: Throwable) {}
        }
    }
}
