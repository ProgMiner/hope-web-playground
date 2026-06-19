package ru.hopec.core

import ru.hopec.core.topography.Point
import ru.hopec.core.topography.Range
import ru.hopec.core.topography.Resource

data class TranslationUnit(
    val context: CompilationContext,
    val resource: Resource,
    val representations: MutableList<Representation>,
) {
    fun isMain() = moduleName() == mainModuleName()

    fun moduleName() = resource.moduleName()

    fun range(
        from: Point?,
        to: Point?,
    ) = Range(resource, from, to)

    inline fun <reified F : Representation, T : Representation> runPass(pass: CompilationPass<F, T>): T? {
        val from = representation<F>() ?: return null
        val result = pass.run(from, context) ?: return null
        representations.add(result)
        return result
    }

    inline fun <reified T> representation() = representations.filterIsInstance<T>().firstOrNull()
}

fun mainModuleName() = "main"
