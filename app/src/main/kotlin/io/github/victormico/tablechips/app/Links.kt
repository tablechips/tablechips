package io.github.victormico.tablechips.app

import io.github.victormico.tablechips.protocol.TableLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A table somebody pointed this phone at, from outside the app: a scanned code
 * that opened the bridge page, or a link followed from anywhere else.
 *
 * It is never acted on by itself. The address goes into the join screen for
 * the player to look at and confirm, because whoever produced that code chose
 * what is in it.
 */
object IncomingLinks {
    private val _link = MutableStateFlow<TableLink?>(null)
    val link: StateFlow<TableLink?> = _link.asStateFlow()

    fun offer(link: TableLink) {
        _link.value = link
    }

    fun consume() {
        _link.value = null
    }
}
