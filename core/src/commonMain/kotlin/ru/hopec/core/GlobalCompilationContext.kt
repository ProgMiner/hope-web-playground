package ru.hopec.core

import ru.hopec.core.topography.Resource
import ru.hopec.core.tree.GenericTree

class GlobalCompilationContext(
    private val status: MultiStatus = MultiStatus(label = "Compilation"),
    private val units: TranslationUnits = TranslationUnits(),
    private val services: Services = Services(),
) : CompilationContext {
    override fun trees() = units.all().flatMap { it.context.trees() }

    override fun rememberTree(tree: GenericTree) {}

    override fun report(status: CompilationStatus) = this.status.add(status)

    override fun result(): CompilationStatus = status

    override fun resolveModule(module: String) = units.resolve(module)

    override fun services() = services

    fun resolveMain() = units.resolveMain()

    fun newTranslationUnit(
        resource: Resource,
        vararg initial: Representation,
    ) {
        val local = MultiStatus(label = "Compilation of module ${resource.moduleName()}")
        report(local)
        units.add(TranslationUnit(localContext(resource, local), resource, initial.toMutableList()))
    }

    private fun localContext(
        resource: Resource,
        status: MultiStatus,
    ): LocalCompilationContext = LocalCompilationContext(this, resource, mutableListOf(), status)
}
