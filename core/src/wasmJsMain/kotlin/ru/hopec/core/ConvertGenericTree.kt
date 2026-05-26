@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.core

import ru.hopec.core.topography.Point
import ru.hopec.core.topography.Range
import ru.hopec.core.topography.Resource
import ru.hopec.core.tree.GenericNode
import ru.hopec.core.tree.GenericTree

fun GenericTree.toJsObject(): JsObject =
    JsObject().apply {
        set("type", type)
        set("root", root.toJsObject())
    }

fun GenericNode.toJsObject(): JsObject =
    JsObject().apply {
        set("location", range.toJsObject())
        set("text", text)
        set("children", children.map { it.toJsObject() }.toJsArray())
    }

fun Range.toJsObject(): JsObject =
    JsObject().apply {
        set("resource", resource?.toJsObject())
        set("from", from?.toJsObject())
        set("to", to?.toJsObject())
    }

fun Resource.toJsObject(): JsObject =
    JsObject().apply {
        set("path", path)
    }

fun Point.toJsObject(): JsObject =
    JsObject().apply {
        set("index", index)
        set("row", row)
        set("column", column)
    }

@OptIn(ExperimentalJsExport::class)
@JsExport
fun genericTreeType(): String = "GENERIC"
