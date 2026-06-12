package ru.hopec.renamer

import ru.hopec.parser.treesitter.TsSyntaxNode
import ru.hopec.parser.treesitter.range

fun <T> parseMultiple(
    node: TsSyntaxNode,
    parseFunc: (TsSyntaxNode) -> T?,
    from: UInt = 0u,
    to: UInt? = null,
): List<T> {
    val list = mutableListOf<T>()
    for (i in from until (to ?: node.namedChildCount)) {
        parseFunc(node.getChildOrThrow(i))?.let { list.add(it) }
    }
    return list
}

fun <T, V : T> parseMultipleOrError(
    node: TsSyntaxNode,
    parseFunc: (TsSyntaxNode) -> T?,
    orElse: (RenamerException) -> V,
    from: UInt = 0u,
    to: UInt? = null,
): List<T> {
    val list = mutableListOf<T>()
    for (i in from until (to ?: node.namedChildCount)) {
        try {
            parseFunc(node.getChildOrThrow(i))?.let { list.add(it) }
        } catch (e: RenamerException) {
            list.add(orElse(e))
        }
    }
    return list
}

fun <T> parseMultipleOrNull(
    node: TsSyntaxNode,
    parseFunc: (TsSyntaxNode) -> T?,
    from: UInt = 0u,
    to: UInt? = null,
) = parseMultiple(node, { node -> runCatching { parseFunc(node) }.getOrNull() }, from, to)

fun TsSyntaxNode.getChildOrThrow(i: UInt): TsSyntaxNode {
    val child =
        this.namedChild(i) ?: throw RenamerException(
            "Cannot find child $i of node $this",
            this.range(),
        )
    if (child.isError) {
        throw RenamerException(
            "Error in node $this",
            child.range(),
        )
    }
    if (child.isMissing) {
        throw RenamerException(
            "Expected ${child.type}",
            child.range(),
        )
    }
    return child
}

fun parseMultipleIdent(
    node: TsSyntaxNode,
    from: UInt = 0u,
    to: UInt? = null,
) = parseMultiple(node, { child -> child.text }, from, to)
