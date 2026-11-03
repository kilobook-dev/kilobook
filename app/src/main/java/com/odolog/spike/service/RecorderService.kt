package com.odolog.spike.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.odolog.spike.MainActivity
import com.odolog.spike.graph
import com.odolog.spike.vehicle.VehicleMonitor

/**
 * Keeps trip recording alive while the screen is not visible (navigation in front, app window closed).
 *
 * Execution policy (Pleos "앱 구조 및 동작 모델"):
 * - Runs while background recording is enabled; started when the screen opens, stopped by the debug switch.
 * - Event-driven only: vehicle listeners, no polling. The only timer is the fallback idle check,
 *   which exists only while a trip is open and gear/parking brake cannot be read.
 */
class RecorderService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("오도로그: 주행 기록 중")
            .setContentText("주행거리와 기어 변화를 기록하고 있습니다")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Log.i(VehicleMonitor.TAG, "RecorderService: onCreate -> startForeground")
        graph.session.acquire(OWNER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Log.i(VehicleMonitor.TAG, "RecorderService: onDestroy")
        graph.session.release(OWNER)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RecorderService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }
}
