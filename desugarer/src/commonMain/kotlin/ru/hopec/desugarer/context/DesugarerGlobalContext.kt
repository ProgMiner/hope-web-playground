package ru.hopec.desugarer.context

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data

data class ModuleDeclarations (
    val functions: Map<String, Set<DesugaredRepresentation.Declarations.Function.Name.User>>,
    val constructors: Map<String, Set<DesugaredRepresentation.Declarations.Function.Name.Constructor>>,
    val dataTypes: Map<String, Data.Name.Defined>,
)

data class DesugarerGlobalContext (
    val moduleDeclarations: MutableMap<String, ModuleDeclarations>,
)