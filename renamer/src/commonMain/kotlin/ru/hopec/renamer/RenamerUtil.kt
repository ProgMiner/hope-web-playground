package ru.hopec.renamer

import ru.hopec.parser.treesitter.TsSyntaxNode
import ru.hopec.parser.treesitter.range

data class Infix(
    val priority: Int,
    val isRightAssoc: Boolean,
)

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

fun castFuncDecl(node: FirstPassNode.Statement.FunctionDeclaration) =
    AstNode.FunctionDeclaration(
        node.name,
        mutableListOf(),
        node.boundVars,
        castTypeExpr(node.typeExpr),
    )

fun castDataDecl(node: FirstPassNode.Statement.DataDeclaration) =
    AstNode.DataDeclaration(
        node.name,
        node.boundVars,
        node.dataConstructors.map { (name, type) ->
            name to type?.let { castTypeExpr(type) }
        },
    )

fun castTypeExpr(node: FirstPassNode.TypeExpr): AstNode.TypeExpr =
    when (node) {
        is FirstPassNode.TypeExpr.NamedType -> {
            AstNode.NamedType(node.type, node.arguments.map { castTypeExpr(it) })
        }

        is FirstPassNode.TypeExpr.FunctionalType -> {
            AstNode.FunctionalType(castTypeExpr(node.premise), castTypeExpr(node.result))
        }

        is FirstPassNode.TypeExpr.VarType -> {
            AstNode.VarType(node.name)
        }

        is FirstPassNode.TypeExpr.ProductType -> {
            AstNode.ProductType(castTypeExpr(node.left), castTypeExpr(node.right))
        }
    }
