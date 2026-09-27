package io.github.victormico.tablechips.app

import android.app.Application

/**
 * Exists for one reason: the table must know where its ledger lives before
 * anything opens one. The activity is not that place, because Android restarts
 * the service on its own after killing the process, with no screen in sight.
 */
class TableChipsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HostController.keepLedgerIn(ledgerStore(this))
    }
}
