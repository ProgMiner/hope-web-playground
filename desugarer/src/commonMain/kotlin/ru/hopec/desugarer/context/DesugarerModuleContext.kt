package ru.hopec.desugarer.context

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.ResolvedExpr
import ru.hopec.desugarer.ResolvedPattern
import ru.hopec.desugarer.getInternalConstructors
import ru.hopec.desugarer.internalData

data class DesugarerModuleContext(
    val globalFunctions: MutableMap<String, MutableSet<DesugaredRepresentation.Declarations.Function.Name>> = mutableMapOf(),
    val globalConstructors: MutableMap<String, MutableSet<DesugaredRepresentation.Declarations.Function.Name.Constructor>> = getInternalConstructors().toMutableMap(),
    val moduleFunctions: MutableMap<String, MutableSet<DesugaredRepresentation.Declarations.Function.Name.User>> = mutableMapOf(),
    val moduleConstructors: MutableMap<String, MutableSet<DesugaredRepresentation.Declarations.Function.Name.Constructor>> = mutableMapOf(),
    val globalDataTypes: MutableMap<String, DesugaredRepresentation.Declarations.Data.Name> = internalData.toMutableMap(),
    val moduleDataTypes: MutableMap<String, DesugaredRepresentation.Declarations.Data.Name.Defined> = mutableMapOf(),
) {
    fun extendGlobalFunctions(functions: Map<String, Set<DesugaredRepresentation.Declarations.Function.Name.User>>) {
        functions.forEach { (name, set) ->
            set.forEach { function ->
                if (globalFunctions.containsKey(name)) {
                    globalFunctions[name]!!.add(function)
                } else {
                    globalFunctions[name] = mutableSetOf(function)
                }
            }
        }
    }

    fun extendModuleFunction(name: String, function: DesugaredRepresentation.Declarations.Function.Name.User) {
        if (moduleFunctions.containsKey(name)) {
            moduleFunctions[name]!!.add(function)
        } else {
            moduleFunctions[name] = mutableSetOf(function)
        }
    }

    fun extendGlobalConstructors(constructors: Map<String, Set<DesugaredRepresentation.Declarations.Function.Name.Constructor>>) {
        constructors.forEach { (name, set) ->
            set.forEach { constructor ->
                if (globalConstructors.containsKey(name)) {
                    globalConstructors[name]!!.add(constructor)
                } else {
                    globalConstructors[name] = mutableSetOf(constructor)
                }
            }
        }
    }

    fun extendModuleConstructor(name: String, constructor: DesugaredRepresentation.Declarations.Function.Name.Constructor) {
        if (moduleConstructors.containsKey(name)) {
            moduleConstructors[name]!!.add(constructor)
        } else {
            moduleConstructors[name] = mutableSetOf(constructor)
        }
    }

    fun extendGlobalData(dataTypes:  Map<String, DesugaredRepresentation.Declarations.Data.Name.Defined>) {
        dataTypes.forEach { (_, dataType) ->
            globalDataTypes[dataType.name] = dataType
        }
    }

    fun extendModuleData(data: DesugaredRepresentation.Declarations.Data.Name.Defined) {
        moduleDataTypes[data.name] = data
    }

    private fun allConstructors(name: String): ResolvedPattern.GlobalSet? {
        val module = moduleConstructors[name]
        val global = globalConstructors[name]
        if (module == null && global == null) {
            return null
        }
        return ResolvedPattern.GlobalSet(((module ?: emptySet()) + (global ?: emptySet())).toMutableSet())
    }

    private fun allFunctions(name: String): ResolvedExpr.GlobalSet? {
        val module = moduleFunctions[name]
        val global = globalFunctions[name]
        val constructors = allConstructors(name)?.toExpr()?.idents
        if (module == null && global == null && constructors == null) {
            return null
        }
        return ResolvedExpr.GlobalSet(
            ((module ?: emptySet()) + (global ?: emptySet()) + (constructors ?: emptySet())).toMutableSet()
        )
    }

    private fun allDataTypes(name: String) = moduleDataTypes[name] ?: globalDataTypes[name]

    fun resolveExpr(name: String) = allFunctions(name)

    fun resolvePattern(name: String) = allConstructors(name)

    fun resolveData(name: String) = allDataTypes(name)
}