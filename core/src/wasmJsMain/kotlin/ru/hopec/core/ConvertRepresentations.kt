package ru.hopec.core

@OptIn(ExperimentalWasmJsInterop::class)
fun TranslationUnitRepresentations.toJsObject() =
    JsObject().apply {
        set("resource", resource.toJsObject())
        set("trees", trees.map { it.toJsObject() }.toJsArray())
    }
