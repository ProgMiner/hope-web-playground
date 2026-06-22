package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.context.DesugarerGlobalContext
import ru.hopec.desugarer.context.DesugarerLocalContext
import ru.hopec.desugarer.context.DesugarerModuleContext
import ru.hopec.desugarer.context.ModuleDeclarations
import ru.hopec.renamer.AstNode
import ru.hopec.renamer.RenamedRepresentation
import kotlin.collections.set

class Desugarer(
    importedContext: DesugarerGlobalContext = DesugarerGlobalContext(),
    topLevelContext: DesugarerModuleContext = DesugarerModuleContext(),
    localContext: DesugarerLocalContext = DesugarerLocalContext(),
    localModuleDeclarations: MutableMap<String, ModuleDeclarations> = mutableMapOf(),
    private val fileScopeName: String? = null,
) : ModuleDesugarer(importedContext, topLevelContext, localContext, localModuleDeclarations) {
    fun renamedToDesugared(from: RenamedRepresentation): DesugaredRepresentation {
        val statements = from.program.list
        val dataTypes: MutableMap<Data.Name.Defined, Data> = mutableMapOf()
        val functions: MutableMap<Declarations.Function.Name, Declarations.Function> = mutableMapOf()
        val modules: MutableMap<String, DesugaredRepresentation.Module> = mutableMapOf()

        val hasExplicitModules = statements.any { it is AstNode.Module }

        statements.forEach { statement ->
            when (statement) {
                is AstNode.Module -> {
                    val moduleDesugarer =
                        ModuleDesugarer(
                            globalContext = globalContext,
                            moduleContext = moduleContext.toGlobal(),
                            localModuleDeclarations = localModuleDeclarations,
                        )
                    val module = moduleDesugarer.resolveModuleLocally(statement)
                    modules[statement.name] = module

                    if (fileScopeName != null && statement.name == fileScopeName) {
                        globalContext.fileDeclarations[fileScopeName] =
                            localModuleDeclarations[fileScopeName]
                                ?: ModuleDeclarations()
                    }
                }

                is AstNode.DataDeclaration -> {
                    val dataType = resolveDataDecl(statement, fileScopeName)
                    dataTypes[dataType.first] = dataType.second
                }

                is AstNode.FunctionDeclaration -> {
                    val function = resolveFunctionDecl(statement, fileScopeName)
                    functions[function.first] = function.second
                }

                is AstNode.ModuleUseDeclaration -> {
                    statement.modules.forEach { importModule(it) }
                }

                is AstNode.ConstantExportDeclaration -> {
                    if (fileScopeName == null && !hasExplicitModules) {
                        throw IllegalStateException("pubconst cannot be at top level in main file")
                    }
                }

                is AstNode.TypeExportDeclaration -> {
                    if (fileScopeName == null && !hasExplicitModules) {
                        throw IllegalStateException("pubtype cannot be at top level in main file")
                    }
                }

                is AstNode.Error -> {}
            }
        }

        if (fileScopeName != null && hasExplicitModules) {
            val hasTopLevelExports =
                statements.any {
                    it is AstNode.ConstantExportDeclaration || it is AstNode.TypeExportDeclaration
                }
            if (hasTopLevelExports) {
                val fileFunctionsMap: MutableMap<String, MutableSet<Declarations.Function.Name>> = mutableMapOf()
                val fileConstructorsMap: MutableMap<String, MutableSet<Declarations.Function.Name.Constructor>> = mutableMapOf()
                val fileDataTypesMap: MutableMap<String, Data.Name.Defined> = mutableMapOf()

                statements.forEach { statement ->
                    when (statement) {
                        is AstNode.ConstantExportDeclaration -> {
                            statement.constants.forEach { name ->
                                val constructorSet = moduleContext.moduleConstructors[name] ?: emptySet()
                                val functionsSet = moduleContext.moduleFunctions[name] ?: emptySet()
                                val globalConstructorSet = moduleContext.globalConstructors[name] ?: emptySet()
                                val globalFunctionsSet = moduleContext.globalFunctions[name] ?: emptySet()

                                val allConstructors = constructorSet + globalConstructorSet
                                val allFunctions = functionsSet + globalFunctionsSet

                                if (allConstructors.isEmpty() && allFunctions.isEmpty()) {
                                    throw IllegalArgumentException("No constant found for '$name' to export from file '$fileScopeName'")
                                }
                                fileConstructorsMap.getOrPut(name) { mutableSetOf() }.addAll(allConstructors)
                                fileFunctionsMap.getOrPut(name) { mutableSetOf() }.addAll(allFunctions)
                            }
                        }

                        is AstNode.TypeExportDeclaration -> {
                            statement.types.forEach { name ->
                                val dataType =
                                    moduleContext.moduleDataTypes[name]
                                        ?: moduleContext.globalDataTypes[name] as? Data.Name.Defined
                                        ?: throw IllegalStateException(
                                            "No data type found for '$name' to export from file " +
                                                "'$fileScopeName'",
                                        )
                                fileDataTypesMap[name] = dataType
                            }
                        }

                        else -> {}
                    }
                }

                globalContext.fileDeclarations[fileScopeName] =
                    ModuleDeclarations(
                        fileFunctionsMap,
                        fileConstructorsMap,
                        fileDataTypesMap,
                    )
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
