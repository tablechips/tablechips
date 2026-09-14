package io.github.victormico.tablechips.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.victormico.tablechips.core.TableState
import io.github.victormico.tablechips.server.HostAddress

/**
 * The host's screen at phase 1: open the table, and show every address a guest
 * could type. The address is never guessed or hardcoded, because the hotspot
 * address depends on the vendor.
 */
@Composable
fun HostScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenClient: (String) -> Unit,
) {
    val status by HostController.status.collectAsStateWithLifecycle()
    val table by HostController.table.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.host_title), style = MaterialTheme.typography.headlineSmall)

        when {
            status.starting -> Text(stringResource(R.string.host_starting))
            status.running -> Text(stringResource(R.string.host_room_code, status.roomCode.orEmpty()))
            else -> Text(stringResource(R.string.host_stopped))
        }

        status.failure?.let { failure ->
            Text(
                when (failure) {
                    HostFailure.COULD_NOT_START -> stringResource(R.string.host_error_start)
                },
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(onClick = if (status.running) onStop else onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (status.running) R.string.host_stop else R.string.host_start))
        }

        if (status.running) {
            Text(stringResource(R.string.host_hint), style = MaterialTheme.typography.bodySmall)
            AddressList(
                addresses = status.addresses,
                port = status.port,
                onCopy = { url -> context.copyToClipboard(url) },
            )
            OutlinedButton(
                onClick = { onOpenClient("http://127.0.0.1:${status.port}/") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.host_open_client))
            }
            HorizontalDivider()
            PlayerList(table)
        }
    }
}

@Composable
private fun AddressList(addresses: List<HostAddress>, port: Int, onCopy: (String) -> Unit) {
    Text(stringResource(R.string.host_addresses_title), style = MaterialTheme.typography.titleMedium)
    if (addresses.isEmpty()) {
        Text(stringResource(R.string.host_no_addresses))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        addresses.forEach { address ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(address.url(port), style = MaterialTheme.typography.titleMedium)
                        Text(address.interfaceName, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { onCopy(address.url(port)) }) {
                        Text(stringResource(R.string.host_copy))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerList(table: TableState?) {
    Text(stringResource(R.string.host_players_title), style = MaterialTheme.typography.titleMedium)
    val players = table?.players.orEmpty()
    if (players.isEmpty()) {
        Text(stringResource(R.string.host_players_none))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(players, key = { it.id.value }) { player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    player.name + " — " + pluralStringResource(
                        R.plurals.host_player_chips,
                        player.stack.toInt(),
                        player.stack,
                    ),
                )
                Text(
                    player.seat?.let { stringResource(R.string.host_seat, it + 1) }
                        ?: stringResource(R.string.host_watching),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
    // Android 13 and later show their own confirmation; anything older shows none.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.host_copied, Toast.LENGTH_SHORT).show()
    }
}
