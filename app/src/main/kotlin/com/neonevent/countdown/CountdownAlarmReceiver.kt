package com.neonevent.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class CountdownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = CountdownPrefs(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> schedule(context)

            ACTION_ALARM -> {
                val expectedKey = intent.getStringExtra(EXTRA_TARGET_KEY)
                if (!prefs.soundEnabled || expectedKey != prefs.targetKey()) return
                val now = System.currentTimeMillis()
                if (prefs.targetMillis(now) > now) {
                    schedule(context)
                    return
                }
                runCatching {
                    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 1400)
                    Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, 1800L)
                }
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.neonevent.countdown.ALARM"
        private const val EXTRA_TARGET_KEY = "target_key"
        private const val REQUEST_CODE = 7672

        fun schedule(context: Context) {
            val prefs = CountdownPrefs(context)
            val intent = Intent(context, CountdownAlarmReceiver::class.java).setAction(ACTION_ALARM)
                .putExtra(EXTRA_TARGET_KEY, prefs.targetKey())
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!prefs.soundEnabled) {
                manager.cancel(pending)
                return
            }
            val target = prefs.targetMillis()
            if (target <= System.currentTimeMillis()) {
                manager.cancel(pending)
                return
            }
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending) }
        }
    }
}
