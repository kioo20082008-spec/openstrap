package org.openstrap.app.device

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.selfcheck.SelfLearning
import java.time.LocalDate

/**
 * The Undo button of a self-check notification: takes that incident's change back and clears the notification. The AI body
 * learning's notification has one too ([ACTION_UNDO_AI]): it takes back what the AI changed that day.
 */
class SelfCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UNDO -> undo(context, intent)
            ACTION_UNDO_AI -> undoAi(context, intent)
        }
    }

    private fun undo(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, NONE)
        if (id == NONE) return
        val container = (context.applicationContext as OpenStrapApp).container
        val pending = goAsync()
        launchForBroadcast(container.appScope, "self-check: undo", pending::finish) {
            container.settings.updateSelfCheck { SelfLearning.undo(it, id) }
            context.getSystemService(NotificationManager::class.java).cancel(SelfCheckNotifier.notificationId(id))
        }
    }

    private fun undoAi(context: Context, intent: Intent) {
        val day = intent.getStringExtra(EXTRA_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val container = (context.applicationContext as OpenStrapApp).container
        val pending = goAsync()
        launchForBroadcast(container.appScope, "ai learning: undo", pending::finish) {
            container.settings.updateSelfCheck { SelfLearning.undoAiOn(it, day) }
            context.getSystemService(NotificationManager::class.java).cancel(AiLearnNotifier.NOTIFICATION_ID)
        }
    }

    companion object {
        const val ACTION_UNDO = "org.openstrap.app.SELF_CHECK_UNDO"
        const val ACTION_UNDO_AI = "org.openstrap.app.AI_LEARN_UNDO"
        const val EXTRA_ID = "incident"
        const val EXTRA_DAY = "day"
        private const val NONE = -1
    }
}
