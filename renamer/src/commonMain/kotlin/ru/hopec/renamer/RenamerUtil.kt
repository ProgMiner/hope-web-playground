package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.parser.treesitter.TsPoint
import ru.hopec.parser.treesitter.TsSyntaxNode

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
            StatusSeverity.ERROR,
            "Cannot find child $i of node $this",
            this.endPosition.toPosition(),
        )
    if (child.isError) {
        throw RenamerException(
            StatusSeverity.ERROR,
            "Error in node $this",
            child.endPosition.toPosition(),
        )
    }
    return child
}

fun parseMultipleIdent(
    node: TsSyntaxNode,
    from: UInt = 0u,
    to: UInt? = null,
) = parseMultiple(node, { child -> child.text }, from, to)

fun TsPoint.toPosition() = RenamerException.RenamerLocation(this.row.toInt(), this.column.toInt())

fun stringToList(str: String): AstNode.ConstructorPattern =
    str.foldRight(AstNode.ConstructorPattern("nil", emptyList()))
        { char, acc ->
            AstNode.ConstructorPattern(
                "cons",
                listOf(
                    AstNode.ConstructorPattern(char.toString(), emptyList()),
                    acc,
                ),
            )
        }
