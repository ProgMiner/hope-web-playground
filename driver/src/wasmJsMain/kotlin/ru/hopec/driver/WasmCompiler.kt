package ru.hopec.driver

actual fun compileWatToBinary(wat: String): ByteArray {
    throw UnsupportedOperationException(
        "compileWatToBinary is not implemented for wasmJsMain yet. Use JVM target for WAT -> WASM compilation."
    )
}

