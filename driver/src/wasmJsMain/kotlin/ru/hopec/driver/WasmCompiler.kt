@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.driver

external interface GlobalWithWabt : JsAny {
    val wabt: WabtApi?
    val hopecToBinary: ((JsAny) -> WabtBinary)?
}

external interface WabtApi : JsAny {
    fun parseWat(filename: String, source: String): WabtModule
}

external interface WabtModule : JsAny {
    fun resolveNames()
    fun validate()
}

external interface WabtBinary : JsAny {
    val buffer: Uint8ArrayLike
}

external interface Uint8ArrayLike : JsAny {
    val length: Int
    fun at(index: Int): JsNumber?
}

private fun globalThisRef(): JsAny = js("globalThis")

actual fun compileWatToBinary(wat: String): ByteArray {
    val global = globalThisRef().unsafeCast<GlobalWithWabt>()
    val wabt = global.wabt
        ?: throw IllegalStateException(
            "wabt is not initialized on globalThis. " +
                "Initialize wabt before calling compile()."
        )
    val toBinary = global.hopecToBinary
        ?: throw IllegalStateException(
            "hopecToBinary helper is not initialized on globalThis."
        )

    val module = wabt.parseWat("module.wat", wat)
    module.resolveNames()
    module.validate()

    val binary = toBinary(module).buffer
    return ByteArray(binary.length) { index ->
        (binary.at(index)?.toInt() ?: 0).toByte()
    }
}




