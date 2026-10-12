package com.mk.daftar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/**
 * The neutral cover shown in place of the app's content: only the notebook mark and the app name, on paper.
 * It draws immediately and carries no figures. Enforcing it when the app is backgrounded belongs to the
 * platform and security layers.
 */
@Composable
fun DaftarPrivacyCover(name: String, modifier: Modifier = Modifier) {
    val colors = DaftarTheme.colors
    Box(
        modifier = modifier.fillMaxSize().background(colors.paper),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DaftarMark(Modifier.size(65.dp))
            Text(name, style = DaftarTheme.typography.sectionHeading, color = colors.ink)
        }
    }
}
