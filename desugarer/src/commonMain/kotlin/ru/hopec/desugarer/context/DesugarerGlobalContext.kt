package ru.hopec.desugarer.context

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function

data class ModuleDeclarations(
    val functions: Map<String, Set<Function.Name>> = emptyMap(),
    val constructors: Map<String, Set<Function.Name.Constructor>> = emptyMap(),
    val dataTypes: Map<String, Data.Name.Defined> = emptyMap(),
    val classes: Map<String, ClassInfo> = emptyMap(),
)

data class DesugarerGlobalContext(
    val moduleDeclarations: MutableMap<String, ModuleDeclarations> = mutableMapOf(),
    val fileDeclarations: MutableMap<String, ModuleDeclarations> = mutableMapOf(),
)
