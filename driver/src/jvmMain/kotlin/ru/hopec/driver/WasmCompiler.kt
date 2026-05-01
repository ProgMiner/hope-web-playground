package ru.hopec.driver

import com.dylibso.chicory.wabt.Wat2Wasm

actual fun compileWatToBinary(wat: String): ByteArray = Wat2Wasm.parse(wat)
