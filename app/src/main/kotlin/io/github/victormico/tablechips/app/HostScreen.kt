package io.github.victormico.tablechips.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.Mark
import io.github.victormico.tablechips.app.ui.PrimaryButton
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.StatusDot
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.TableState
import io.github.victormico.tablechips.server.HostAddress

/**
 * The host's phone. Closed, it is the front door of the product; open, it is
 * the screen people lean over to read the address out loud.
 *
 * Every screen here is the same three bands: a fixed header, content that
 * scrolls, and the actions pinned to the bottom where a thumb reaches them.
 */
@Composable
fun HostScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenClient: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val status by HostController.status.collectAsStateWithLifecycle()
    val table by HostController.table.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Refugi.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (status.running) {
                OpenTable(
                    status = status,
                    table = table,
                    onStop = onStop,
                    onCopy = { context.copyToClipboard(it) },
                    onShare = onShare,
                    onOpenClient = onOpenClient,
                )
            } else {
                ClosedTable(starting = status.starting, failed = status.failure != null, onStart = onStart)
            }
        }
    }
}

/** Two doors and nothing else. Joining somebody else's table arrives with F4. */
@Composable
private fun ClosedTable(starting: Boolean, failed: Boolean, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mark(72.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TcText(stringResource(R.string.app_name), Type.brand)
                Spacer(Modifier.height(10.dp))
                TcText(stringResource(R.string.home_tagline), Type.bodyLarge, color = Refugi.text2)
            }
            if (failed) TcText(stringResource(R.string.host_error_start), Type.body, color = Refugi.loss)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Refugi.side, 0.dp, Refugi.side, 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                label = stringResource(if (starting) R.string.host_starting else R.string.home_create),
                subtitle = stringResource(R.string.home_create_sub),
                enabled = !starting,
                height = 72.dp,
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun ColumnScope.OpenTable(
    status: HostStatus,
    table: TableState?,
    onStop: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenClient: (String) -> Unit,
) {
    val primary = status.addresses.firstOrNull()?.url(status.port)

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(Refugi.side, 14.dp, Refugi.side, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Mark(18.dp)
        TcText(stringResource(R.string.host_room_code, status.roomCode.orEmpty()), Type.title)
        Box(Modifier.weight(1f))
        TcText(stringResource(R.string.host_role).uppercase(), Type.caption, color = Refugi.gain)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Refugi.surfaceHigh))

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(Refugi.side, 18.dp, Refugi.side, 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The one light surface of the whole product: an address has to be read
        // out across a table, at night, from somebody else's hand. The QR that
        // belongs in this block arrives with the link bridge.
        Card(
            background = Refugi.text,
            border = Refugi.text,
            radius = 16.dp,
            padding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (status.addresses.isEmpty()) {
                TcText(stringResource(R.string.host_no_addresses), Type.body, color = Refugi.bg)
            }
            status.addresses.forEachIndexed { index, address ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                TcText(address.url(status.port), Type.address, color = Refugi.bg)
                TcText(address.interfaceName, Type.body, color = Color(0xFF5A5048))
            }
            Spacer(Modifier.height(10.dp))
            TcText(stringResource(R.string.host_scan_hint), Type.body, color = Color(0xFF5A5048))
        }

        if (primary != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SecondaryButton(
                    label = stringResource(R.string.host_copy),
                    onClick = { onCopy(primary) },
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    style = Type.name.copy(fontWeight = FontWeight.SemiBold),
                )
                SecondaryButton(
                    label = stringResource(R.string.host_share),
                    onClick = { onShare(primary) },
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    style = Type.name.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }

        // Permanent and informative, not a toast: somebody always arrives late.
        Card(
            background = Refugi.surfaceHigh,
            border = Refugi.lineAccent,
            radius = 12.dp,
            padding = 13.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    modifier = Modifier.size(18.dp)
                        .border(BorderStroke(2.dp, Refugi.warn), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { TcText("!", Type.caption, color = Refugi.warn) }
                TcText(stringResource(R.string.host_wifi), Type.body, color = Refugi.warn)
            }
        }

        val players = table?.players.orEmpty()
        Row(verticalAlignment = Alignment.Bottom) {
            Caption(stringResource(R.string.host_connected))
            Box(Modifier.weight(1f))
            TcText(
                chips(players.count { it.connected }.toLong()) + " / " +
                    chips((table?.config?.seatCount ?: 0).toLong()),
                Type.caption, color = Refugi.gain,
            )
        }
        if (players.isEmpty()) {
            TcText(stringResource(R.string.host_players_none), Type.body, color = Refugi.text2)
        }
        players.forEach { player ->
            Card(
                radius = 11.dp,
                padding = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (player.connected) Refugi.gain else Refugi.line)
                    Spacer(Modifier.width(11.dp))
                    TcText(
                        player.name,
                        if (player.isHost) Type.nameStrong else Type.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (player.seat == null) {
                        TcText(stringResource(R.string.host_watching), Type.body, color = Refugi.text2)
                        Spacer(Modifier.width(8.dp))
                    }
                    TcText(chips(player.stack), Type.chips)
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(Refugi.surfaceHigh))
    Column(
        modifier = Modifier.fillMaxWidth().padding(Refugi.side, 14.dp, Refugi.side, 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (primary != null) {
            PrimaryButton(
                label = stringResource(R.string.host_open_client),
                onClick = { onOpenClient("http://127.0.0.1:${status.port}/") },
            )
        }
        SecondaryButton(
            label = stringResource(R.string.host_stop),
            onClick = onStop,
            danger = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(text, text))
}
