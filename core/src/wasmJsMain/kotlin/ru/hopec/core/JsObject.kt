@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.core

@JsName("Object")
external class JsObject : JsAny {
    operator fun get(key: JsString): JsAny?

    operator fun set(
        key: JsString,
        value: JsAny?,
    )
}

fun JsObject.set(
    key: String,
    value: String,
) {
    set(key.toJsString(), value.toJsString())
}

fun JsObject.set(
    key: String,
    value: Int,
) {
    set(key.toJsString(), value.toJsNumber())
}

fun JsObject.set(
    key: String,
    value: JsAny,
) {
    set(key.toJsString(), value)
}
