package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.core.errorStatus
import ru.hopec.core.topography.Range
import ru.hopec.parser.TreeSitterRepresentation

object RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(
        from: TreeSitterRepresentation,
        context: CompilationContext,
    ) = try {
        parse(from)
    } catch (e: Exception) {
        context.add(e)
        null
    }

    private fun parse(from: TreeSitterRepresentation): RenamedRepresentation {
        val modulesOperators = runCatching { parseModuleInfix(from) }.getOrElse { mapOf() }
        val cstParser = CstParser(from, modulesOperators)
        return RenamedRepresentation(cstParser.parse())
    }

    private fun CompilationContext.add(exception: Exception) {
        report(errorStatus(exception.message ?: "", exception.range()))
        println("Renaming error: ${exception.message}")
    }

    private fun Exception.range(): Range =
        if (this is RenamerException) {
            range
        } else {
            Range()
        }
}
