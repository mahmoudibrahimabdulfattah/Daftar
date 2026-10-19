package com.mk.daftar.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mk.daftar.core.domain.session.VaultSession
import com.mk.daftar.core.domain.session.VaultSessionState
import com.mk.daftar.core.domain.update.CheckForUpdateUseCase
import com.mk.daftar.core.domain.update.ObserveRemoteConfigUpdatesUseCase
import com.mk.daftar.core.domain.update.SnoozeSoftUpdateUseCase
import com.mk.daftar.core.domain.update.UpdateCheck
import com.mk.daftar.core.domain.update.UpdateStatus
import com.mk.daftar.platform.LaunchReadiness
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Checks the store policy and decides what covers the vault. A forced update replaces [content] entirely, so the lock
 * surface is not composed and its automatic biometric request is never sent while the update is required. A soft
 * prompt is a dialog over an open, uncovered, resumed vault only.
 *
 * The launch starts from the last activated values without a network request. The fetch refreshes them on every
 * resume and whenever Remote Config activates new values. Nothing here waits for the network before the vault.
 */
@Composable
fun UpdateGate(
    sessionState: VaultSessionState,
    covered: Boolean,
    modifier: Modifier = Modifier,
    checkForUpdate: CheckForUpdateUseCase = koinInject(),
    observeRemoteConfigUpdates: ObserveRemoteConfigUpdatesUseCase = koinInject(),
    snoozeSoftUpdate: SnoozeSoftUpdateUseCase = koinInject(),
    session: VaultSession = koinInject(),
    content: @Composable () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.UpToDate) }
    var lastShownBuild by remember { mutableLongStateOf(0L) }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var retry by remember { mutableStateOf(RetryOutcome.Idle) }
    var storeFailed by remember { mutableStateOf(false) }

    // The use cases are read through state, so a new instance on recomposition never restarts the check loop.
    val currentCheck by rememberUpdatedState(checkForUpdate)
    val currentObserve by rememberUpdatedState(observeRemoteConfigUpdates)

    suspend fun refresh(): UpdateCheck {
        val check = currentCheck.check()
        status = settleUpdateStatus(status, check.status, lastShownBuild)
        return check
    }

    LaunchedEffect(lifecycle) {
        status = settleUpdateStatus(status, currentCheck.cached(), lastShownBuild)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            refresh()
            currentObserve().collect { refresh() }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }

    val presentation = updatePresentation(
        status = status,
        sessionOpen = sessionState is VaultSessionState.Open,
        covered = covered,
        resumed = resumed,
    )
    val forced = presentation is UpdatePresentation.Force
    // A soft prompt counts as shown only once it is on screen, so a prompt that never appeared is still due.
    LaunchedEffect(presentation) {
        if (presentation is UpdatePresentation.Soft) {
            lastShownBuild = maxOf(lastShownBuild, presentation.status.targetBuild)
        }
    }
    // The splash must not wait for the lock surface this screen replaces. An open vault is locked, never left underneath.
    LaunchedEffect(forced, sessionState) {
        if (forced) {
            LaunchReadiness.markReady()
            if (sessionState is VaultSessionState.Open) session.lock()
        }
    }

    when (presentation) {
        is UpdatePresentation.Force -> ForceUpdateScreen(
            retry = retry,
            storeFailed = storeFailed,
            onUpdate = {
                storeFailed = !openStore(
                    uriHandler,
                    presentation.status.storeUrl,
                    presentation.status.storeFallbackUrl,
                )
            },
            onRetry = {
                scope.launch {
                    retry = RetryOutcome.Checking
                    val check = refresh()
                    retry = when {
                        !check.fetchSucceeded -> RetryOutcome.Failed
                        check.status is UpdateStatus.Force -> RetryOutcome.StillRequired
                        else -> RetryOutcome.Idle
                    }
                }
            },
            modifier = modifier,
        )

        UpdatePresentation.Content, is UpdatePresentation.Soft -> Box(modifier.fillMaxSize()) {
            content()
            if (presentation is UpdatePresentation.Soft) {
                SoftUpdateDialog(
                    status = presentation.status,
                    storeFailed = storeFailed,
                    onUpdate = {
                        storeFailed = !openStore(
                            uriHandler,
                            presentation.status.storeUrl,
                            presentation.status.storeFallbackUrl,
                        )
                    },
                    onLater = {
                        status = UpdateStatus.UpToDate
                        scope.launch { snoozeSoftUpdate(presentation.status.targetBuild) }
                    },
                )
            }
        }
    }
}

/** The state of the force screen's "Try again". */
internal enum class RetryOutcome { Idle, Checking, Failed, StillRequired }
