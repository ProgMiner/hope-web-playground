package ru.hopec.driver

import ru.hopec.codegen.CodeGenPass
import ru.hopec.core.GlobalCompilationContext
import ru.hopec.core.TranslationUnit
import ru.hopec.core.errorStatus
import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.Desugarer
import ru.hopec.desugarer.context.DesugarerGlobalContext
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.Infix
import ru.hopec.renamer.Program
import ru.hopec.renamer.RenamedRepresentation
import ru.hopec.renamer.RenamerPass
import ru.hopec.typecheck.TypeCheckPass

class MultiFilePipeline(
    private val context: GlobalCompilationContext,
) {
    private val globalDesugarerContext = DesugarerGlobalContext()
    private val typeCheckPass = TypeCheckPass()
    private val codeGenPass = CodeGenPass()

    fun compile(): String? {
        val units = context.translationUnits()
        val mainUnit = context.resolveMain() ?: return null

        val libraryDesugared =
            units
                .filter { !it.isMain() }
                .mapNotNull { processUnit(it) }

        val mainDesugared = processUnit(mainUnit) ?: return null
        val merged = mergeForCodegen(mainDesugared, libraryDesugared)

        val typed = typeCheckPass.run(merged, mainUnit.context) ?: return null
        mainUnit.representations.add(typed)

        return codeGenPass.run(typed, mainUnit.context).wat
    }

    private fun processUnit(unit: TranslationUnit): DesugaredRepresentation? {
        val tree = unit.representation<TreeSitterRepresentation>() ?: return null
        val renamed = RenamerPass.run(tree, unit.context) ?: return null
        unit.representations.add(renamed)

        val fileScopeName = if (unit.isMain()) null else unit.moduleName()
        val wrapped = wrapFileModule(renamed.program, unit.moduleName(), unit.isMain())

        val desugared =
            Desugarer(
                importedContext = globalDesugarerContext,
                fileScopeName = fileScopeName,
            ).renamedToDesugared(RenamedRepresentation(wrapped, renamed.globalOperators, renamed.moduleOperators))
        unit.representations.add(desugared)
        return desugared
    }

    companion object {
        fun wrapFileModule(
            program: Program,
            moduleName: String,
            isMain: Boolean,
        ): Program {
            if (isMain) return program

            val statements = program.list
            if (statements.isEmpty()) return program

            val hasModules = statements.any { it is AstNode.Module }

            if (!hasModules) {
                return Program(
                    listOf(AstNode.Module(moduleName, statements.filterIsInstance<AstNode.Statement>())),
                )
            }

            return program
        }

        fun mergeForCodegen(
            main: DesugaredRepresentation,
            libraries: Collection<DesugaredRepresentation>,
        ): DesugaredRepresentation {
            val modules = mutableMapOf<String, DesugaredRepresentation.Module>()
            for (library in libraries) {
                modules.putAll(library.modules)
            }
            modules.putAll(main.modules)
            return DesugaredRepresentation(modules, main.topLevel)
        }
    }
}
