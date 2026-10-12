package com.mk.daftar

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionName", "ktlint:standard:function-naming")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
