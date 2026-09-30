package io.github.tablechips.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.tablechips.app.ui.BackHeader
import io.github.tablechips.app.ui.Frame
import io.github.tablechips.app.ui.Note
import io.github.tablechips.app.ui.Refugi
import io.github.tablechips.app.ui.SecondaryButton
import io.github.tablechips.app.ui.TcText
import io.github.tablechips.app.ui.Type
import java.util.concurrent.Executors

/**
 * Reading the code off the host's screen. It is the fast path, never the only
 * one: typing the address is one tap away on this same screen, and stays there
 * if the camera is refused, missing or simply too dark to focus.
 */
@Composable
fun ScannerScreen(onCode: (String) -> Unit, onType: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Frame(
        scrolling = false,
        header = {
            BackHeader(
                title = stringResource(R.string.scan_title),
                subtitle = { TcText(stringResource(R.string.scan_hint), Type.body, color = Refugi.text2) },
                onBack = onBack,
            )
        },
        actions = {
            SecondaryButton(
                label = stringResource(R.string.scan_type_instead),
                onClick = onType,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (granted) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Refugi.lineAccent), RoundedCornerShape(16.dp)),
            ) {
                CameraPreview(onCode)
            }
        } else {
            Note(stringResource(R.string.scan_no_camera))
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Refugi.bg),
        factory = { viewContext ->
            val view = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val future = ProcessCameraProvider.getInstance(viewContext)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    val text = image.readQr()
                    image.close()
                    // One code is enough: a second reading would fire the same
                    // navigation twice while the camera is still running.
                    if (text != null && !done) {
                        done = true
                        view.post { onCode(text) }
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(viewContext))
            view
        },
    )
}

/** The luminance plane is all a QR needs; no colour, no copy of the image kept. */
private fun ImageProxy.readQr(): String? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes, plane.rowStride, height, 0, 0, width, height, false,
    )
    return runCatching {
        QR_READER.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.also { QR_READER.reset() }.getOrNull()
}

private val QR_READER = MultiFormatReader().apply {
    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
}
