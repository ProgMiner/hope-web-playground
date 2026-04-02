package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation

class RenamerPass: CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(from: TreeSitterRepresentation, context: CompilationContext): RenamedRepresentation? {
        TODO("Not yet implemented")
    }
}