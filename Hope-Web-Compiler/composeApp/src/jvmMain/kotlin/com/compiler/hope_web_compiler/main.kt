package com.compiler.hope_web_compiler

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "hope_web_compiler",
    ) {
        App()
    }
}