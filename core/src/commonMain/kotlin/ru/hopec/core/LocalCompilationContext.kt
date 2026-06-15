package ru.hopec.core

import ru.hopec.core.topography.Resource
import ru.hopec.core.tree.GenericTree
import ru.hopec.core.tree.intoNode
import ru.hopec.core.tree.statusTreeType

class LocalCompilationContext(
    private val global: CompilationContext,
    private val resource: Resource,
    private val trees: MutableList<GenericTree>,
    private val status: MultiStatus,
) : CompilationContext {
    override fun trees(): List<TranslationUnitRepresentations> {
        val trees = this.trees.toMutableList()
        trees.add(GenericTree(statusTreeType(), status.intoNode()))
        return listOf(TranslationUnitRepresentations(resource, trees.toList()))
    }

    override fun rememberTree(tree: GenericTree) {
        trees.add(tree)
    }

    override fun report(status: CompilationStatus) = this.status.add(status)

    override fun result(): CompilationStatus = status

    override fun resolveModule(module: String) = global.resolveModule(module)
}
