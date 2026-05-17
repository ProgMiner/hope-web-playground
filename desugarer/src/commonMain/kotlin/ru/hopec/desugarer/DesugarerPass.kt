package ru.hopec.desugarer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.renamer.RenamedRepresentation

object DesugarerPass : CompilationPass<RenamedRepresentation, DesugaredRepresentation> {
    override fun run(from: RenamedRepresentation, context: CompilationContext) =
        Desugarer().renamedToDesugared(from)
}
