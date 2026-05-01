package ru.hopec.typecheck

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.renamer.RenamedRepresentation

class TypeCheckPass : CompilationPass<RenamedRepresentation, TypedRepresentation> {
    override fun run(from: RenamedRepresentation, context: CompilationContext): TypedRepresentation? {
        TODO("Not yet implemented")
    }
}
