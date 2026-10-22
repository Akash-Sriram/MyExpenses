package org.totschnig.myexpenses.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.util.ui.UiUtils

const val NOTIFICATION_AUTO_BACKUP = -2
const val NOTIFICATION_CONTRIB = -3
const val NOTIFICATION_WEB_UI = -4
const val NOTIFICATION_PLANNER_ERROR = -5
const val NOTIFICATION_EXCHANGE_RATE_DOWNLOAD_ERROR = -6

const val CHANNEL_ID_SYNC = "sync"
const val CHANNEL_ID_PLANNER = "planner"
const val CHANNEL_ID_DEFAULT = "default"
const val CHANNEL_ID_AUTO_BACKUP = "autoBackup"
const val CHANNEL_ID_LOANS = "loans"

class NotificationBuilderWrapper(
    private val context: Context,
    channel: String
) {
    private val builder = Notification.Builder(context, channel)

    fun setSmallIcon(smallIcon: Int): NotificationBuilderWrapper {
        builder.setSmallIcon(smallIcon)
        return this
    }

    fun setContentTitle(title: String?): NotificationBuilderWrapper {
        builder.setContentTitle(title)
        return this
    }

    fun setContentText(content: CharSequence?): NotificationBuilderWrapper {
        builder.setContentText(content)
        return this
    }

    fun setContentIntent(contentIntent: PendingIntent?): NotificationBuilderWrapper {
        builder.setContentIntent(contentIntent)
        return this
    }

    fun setDeleteIntent(deleteIntent: PendingIntent?): NotificationBuilderWrapper {
        builder.setDeleteIntent(deleteIntent)
        return this
    }

    fun setAutoCancel(autoCancel: Boolean): NotificationBuilderWrapper {
        builder.setAutoCancel(autoCancel)
        return this
    }

    fun setWhen(whenTime: Long): NotificationBuilderWrapper {
        builder.setWhen(whenTime)
        builder.setShowWhen(true)
        return this
    }

    fun addAction(icon: Int, title: String?, intent: PendingIntent?): NotificationBuilderWrapper {
        val actionIcon = if (icon == 0) null else {
            val bitmap = UiUtils.getTintedBitmapForTheme(context, icon, R.style.LightBackground)
            if (bitmap != null) Icon.createWithBitmap(bitmap) else null
        }
        builder.addAction(Notification.Action.Builder(actionIcon, title, intent).build())
        return this
    }

    fun build(): Notification {
        return builder.build()
    }

    companion object {
        const val NOTIFICATION_AUTO_BACKUP = org.totschnig.myexpenses.util.NOTIFICATION_AUTO_BACKUP
        const val NOTIFICATION_CONTRIB = org.totschnig.myexpenses.util.NOTIFICATION_CONTRIB
        const val NOTIFICATION_WEB_UI = org.totschnig.myexpenses.util.NOTIFICATION_WEB_UI
        const val NOTIFICATION_PLANNER_ERROR = org.totschnig.myexpenses.util.NOTIFICATION_PLANNER_ERROR
        const val NOTIFICATION_EXCHANGE_RATE_DOWNLOAD_ERROR = org.totschnig.myexpenses.util.NOTIFICATION_EXCHANGE_RATE_DOWNLOAD_ERROR

        const val CHANNEL_ID_SYNC = org.totschnig.myexpenses.util.CHANNEL_ID_SYNC
        const val CHANNEL_ID_PLANNER = org.totschnig.myexpenses.util.CHANNEL_ID_PLANNER
        const val CHANNEL_ID_DEFAULT = org.totschnig.myexpenses.util.CHANNEL_ID_DEFAULT
        const val CHANNEL_ID_AUTO_BACKUP = org.totschnig.myexpenses.util.CHANNEL_ID_AUTO_BACKUP
        const val CHANNEL_ID_LOANS = org.totschnig.myexpenses.util.CHANNEL_ID_LOANS

        @JvmStatic
        fun createChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val importance = NotificationManager.IMPORTANCE_DEFAULT

            val syncChannel = NotificationChannel(
                CHANNEL_ID_SYNC,
                context.getString(R.string.synchronization),
                importance
            ).apply {
                setSound(null, null)
            }
            manager.createNotificationChannel(syncChannel)

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_PLANNER,
                    context.getString(R.string.planner_notification_channel_name),
                    importance
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_DEFAULT,
                    context.getString(R.string.app_name),
                    importance
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_AUTO_BACKUP,
                    context.getString(R.string.pref_auto_backup_title),
                    importance
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_LOANS,
                    context.getString(R.string.loan_due_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        @JvmStatic
        fun defaultBigTextStyleBuilder(
            context: Context,
            title: String,
            content: CharSequence
        ): NotificationBuilderWrapper {
            return bigTextStyleBuilder(context, CHANNEL_ID_DEFAULT, title, content)
        }

        @JvmStatic
        fun bigTextStyleBuilder(
            context: Context,
            channel: String,
            title: String,
            content: CharSequence
        ): NotificationBuilderWrapper {
            val wrapper = NotificationBuilderWrapper(context, channel)
                .setSmallIcon(R.drawable.ic_stat_notification_sigma)
                .setContentTitle(title)
            wrapper.setContentText(content)
            wrapper.builder.style = Notification.BigTextStyle().bigText(content)
            return wrapper
        }
    }
}
