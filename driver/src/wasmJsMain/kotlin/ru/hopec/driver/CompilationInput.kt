@file:OptIn(ExperimentalWasmJsInterop::class)

package ru.hopec.driver

import ru.hopec.parser.treesitter.JsTree
import ru.hopec.parser.treesitter.Tree

external interface Resource {
    val path: String
}

fun Resource.toHopec() =
    ru.hopec.core.topography
        .Resource(path)

external interface TranslationUnit : JsAny {
    val resource: Resource
    val tree: Tree
}

external interface CompilationInput {
    val resources: JsArray<TranslationUnit>
}

fun CompilationInput.toHopec() = HopecInput(resources.toList().map { it.resource.toHopec() to JsTree(it.tree) })
