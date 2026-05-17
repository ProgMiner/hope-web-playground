package ru.hopec.desugarer.context

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function

data class ModuleDeclarations(
    val functions: Map<String, Set<Function.Name.User>> = emptyMap(),
    val constructors: Map<String, Set<Function.Name.Constructor>> = emptyMap(),
    val dataTypes: Map<String, Data.Name.Defined> = emptyMap(),
)

data class DesugarerGlobalContext(
    val moduleDeclarations: MutableMap<String, ModuleDeclarations> = mutableMapOf(),
)
