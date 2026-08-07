package com.phantomcode.app.data.vm

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
import com.phantomcode.app.MainActivity

/**
 * Foreground Service da VM (T23 · §12.3): mantém o processo QEMU vivo quando o
 * app vai para o background, com notificação persistente "Phantom-Code ·
 * ambiente Linux ativo". Sem isso, o Android pode matar a VM.
 */
class VmForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Botão "Parar sessão" da notificação → encerra a VM.
            QemuManager.instance?.stop()
            return START_NOT_STICKY
        }
        startAsForeground()
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = createChannel()
        val notification = buildNotification(channelId)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createChannel(): String {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ambiente Linux (VM)",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém o Linux rodando em segundo plano"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    private fun buildNotification(channelId: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, VmForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Phantom-Code")
            .setContentText("Ambiente Linux ativo — toque para abrir")
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Parar sessão",
                    stopPi,
                ),
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        // O processo QEMU pode continuar; se o app chamar stop() ele é encerrado.
    }

    companion object {
        private const val CHANNEL_ID = "phantom_vm"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.phantomcode.app.action.STOP_VM"

        /** Inicia o FGS (API 26+ usa startForegroundService). */
        fun start(context: Context) {
            val intent = Intent(context, VmForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VmForegroundService::class.java))
        }
    }
}
