package com.odolog.spike.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.odolog.spike.AppLog
import com.odolog.spike.MainActivity
import com.odolog.spike.graph
import com.odolog.spike.vehicle.describe

/**
 * Keeps trip recording alive while the screen is not visible (navigation in front, app window closed).
 *
 * Execution policy (Pleos "앱 구조 및 동작 모델"):
 * - Runs while background recording is enabled. Started when the screen opens or the device boots,
 *   stopped by the debug switch.
 * - Event-driven only: vehicle listeners, no polling. The only timer is the fallback idle check,
 *   which exists only while a trip is open and gear/parking brake cannot be read.
 */
class RecorderService : Service() {
    private var recording = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted the service after killing the process (START_STICKY).
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "system restart"
        AppLog.i("RecorderService: onStartCommand(reason=$reason)")
        if (recording) return START_STICKY

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            AppLog.i("RecorderService: startForeground(specialUse) -> ok")
        } catch (e: Exception) {
            // Shown as-is (for example ForegroundServiceStartNotAllowedException); no other start path is tried.
            AppLog.i("RecorderService: startForeground(specialUse) -> ${e.describe()}")
            stopSelf()
            return START_NOT_STICKY
        }
        recording = true
        graph.session.acquire(OWNER)
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.i("RecorderService: onDestroy")
        if (recording) graph.session.release(OWNER)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_recent_history)
        .setContentTitle("오도로그: 주행 기록 중")
        .setContentText("주행거리와 기어 변화를 기록하고 있습니다")
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .setOngoing(true)
        .build()

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "주행 기록", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val OWNER = "service"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_REASON = "reason"

        /** Throws what startForegroundService throws (e.g. ForegroundServiceStartNotAllowedException). */
        fun start(context: Context, reason: String) {
            val intent = Intent(context, RecorderService::class.java).putExtra(EXTRA_REASON, reason)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }
}
