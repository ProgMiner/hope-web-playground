@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.driver

@JsModule("binaryen")
external val binaryen: JsAny

@JsFun("(binaryen, wat) => binaryen.parseText(wat)")
private external fun parseWat(
    binaryen: JsAny,
    wat: String,
): JsAny

@JsFun("(module) => { const out = module.emitBinary(); return out.binary ?? out; }")
private external fun emitBinary(module: JsAny): JsAny

@JsFun("(module) => module.dispose?.()")
private external fun disposeModule(module: JsAny)

@JsFun("(u8) => u8.length")
private external fun byteLength(u8: JsAny): Int

@JsFun("(u8, index) => u8[index]")
private external fun byteAt(
    u8: JsAny,
    index: Int,
): Int

actual fun compileWatToBinary(wat: String): ByteArray {
    val module = parseWat(binaryen, wat)
    try {
        val binary = emitBinary(module)
        return ByteArray(byteLength(binary)) { index -> byteAt(binary, index).toByte() }
    } finally {
        disposeModule(module)
    }
}
