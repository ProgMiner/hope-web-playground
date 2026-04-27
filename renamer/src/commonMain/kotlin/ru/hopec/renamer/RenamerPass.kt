package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation

class RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(from: TreeSitterRepresentation, context: CompilationContext) =
        try {
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
        println("Renaming error: ${exception.message}")
//    context.report(CompilationStatus.Plain(
//                e.severity,
//                e.message ?: "",
//                StatusLocation(e.location.row, e.location.column)
//            ))
    }
}
