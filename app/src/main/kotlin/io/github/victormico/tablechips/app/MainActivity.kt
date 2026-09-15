package io.github.victormico.tablechips.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import io.github.victormico.tablechips.app.ui.Refugi

class MainActivity : ComponentActivity() {

    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The product is dark only, so the system bars are told so explicitly
        // instead of being left to follow the phone's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Refugi.bg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Refugi.bg.toArgb()),
        )
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            HostScreen(
                onStart = { HostService.start(this) },
                onStop = { HostService.stop(this) },
                onOpenClient = { url -> openInBrowser(url) },
                onShare = { url -> share(url) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // The hotspot may have been turned on while the app was in the background.
        HostController.refreshAddresses()
    }

    /**
     * The table runs in a foreground service, and from Android 13 a service
     * without notification permission runs with an invisible notification. Ask
     * once; refusing does not stop the game.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openInBrowser(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Sending the address by whatever the guest already has open beats typing it. */
    private fun share(url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.host_share_text, url))
        }
        startActivity(Intent.createChooser(send, getString(R.string.host_share)))
    }
}
