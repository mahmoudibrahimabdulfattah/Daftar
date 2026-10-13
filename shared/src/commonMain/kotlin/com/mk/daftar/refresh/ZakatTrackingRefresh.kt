package com.mk.daftar.refresh

import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.usecase.EnsureZakatTrackingUseCase
import com.mk.daftar.core.domain.usecase.ObserveZakatDayUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps zakat tracking current for the open vault. Each unlock runs [EnsureZakatTrackingUseCase] once, and so does each
 * change of the Cairo day while the vault stays open. Nothing runs while locked, and no background job or notification is
 * created. A failed ensure is ignored: it never blocks an unlock and never shows anything, because the declarations are
 * simply reconciled again on the next unlock or day change.
 */
class ZakatTrackingRefresh(
    private val session: VaultSession,
    private val ensure: EnsureZakatTrackingUseCase,
    private val days: ObserveZakatDayUseCase,
    private val scope: CoroutineScope,
) {
    private var observing: Job? = null

    fun start() {
        if (observing != null) return
        observing = scope.launch {
            session.state
                .map { it is VaultSessionState.Open }
                .distinctUntilChanged()
                .collectLatest { open ->
                    if (open) {
                        // The day flow emits the current Cairo day first, which covers the unlock itself.
                        days().collect { ensure() }
                    }
                }
        }
    }
}
