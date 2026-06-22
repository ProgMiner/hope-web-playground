package ru.hopec.renamer

import ru.hopec.core.Representation

data class RenamedRepresentation(
    val program: Program,
    val globalOperators: Map<String, Infix>,
    val moduleOperators: Map<String, Map<String, Infix>>,
) : Representation
