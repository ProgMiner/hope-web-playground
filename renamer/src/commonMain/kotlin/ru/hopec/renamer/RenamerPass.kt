package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation

object RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(
        from: TreeSitterRepresentation,
        context: CompilationContext,
    ) = try {
        parse(from, context)
    } catch (e: Exception) {
        context.add(e)
        null
    }

    private fun parse(
        from: TreeSitterRepresentation,
        context: CompilationContext,
    ): RenamedRepresentation {
        val modulesOperators = runCatching { parseModuleInfix(from) }.getOrElse { mapOf() }
        val cstParser = CstParser(from, modulesOperators)
        return RenamedRepresentation(cstParser.parse(context))
    }
}
