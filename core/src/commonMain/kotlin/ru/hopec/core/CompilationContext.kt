package ru.hopec.core

import ru.hopec.core.tree.GenericTree

class CompilationContext {

    private val trees: MutableList<GenericTree> = mutableListOf()

    fun rememberTree(tree: GenericTree) {
        trees.add(tree)
    }

    fun trees(): List<GenericTree> = trees

    fun report(status: CompilationStatus): Any = TODO()

    fun result(): CompilationStatus = TODO()
}
