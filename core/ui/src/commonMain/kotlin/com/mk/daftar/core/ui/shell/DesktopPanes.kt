package com.mk.daftar.core.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.daftar.core.designsystem.component.DaftarIconButton
import com.mk.daftar.core.designsystem.icon.DaftarIcons
import com.mk.daftar.core.designsystem.resources.Res as DesignRes
import com.mk.daftar.core.designsystem.resources.btn_toggle_values
import com.mk.daftar.core.designsystem.theme.DaftarSpacing
import com.mk.daftar.core.ui.presentation.ValuesVisibility
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * True when this screen is one of two panes actually shown side by side on desktop, not merely eligible for it. In a
 * pair the list's bar keeps only its title and the detail pane's bar carries the eye and refresh at the far end, so the
 * two bars read as one header (design phase 28). Phones and single panes always get false.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun inDesktopPanePair(): Boolean {
    if (!LocalDesktopLayout.current) return false
    val scope = LocalListDetailSceneScope.current ?: return false
    val value = scope.scaffoldTransitionScope.scaffoldStateTransition.targetState
    return value.primary == PaneAdaptedValue.Expanded && value.secondary == PaneAdaptedValue.Expanded
}

/** The privacy eye bound to the vault's shared visibility, for a bar that has no view model of its own. */
@Composable
fun ValuesVisibilityAction(modifier: Modifier = Modifier) {
    val visibility: ValuesVisibility = koinInject()
    val visible by visibility.visible.collectAsStateWithLifecycle()
    DaftarIconButton(
        onClick = visibility::toggle,
        icon = if (visible) DaftarIcons.Eye else DaftarIcons.EyeOff,
        contentDescription = stringResource(DesignRes.string.btn_toggle_values),
        modifier = modifier,
    )
}

/** A top bar with actions only, at its logical end: the detail pane's bar while nothing is selected. */
@Composable
fun PaneActionsBar(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DaftarSpacing.topBarMinHeight)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        content = actions,
    )
}
