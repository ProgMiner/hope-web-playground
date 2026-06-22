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
import ru.hopec.renamer.parseModuleInfix
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
        val moduleOperators = collectModuleOperators(units)

        val libraryDesugared =
            units
                .filter { !it.isMain() }
                .mapNotNull { processUnit(it, moduleOperators) }

        val mainDesugared = processUnit(mainUnit, moduleOperators) ?: return null
        val merged = mergeForCodegen(mainDesugared, libraryDesugared)

        val typed = typeCheckPass.run(merged, mainUnit.context) ?: return null
        mainUnit.representations.add(typed)

        return codeGenPass.run(typed, mainUnit.context).wat
    }

    private fun processUnit(
        unit: TranslationUnit,
        moduleOperators: Map<String, Map<String, Infix>>,
    ): DesugaredRepresentation? {
        val tree = unit.representation<TreeSitterRepresentation>() ?: return null
        val renamed = RenamerPass.run(tree, unit.context, moduleOperators) ?: return null
        unit.representations.add(renamed)
        val wrapped = wrapFileModule(renamed.program, unit.moduleName(), unit.isMain())
        val desugared =
            try {
                Desugarer(importedContext = globalDesugarerContext)
                    .renamedToDesugared(RenamedRepresentation(wrapped))
            } catch (e: RuntimeException) {
                if (e is IllegalStateException || e is IllegalArgumentException) {
                    unit.context.report(
                        errorStatus(
                            e.message ?: "Desugarer error",
                            unit.range(null, null),
                        ),
                    )
                    return null
                }
                throw e
            }
        unit.representations.add(desugared)
        return desugared
    }

    companion object {
        fun collectModuleOperators(units: List<TranslationUnit>): Map<String, Map<String, Infix>> {
            val merged = mutableMapOf<String, Map<String, Infix>>()
            for (unit in units) {
                val tree = unit.representation<TreeSitterRepresentation>() ?: continue
                merged.putAll(runCatching { parseModuleInfix(tree) }.getOrElse { emptyMap() })
            }
            return merged
        }

        fun wrapFileModule(
            program: Program,
            moduleName: String,
            isMain: Boolean,
        ): Program {
            if (isMain) return program

            val statements = program.list
            if (statements.isEmpty()) return program
            if (statements.any { it is AstNode.Module }) return program

            return Program(listOf(AstNode.Module(moduleName, statements.filterIsInstance<AstNode.Statement>())))
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
