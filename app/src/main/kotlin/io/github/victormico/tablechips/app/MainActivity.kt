package io.github.victormico.tablechips.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 and later force this anyway; asking for it explicitly makes
        // older versions behave the same and fixes the system bar icon contrast.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            MaterialTheme {
                Surface {
                    HostScreen(
                        onStart = { HostService.start(this) },
                        onStop = { HostService.stop(this) },
                        onOpenClient = { url -> openInBrowser(url) },
                    )
                }
            }
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
}
