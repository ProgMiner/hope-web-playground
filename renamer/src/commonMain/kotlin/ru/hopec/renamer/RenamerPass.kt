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
        val firstPass = RenamerFirstPass(from).parse(context)
        val secondPass = RenamerSecondPass(firstPass).parse(context)
        return RenamedRepresentation(secondPass, firstPass.globalInfixes)
    }
}
