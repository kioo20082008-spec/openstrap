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
import org.openstrap.app.MainActivity
import org.openstrap.app.R
import org.openstrap.app.data.ailearn.AiLearning
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.AiLearnCopy
import java.time.LocalDate

/**
 * Says what the AI body learning learned today (docs/algorithms/self-check.md): one quiet notification a day, only when it
 * learned something or changed something, with an Undo button when it changed how Energy is worked out, and a tap that opens
 * the learning page. Low importance, so no sound or buzz. When it goes out, and that it is at most once a day and not in
 * quiet hours, is [AiLearning.notice]'s and the keeper's; this only shows it. The words are [AiLearnCopy]'s.
 */
class AiLearnNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(notice: AiLearning.Notice, day: LocalDate) {
        if (!manager.areNotificationsEnabled()) return
        // Android 13 brought the notification permission. Before it, the check would always read as denied.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AiLearnCopy.channelName(), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, OPEN_CODE,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SELF_CHECK, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openstrap)
            .setContentTitle(AiLearnCopy.noticeTitle())
            .setContentText(AiLearnCopy.noticeText(notice))
            .setStyle(NotificationCompat.BigTextStyle().bigText(AiLearnCopy.noticeBig(notice)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (notice.applied.isNotEmpty()) {
            val undo = PendingIntent.getBroadcast(
                context, UNDO_CODE,
                Intent(context, SelfCheckReceiver::class.java).setAction(SelfCheckReceiver.ACTION_UNDO_AI).putExtra(SelfCheckReceiver.EXTRA_DAY, day.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, Strings.app.get(R.string.selfcheck_undo), undo)
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val CHANNEL = "ai_learn"
        private const val OPEN_CODE = 7581
        private const val UNDO_CODE = 7582

        /** One notification, replaced each day. */
        const val NOTIFICATION_ID = 7580
    }
}
