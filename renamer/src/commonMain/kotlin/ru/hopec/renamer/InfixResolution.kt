package ru.hopec.renamer

import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode
import kotlin.collections.flatten


data class Infix(
    val priority: Int,
    val isRightAssoc: Boolean
)

fun parseModuleInfix(from: TreeSitterRepresentation): Map<String, Map<String, Infix>> {
    val rootNode = from.tree.rootNode
    val topLevelNodes =
        parseMultipleOrNull(rootNode, { child ->
            if (child.type == "module") {
                val moduleName = child.getChildOrThrow(0u).text
                val operators = parseMultipleOrNull(child, ::parseInfix, 1u).flatten()
                val export = parseMultipleOrNull(child, ::parseExport, 1u).flatten().toSet()
                return@parseMultipleOrNull Pair(moduleName, operators.filter { (name, _) -> export.contains(name) }.toMap())
            }
            null
        })
    return topLevelNodes.toMap()
}

fun parseInfix(node: TsSyntaxNode): List<Pair<String, Infix>>? {
    if (node.type == "infix_declaration") {
        val assoc = node.getChildOrThrow(0u).text != "infix"
        val names = parseMultipleIdent(node = node, to = node.namedChildCount - 1u)
        val priority = node.getChildOrThrow(node.namedChildCount - 1u).text
        return names.map { Pair(it, Infix(priority.toInt(), assoc)) }
    }
    return null
}

fun parseExport(node: TsSyntaxNode): List<String>? {
    if (node.type == "constant_export_declaration") {
        val names = parseMultipleIdent(node = node)
        return names
    }
    return null
}
