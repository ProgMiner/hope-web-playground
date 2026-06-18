package ru.hopec.typecheck

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.signatureService

class TypeCheckPass : CompilationPass<DesugaredRepresentation, TypedRepresentation> {
    override fun run(
        from: DesugaredRepresentation,
        context: CompilationContext,
    ): TypedRepresentation? = annotate(from, context)
}
