package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation

object RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(
        from: TreeSitterRepresentation,
        context: CompilationContext,
    ) = parse(from, context)
//    } catch (e: Exception) {
//        context.add(e)
//        null
//    }

    private fun parse(
        from: TreeSitterRepresentation,
        context: CompilationContext,
    ): RenamedRepresentation {
        val firstPass = RenamerFirstPass(from).parse(context)

        val importedOperators =
            firstPass.imported
                .toSet()
                .filter { it !in firstPass.modules.keys }
                .associateWith { file ->
                    val repr = context.resolveModule(file) ?: throw IllegalStateException("File $file does not exist")
                    val renamer =
                        repr.runPass(RenamerPass)
                            ?: throw IllegalStateException("Error while parsing imported file $file")
                    val global = renamer.globalOperators
                    val module = renamer.moduleOperators[file] ?: emptyMap()
                    global + module
                }
        val secondPass = RenamerSecondPass(firstPass, importedOperators).parse(context)
        return RenamedRepresentation(secondPass, firstPass.globalInfixes, firstPass.modules)
    }
}
