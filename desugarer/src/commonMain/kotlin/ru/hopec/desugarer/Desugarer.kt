package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.context.DesugarerGlobalContext
import ru.hopec.desugarer.context.DesugarerLocalContext
import ru.hopec.desugarer.context.DesugarerModuleContext
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamedRepresentation
import kotlin.collections.set

class Desugarer(
    importedContext: DesugarerGlobalContext = DesugarerGlobalContext(),
    topLevelContext: DesugarerModuleContext = DesugarerModuleContext(),
    localContext: DesugarerLocalContext = DesugarerLocalContext(),
) : ModuleDesugarer(importedContext, topLevelContext, localContext) {
    fun renamedToDesugared(from: RenamedRepresentation): DesugaredRepresentation {
        val statements = from.program.list
        val dataTypes: MutableMap<Data.Name.Defined, Data> = mutableMapOf()
        val functions: MutableMap<Declarations.Function.Name, Declarations.Function> = mutableMapOf()
        val modules: MutableMap<String, DesugaredRepresentation.Module> = mutableMapOf()
        statements.forEach { statement ->
            when (statement) {
                is AstNode.Module -> {
                    val module =
                        ModuleDesugarer(
                            globalContext = globalContext,
                            moduleContext = moduleContext.toGlobal(),
                        ).resolveModule(statement)
                    modules[statement.name] = module
                }

                is AstNode.DataDeclaration -> {
                    val dataType = resolveDataDecl(statement, null)
                    dataTypes[dataType.first] = dataType.second
                }

                is AstNode.FunctionDeclaration -> {
                    val function = resolveFunctionDecl(statement, null)
                    functions[function.first] = function.second
                }

                is AstNode.ModuleUseDeclaration -> {
                    statement.modules.forEach { importModule(it) }
                }

                is AstNode.Error -> {}

                else -> {
                    throw IllegalStateException("Export cannot be in top level")
                }
            }
        }

        val declarations =
            Declarations(
                data = dataTypes,
                functions = functions,
            )

        return DesugaredRepresentation(
            modules = modules,
            topLevel = declarations,
        )
    }
}
