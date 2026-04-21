package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.parser.treesitter.TsPoint
import ru.hopec.parser.treesitter.TsSyntaxNode

fun <T> parseMultiple(node: TsSyntaxNode, parseFunc: (TsSyntaxNode) -> T?, from: UInt = 0u, to: UInt? = null): List<T> {
    val list = mutableListOf<T>()
    for (i in from until (to ?: node.namedChildCount)) {
        parseFunc(node.getChildOrThrow(i))?.let { list.add(it) }
    }
    return list
}
fun TsSyntaxNode.getChildOrThrow(i: UInt, type: String? = null): TsSyntaxNode {
    val child = this.namedChild(i) ?: throw RenamerException(
        StatusSeverity.ERROR,
        "Cannot find child ${i} of node $this",
        this.startPosition.toPosition()
    )
    if (child.isError)
        throw RenamerException(
            StatusSeverity.ERROR,
            "Error in node $this",
            child.startPosition.toPosition()
        )
    if (type != null && child.type != type)
        throw RenamerException(
            StatusSeverity.ERROR,
            "Cannot find child of type $type in node $this",
            child.startPosition.toPosition()
        )
    return child
}

fun parseMultipleIdent(node: TsSyntaxNode, from: UInt = 0u, to: UInt? = null) = parseMultiple(node, {child -> child.text}, from, to)

fun TsPoint.toPosition() = RenamerException.RenamerLocation(this.row.toInt(), this.column.toInt())
