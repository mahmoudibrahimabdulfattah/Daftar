package com.mk.daftar.core.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.domain.refresh.PriceRefreshController
import com.mk.daftar.core.domain.refresh.PriceRefreshStatus
import com.mk.daftar.core.ui.resources.Res
import com.mk.daftar.core.ui.resources.price_refresh
import com.mk.daftar.core.ui.resources.price_refreshing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The price refresh action pinned in a top bar beside the privacy eye, on every screen that shows values. It drives the
 * app's single refresh controller, so repeated taps and taps on other screens coalesce into the refresh already running,
 * and it never changes whether values are hidden.
 */
@Composable
fun PriceRefreshAction(modifier: Modifier = Modifier) {
    val controller: PriceRefreshController = koinInject()
    val status by controller.status.collectAsStateWithLifecycle()
    val refreshing = status is PriceRefreshStatus.Refreshing
    DaftarIconButton(
        onClick = { controller.refresh() },
        icon = DaftarIcons.Refresh,
        contentDescription = stringResource(if (refreshing) Res.string.price_refreshing else Res.string.price_refresh),
        enabled = !refreshing,
        modifier = modifier,
    )
}
