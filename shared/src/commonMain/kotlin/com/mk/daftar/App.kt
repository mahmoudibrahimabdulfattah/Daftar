package com.mk.daftar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.designsystem.catalog.DesignCatalog
import com.mk.daftar.core.designsystem.theme.DaftarTheme

/** Root of the app, shared by the Android activity and the iOS view controller. */
@Composable
fun App(modifier: Modifier = Modifier) {
    DaftarTheme {
        DesignCatalog(modifier)
    }
}
