package org.openstrap.app.device

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.first
import org.openstrap.app.MainActivity
import org.openstrap.app.R
import org.openstrap.app.data.selfcheck.SelfLearning
import org.openstrap.app.data.settings.SettingsStore
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.SelfCheckCopy
import java.time.LocalTime

/**
 * Says what the self-check found or adjusted (docs/algorithms/self-check.md): one quiet notification for an incident, with
 * an Undo button when something was changed, and a tap that opens the self-learning page. Low importance, so no sound or
 * buzz. The policy (at most two a day, once per incident, not in quiet hours) is [SelfLearning.nextNotice]'s; this only
 * shows it. The words are [SelfCheckCopy]'s, the channel's name too, which follows a change of language.
 */
class SelfCheckNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(i: SelfLearning.Incident) {
        if (!manager.areNotificationsEnabled()) return
        // Android 13 brought the notification permission. Before it, the check would always read as denied.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, SelfCheckCopy.channelName(), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, OPEN_CODE,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SELF_CHECK, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = SelfCheckCopy.noticeText(i)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openstrap)
            .setContentTitle(SelfCheckCopy.noticeTitle(i))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(text) + SelfCheckCopy.adjustments(i)).joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (i.status == SelfLearning.Status.Applied) {
            val undo = PendingIntent.getBroadcast(
                context, i.id,
                Intent(context, SelfCheckReceiver::class.java).setAction(SelfCheckReceiver.ACTION_UNDO).putExtra(SelfCheckReceiver.EXTRA_ID, i.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, Strings.app.get(R.string.selfcheck_undo), undo)
        }
        manager.notify(notificationId(i.id), builder.build())
    }

    /**
     * True while a notice should wait: the user's quiet hours, Do Not Disturb, or the night (before 8 and from 22, as the
     * coach buzzes only by day). The notice is not lost: the keeper asks again on its next look.
     */
    suspend fun quiet(settings: SettingsStore, now: LocalTime = LocalTime.now()): Boolean {
        val hours = settings.quietHours.first()
        val dnd = manager.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        return hours.covers(now.hour * 60 + now.minute) || dnd || now.hour < DAY_START || now.hour >= DAY_END
    }

    companion object {
        private const val CHANNEL = "self_check"
        private const val OPEN_CODE = 7520
        private const val ID_BASE = 7530
        private const val ID_SPAN = 50
        private const val DAY_START = 8
        private const val DAY_END = 22

        /** One notification an incident, and a few kept at a time. */
        fun notificationId(incident: Int): Int = ID_BASE + incident % ID_SPAN
    }
}
