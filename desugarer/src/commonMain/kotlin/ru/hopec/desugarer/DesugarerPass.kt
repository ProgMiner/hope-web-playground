package ru.hopec.desugarer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.renamer.RenamedRepresentation

object DesugarerPass : CompilationPass<RenamedRepresentation, DesugaredRepresentation> {
    override fun run(
        from: RenamedRepresentation,
        context: CompilationContext,
    ): DesugaredRepresentation? {
        val repr =
            try {
                Desugarer().renamedToDesugared(from)
            } catch (e: IllegalStateException) {
                context.add(e)
                null
            }
        repr?.dumpSignature(context.signatureService())

        return repr
    }

    private fun CompilationContext.add(exception: Exception) {
        println("Renaming error: ${exception.message}")
    }
}
