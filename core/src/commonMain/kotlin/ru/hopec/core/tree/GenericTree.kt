package ru.hopec.core.tree

import ru.hopec.core.topography.Range

data class GenericTree(
    val type: String,
    val root: GenericNode,
)

data class GenericNode(
    val range: Range,
    val text: String,
    val children: List<GenericNode>,
)
