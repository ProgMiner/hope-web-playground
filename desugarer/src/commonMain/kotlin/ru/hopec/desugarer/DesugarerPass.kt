package ru.hopec.desugarer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.core.errorStatus
import ru.hopec.core.topography.Range
import ru.hopec.renamer.RenamedRepresentation

object DesugarerPass : CompilationPass<RenamedRepresentation, DesugaredRepresentation> {
    override fun run(
        from: RenamedRepresentation,
        context: CompilationContext,
    ) = try {
        Desugarer().renamedToDesugared(from)
    } catch (e: IllegalStateException) {
        context.report(errorStatus("Desugarer error: ${e.message}", Range()))
        null
    } catch (e: IllegalArgumentException) {
        context.report(errorStatus("Desugarer error: ${e.message}", Range()))
        null
    }
}
