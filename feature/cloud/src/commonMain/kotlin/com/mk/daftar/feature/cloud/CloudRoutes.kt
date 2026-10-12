package com.mk.daftar.feature.cloud

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mk.daftar.core.navigation.CloudUploadRoute
import com.mk.daftar.core.navigation.LocalNavigator

/** The Settings entry: opens the cloud screen with upload and deletion, and leaves the restore to the locked surface. */
@Composable
fun CloudRouteScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    CloudScreen(
        mode = CloudMode.SETTINGS,
        onRestore = {},
        onOpenUpload = { navigator.goTo(CloudUploadRoute) },
        onBack = { navigator.goBack() },
        modifier = modifier,
    )
}
