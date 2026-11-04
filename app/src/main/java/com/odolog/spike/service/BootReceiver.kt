package com.odolog.spike.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import com.odolog.spike.AppLog
import com.odolog.spike.graph
import com.odolog.spike.vehicle.describe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S8: start recording when the car boots, without the driver opening the app.
 *
 * BOOT_COMPLETED is an exemption from the Android 12+ background start restriction, and specialUse is not
 * in the list of types a BOOT_COMPLETED receiver may not start (Android 15 behavior changes).
 * Not delivered while the app is in the stopped state (installed but never opened, or force-stopped).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED) return
        AppLog.i("BootReceiver: $action 수신 (${Process.myUserHandle()})")

        val result = if (!context.graph.settings.state.value.backgroundService) {
            "백그라운드 기록 꺼짐 -> 서비스 시작 안 함"
        } else {
            try {
                RecorderService.start(context, reason = "boot")
                "RecorderService.start(boot) -> ok"
            } catch (e: Exception) {
                "RecorderService.start(boot) -> ${e.describe()}"
            }
        }
        AppLog.i("BootReceiver: $result")
        BootRecord.save(context, "$action · $result")
    }
}

/** The last boot start result, kept across processes so the screen can show it later. */
object BootRecord {
    private const val PREFS = "boot_record"
    private const val KEY = "last"

    fun save(context: Context, text: String) {
        val at = SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA).format(Date())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, "$at · $text").apply()
    }

    fun last(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
