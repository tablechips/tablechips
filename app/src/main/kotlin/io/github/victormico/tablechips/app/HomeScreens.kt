package io.github.victormico.tablechips.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.victormico.tablechips.app.ui.BackHeader
import io.github.victormico.tablechips.app.ui.Caption
import io.github.victormico.tablechips.app.ui.Card
import io.github.victormico.tablechips.app.ui.Frame
import io.github.victormico.tablechips.app.ui.Mark
import io.github.victormico.tablechips.app.ui.Note
import io.github.victormico.tablechips.app.ui.PlayerRow
import io.github.victormico.tablechips.app.ui.PrimaryButton
import io.github.victormico.tablechips.app.ui.Refugi
import io.github.victormico.tablechips.app.ui.SecondaryButton
import io.github.victormico.tablechips.app.ui.TcText
import io.github.victormico.tablechips.app.ui.Type
import io.github.victormico.tablechips.app.ui.chips
import io.github.victormico.tablechips.core.TableState

/** Two doors and nothing else. */
@Composable
fun HomeScreen(
    starting: Boolean,
    failed: Boolean,
    canResume: Boolean,
    /** A table is open on this phone, whether or not its owner is sitting at it. */
    tableOpen: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onResume: () -> Unit,
    onReturn: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mark(72.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TcText(stringResource(R.string.app_name), Type.brand)
                Spacer(Modifier.height(10.dp))
                TcText(
                    stringResource(R.string.home_tagline),
                    Type.bodyLarge.copy(textAlign = TextAlign.Center),
                    color = Refugi.text2,
                )
            }
            if (failed) {
                TcText(stringResource(R.string.host_error_start), Type.body, color = Refugi.loss)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Refugi.side, 0.dp, Refugi.side, 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // A table already running is the thing to act on: offering to
            // create another one would be a button that does nothing.
            if (tableOpen) {
                PrimaryButton(
                    label = stringResource(R.string.home_return),
                    subtitle = stringResource(R.string.home_return_sub),
                    height = 72.dp,
                    onClick = onReturn,
                )
            } else {
                PrimaryButton(
                    label = stringResource(if (starting) R.string.host_starting else R.string.home_create),
                    subtitle = stringResource(R.string.home_create_sub),
                    enabled = !starting,
                    height = 72.dp,
                    onClick = onCreate,
                )
            }
            SecondaryButton(
                label = stringResource(R.string.home_join),
                subtitle = stringResource(R.string.home_join_sub),
                height = 72.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = onJoin,
            )
            if (tableOpen) {
                SecondaryButton(
                    label = stringResource(R.string.host_stop),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    height = 56.dp,
                    danger = true,
                )
            }
            if (canResume && !tableOpen) {
                SecondaryButton(
                    label = stringResource(R.string.home_resume),
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    height = 56.dp,
                    style = Type.body.copy(fontSize = 14.sp),
                    borderless = true,
                )
            }
        }
    }
}

/** Asked once, before the first table: the name the others will see. */
@Composable
fun NameScreen(name: String, onName: (String) -> Unit, onDone: () -> Unit, onBack: () -> Unit) {
    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.name_title),
                subtitle = { TcText(stringResource(R.string.name_sub), Type.body, color = Refugi.text2) },
                onBack = onBack,
            )
        },
        actions = {
            PrimaryButton(
                label = stringResource(R.string.home_create),
                onClick = onDone,
                enabled = name.isNotBlank(),
            )
        },
    ) {
        Field(name, stringResource(R.string.join_name), onName)
    }
}

/** Joining somebody else's table: their name for you, and their address. */
@Composable
fun JoinScreen(
    name: String,
    address: String,
    error: String?,
    onName: (String) -> Unit,
    onAddress: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.join_title),
                subtitle = { TcText(stringResource(R.string.home_join_sub), Type.body, color = Refugi.text2) },
                onBack = onBack,
            )
        },
        actions = {
            PrimaryButton(
                label = stringResource(R.string.join_action),
                onClick = onJoin,
                enabled = name.isNotBlank() && address.isNotBlank(),
            )
        },
    ) {
        Caption(stringResource(R.string.join_name))
        Field(name, stringResource(R.string.join_name), onName)
        Caption(stringResource(R.string.join_address))
        Field(address, stringResource(R.string.join_address_hint), onAddress, numeric = true)
        error?.let {
            TcText(
                if (it == "bad_address") stringResource(R.string.join_bad_address) else errorText(it),
                Type.body,
                color = Refugi.loss,
            )
        }
    }
}

/**
 * What the host shows when somebody asks how to get in. The address block is
 * the one light surface of the product: it has to be read across a table, at
 * night, from somebody else's hand. The QR that belongs here arrives with the
 * link bridge.
 */
@Composable
fun ConnectionScreen(
    status: HostStatus,
    table: TableState?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val primary = status.addresses.firstOrNull()?.url(status.port)
    Frame(
        header = {
            BackHeader(
                title = stringResource(R.string.menu_connection),
                subtitle = {
                    TcText(
                        stringResource(R.string.host_room_code, status.roomCode.orEmpty()),
                        Type.body, color = Refugi.text2,
                    )
                },
                onBack = onBack,
            )
        },
        actions = {
            SecondaryButton(
                label = stringResource(R.string.host_stop),
                onClick = onStop,
                danger = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            background = Refugi.text,
            border = Refugi.text,
            radius = 16.dp,
            padding = 16.dp,
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
                    stringResource(R.string.host_copy), { onCopy(primary) }, Modifier.weight(1f),
                    height = 56.dp, style = Type.secondary.copy(fontSize = 15.sp),
                )
                SecondaryButton(
                    stringResource(R.string.host_share), { onShare(primary) }, Modifier.weight(1f),
                    height = 56.dp,
                )
            }
        }
        Note(stringResource(R.string.host_wifi))

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
            PlayerRow(
                name = player.name,
                chips = chips(player.stack),
                dot = if (player.connected) Refugi.gain else Refugi.line,
                strong = player.isHost,
                dim = !player.connected,
            )
        }
        if (primary != null) {
            SecondaryButton(
                label = stringResource(R.string.host_open_client),
                onClick = { onOpenInBrowser("http://127.0.0.1:${status.port}/") },
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
            )
        }
    }
}

/** A plain text field: no component library, so it is drawn here. */
@Composable
private fun Field(
    value: String,
    placeholder: String,
    onValue: (String) -> Unit,
    numeric: Boolean = false,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(60.dp)
            .background(Refugi.surface, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Refugi.line), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            TcText(placeholder, Type.secondary, color = Refugi.text2)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = Type.secondary.copy(color = Refugi.text),
            cursorBrush = SolidColor(Refugi.accent),
            keyboardOptions = KeyboardOptions(
                capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
