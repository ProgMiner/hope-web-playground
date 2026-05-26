package ru.hopec.core

import ru.hopec.core.tree.GenericTree

class CompilationContext {
    private val trees: MutableList<GenericTree> = mutableListOf()
    private val status: MultiStatus = MultiStatus(label = "Compilation")

    fun rememberTree(tree: GenericTree) {
        trees.add(tree)
    }

    fun trees(): List<GenericTree> = trees

    fun report(status: CompilationStatus): Any = this.status.add(status)

    fun result(): CompilationStatus = status
}
