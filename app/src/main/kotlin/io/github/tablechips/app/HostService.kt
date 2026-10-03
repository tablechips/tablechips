package io.github.tablechips.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.tablechips.core.TableConfig
import io.github.tablechips.protocol.ProtocolJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the table alive while the phone is in somebody's pocket.
 *
 * A game lasts hours and the host's screen turns off like everybody else's; an
 * ordinary process would be killed and take the table with it.
 */
class HostService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                closeTable()
                return START_NOT_STICKY
            }
        }
        startInForeground()
        // A null intent is Android restarting this service after killing the
        // process: there was a game in progress, so pick it up rather than
        // opening an empty table over it.
        val resume = intent == null || intent.getBooleanExtra(EXTRA_RESUME, false)
        scope.launch {
            HostController.start(resume = resume, config = intent.tableConfig())
            if (HostController.isRunning) {
                updateNotification()
                HostController.observeTable()
            } else {
                // Nothing was opened, so there is nothing to close: just go.
                ServiceCompat.stopForeground(this@HostService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        HostController.stop()
        super.onDestroy()
    }

    /**
     * Ends the game on purpose. The guests are told before the door shuts and
     * the saved ledger goes with it; being killed by the system is the other
     * case, and that one leaves the game recoverable.
     */
    private fun closeTable() {
        scope.launch {
            HostController.close()
            ServiceCompat.stopForeground(this@HostService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startInForeground() {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val status = HostController.status.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val address = status.addresses.firstOrNull()?.url(status.port) ?: ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mark)
            .setContentTitle(getString(R.string.notification_title, status.roomCode ?: ""))
            .setContentText(getString(R.string.notification_text, address))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            // Nothing here is urgent: the table should never buzz in a pocket.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "table"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "io.github.tablechips.STOP"
        private const val EXTRA_RESUME = "resume"
        private const val EXTRA_CONFIG = "config"

        /**
         * The config chosen on the setup screen, as JSON: the same encoding the
         * ledger uses, so there is one way to write a config down, not two.
         */
        private fun Intent?.tableConfig(): TableConfig =
            this?.getStringExtra(EXTRA_CONFIG)
                ?.let { runCatching { ProtocolJson.decodeFromString<TableConfig>(it) }.getOrNull() }
                ?: TableConfig()

        fun start(context: Context, resume: Boolean = false, config: TableConfig? = null) {
            val intent = Intent(context, HostService::class.java).putExtra(EXTRA_RESUME, resume)
            config?.let { intent.putExtra(EXTRA_CONFIG, ProtocolJson.encodeToString(it)) }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HostService::class.java).setAction(ACTION_STOP))
        }
    }
}
