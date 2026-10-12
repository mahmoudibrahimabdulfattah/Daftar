package com.mk.daftar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mk.daftar.shared.resources.Res
import com.mk.daftar.shared.resources.app_name
import org.jetbrains.compose.resources.stringResource

/** Root of the app, shared by the Android activity and the iOS view controller. */
@Composable
fun App(modifier: Modifier = Modifier) {
    MaterialTheme {
        Surface(modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}
