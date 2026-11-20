package org.openstrap.app.device

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.selfcheck.SelfLearning

/** The Undo button of a self-check notification: takes that incident's change back and clears the notification. */
class SelfCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNDO) return
        val id = intent.getIntExtra(EXTRA_ID, NONE)
        if (id == NONE) return
        val container = (context.applicationContext as OpenStrapApp).container
        val pending = goAsync()
        launchForBroadcast(container.appScope, "self-check: undo", pending::finish) {
            container.settings.updateSelfCheck { SelfLearning.undo(it, id) }
            context.getSystemService(NotificationManager::class.java).cancel(SelfCheckNotifier.notificationId(id))
        }
    }

    companion object {
        const val ACTION_UNDO = "org.openstrap.app.SELF_CHECK_UNDO"
        const val EXTRA_ID = "incident"
        private const val NONE = -1
    }
}
