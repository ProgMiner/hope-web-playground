package ru.hopec.core

import ru.hopec.core.tree.GenericTree

interface CompilationContext {
    fun trees(): List<TranslationUnitRepresentations>

    fun rememberTree(tree: GenericTree)

    fun report(status: CompilationStatus)

    fun result(): CompilationStatus

    fun resolveModule(module: String): TranslationUnit?

    fun services(): Services
}
