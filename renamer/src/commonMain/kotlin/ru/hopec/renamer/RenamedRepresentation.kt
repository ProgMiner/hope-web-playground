package ru.hopec.renamer

import ru.hopec.core.Representation

data class RenamedRepresentation(val topLevelNodes: List<AstNode.TopLevelNode>): Representation