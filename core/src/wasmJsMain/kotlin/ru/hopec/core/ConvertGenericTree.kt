@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.core

import ru.hopec.core.tree.GenericLocation
import ru.hopec.core.tree.GenericNode
import ru.hopec.core.tree.GenericTree

fun GenericTree.toJsObject(): JsObject =
    JsObject().apply {
        set("type", type)
        set("root", root.toJsObject())
    }

fun GenericNode.toJsObject(): JsObject =
    JsObject().apply {
        set("location", location.toJsObject())
        set("text", text)
        set("children", children.map { it.toJsObject() }.toJsArray())
    }

fun GenericLocation.toJsObject(): JsObject =
    JsObject().apply {
        set("file", file)
        set("from", from)
        set("to", to)
    }

@OptIn(ExperimentalJsExport::class)
@JsExport
fun genericTreeType(): String = "GENERIC"
