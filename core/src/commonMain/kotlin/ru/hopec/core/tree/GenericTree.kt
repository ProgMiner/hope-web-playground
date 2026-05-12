package ru.hopec.core.tree

data class GenericTree(
    val type: String,
    val root: GenericNode,
)

data class GenericNode(
    val location: GenericLocation,
    val text: String,
    val children: List<GenericNode>,
)

data class GenericLocation(
    val file: String,
    val from: Int,
    val to: Int,
)
