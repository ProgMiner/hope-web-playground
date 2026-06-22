package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation

object RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(
        from: TreeSitterRepresentation,
        context: CompilationContext,
    ): RenamedRepresentation? = run(from, context, emptyMap())

    fun run(
        from: TreeSitterRepresentation,
        context: CompilationContext,
        externalModuleOperators: Map<String, Map<String, Infix>>,
    ): RenamedRepresentation? =
        try {
            parse(from, context, externalModuleOperators)
        } catch (e: Exception) {
            context.add(e)
            null
        }

    private fun parse(
        from: TreeSitterRepresentation,
        context: CompilationContext,
        externalModuleOperators: Map<String, Map<String, Infix>>,
    ): RenamedRepresentation {
        val localOperators = runCatching { parseModuleInfix(from) }.getOrElse { emptyMap() }
        val modulesOperators = externalModuleOperators + localOperators
        val cstParser = CstParser(from, modulesOperators)
        return RenamedRepresentation(cstParser.parse(context))
    }
}
