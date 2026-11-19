package com.survia.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.survia.android.MainActivity

/**
 * Service de premier plan Android (Foreground Service) garantissant que :
 * 1. Le modèle Gemma 4 E4B IT reste chargé et chaud en mémoire RAM virtuelle (mmap).
 * 2. L'Android LowMemoryKiller (LMK) ne détruit pas le processus quand l'utilisateur
 *    ouvre l'application photo ou consulte Organic Maps.
 */
class GemmaForegroundService : Service {
    constructor() : super()

    companion object {
        const val CHANNEL_ID = "survia_active_sentinel_channel"
        const val NOTIFICATION_ID = 4042

        fun startSentinel(context: Context) {
            val intent = Intent(context, GemmaForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopSentinel(context: Context) {
            val intent = Intent(context, GemmaForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY permet à l'OS de relancer le service si une mémoire extrême l'avait interrompu
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sentinelle de Survie SurvIA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient l'agent d'urgence et le modèle d'IA Gemma 4 prêts en mémoire locale sans réseau."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SurvIA • Sentinelle Active (100% Hors-Ligne)")
            .setContentText("Modèle Gemma 4 en veille rapide • Capteurs GPS et vision prêts")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
