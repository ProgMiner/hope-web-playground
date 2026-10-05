package ru.hopec.desugarer.context

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Class
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function

data class ClassInfo(
    val name: Class.Name,
    val methods: Map<String, Function.Name>,
)
