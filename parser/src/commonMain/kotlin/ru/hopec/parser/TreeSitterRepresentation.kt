package ru.hopec.parser

import ru.hopec.core.Representation
import ru.hopec.parser.treesitter.TsTree

class TreeSitterRepresentation(
    val tree: TsTree,
) : Representation
