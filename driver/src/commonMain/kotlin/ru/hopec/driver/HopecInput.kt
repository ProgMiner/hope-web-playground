package ru.hopec.driver

import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.mainModuleName
import ru.hopec.core.topography.Resource
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsTree

class HopecInput(
    val resources: List<Pair<Resource, TsTree>>,
) {
    constructor(tree: TsTree) : this(listOf(Resource(mainModuleName()) to tree))

    fun populate(context: GlobalCompilationContext) =
        resources.forEach {
            context.newTranslationUnit(it.first, TreeSitterRepresentation(it.second))
        }
}
