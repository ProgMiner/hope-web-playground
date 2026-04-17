package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.core.CompilationStatus
import ru.hopec.core.StatusLocation
import ru.hopec.core.StatusSeverity
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsPoint
import ru.hopec.parser.treesitter.TsSyntaxNode

class RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(from: TreeSitterRepresentation, context: CompilationContext) =
        try {
            parse(from)
        } catch (e: Exception) {
            context.add(e)
            null
        }

    private fun parse(from: TreeSitterRepresentation): RenamedRepresentation {
        val rootNode = from.tree.rootNode
        val topLevelNodes = parseMultiple(rootNode, {
            child ->
            when (child.type) {
                "module" -> parseModule(child)
                else -> parseStatement(child)
            }
        })
        return RenamedRepresentation(topLevelNodes)
    }

    private fun parseModule(node: TsSyntaxNode): AstNode.Module {
        val moduleName = node.getChildOrThrow(0u, "binding").text
        val statements = parseMultiple(node, ::parseStatement, 1u)
        return AstNode.Module(moduleName, statements)
    }

    private fun parseStatement(node: TsSyntaxNode): AstNode.Statement {
        return when (node.type) {
            "data_declaration" -> {
                val name =  node.getChildOrThrow(0u).text
                val params = parseMultiple(node, {child -> child.text}, 1u, node.namedChildCount - 1u)
                val typeNode = parseType(node.getChildOrThrow(node.namedChildCount - 1u))
                AstNode.DataDeclaration(name, params, typeNode)
            }
            "function_equation" -> {
                val patternNode = node.getChildOrThrow(0u)
                val exprNode = node.getChildOrThrow(1u)
                AstNode.FunctionEquation(
                    pattern = parsePattern(patternNode),
                    body = parseExpression(exprNode)
                )
            }
            "function_declaration" -> {
                val names = parseMultiple(node, {child -> child.text}, 0u, node.namedChildCount - 1u)
                val typeNode = parseType(node.getChildOrThrow(node.namedChildCount - 1u))
                AstNode.FunctionDeclaration(names, typeNode)
            }

            "infix_declaration" -> {
                val assoc = node.getChildOrThrow(0u).text != "infix"
                val names = parseMultiple(node, {child -> child.text}, 0u, node.namedChildCount - 1u)
                val priority = node.getChildOrThrow(node.namedChildCount - 1u).text
                AstNode.InfixDeclaration(names, priority.toInt(), assoc)
            }

            "type_variable_declaration" -> AstNode.TypeVaribleDeclaration(parseMultipleIdent(node))
            "type_export_declaration" -> AstNode.TypeExportDeclaration(parseMultipleIdent(node))
            "constant_export_declaration" -> AstNode.ConstantExportDeclaration(parseMultipleIdent(node))
            "module_use_declaration" -> AstNode.ModuleUseDeclaration(parseMultipleIdent(node))
            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown statement: ${node.type} in node $node",
                node.endPosition.toPosition()
            )
        }
    }

    private fun parseExpression(node: TsSyntaxNode): AstNode.Expr {
        return when (node.type) {
            "expression" -> {
                if (node.namedChildCount == 1u)
                    parseExpression(node.getChildOrThrow(0u))
                else {
                    val func = parseExpression(node.getChildOrThrow(0u))
                    val args = parseMultiple(node, ::parseExpression, 1u)
                    AstNode.Application(func, args)
                }
            }

            "ident" -> {
                if (node.text.contains("@")) {
                    val name = node.text.substringBefore("@")
                    val bind = node.text.substringAfter("@")
                    AstNode.Binding(name, bind)
                } else
                    AstNode.Ident(node.text)
            }

            "decimal" -> AstNode.Decimal(node.text.toInt())
            "string" -> AstNode.AstString(node.text)
            "char" -> AstNode.AstChar(node.text.first())
            "tuple" -> AstNode.Tuple(parseMultiple(node, ::parseExpression))
            "list_expression" -> AstNode.ListExpr(parseMultiple(node, ::parseExpression))
            "set_expression" -> AstNode.SetExpr(parseMultiple(node, ::parseExpression))

            "conditional_expression" ->
                AstNode.If(
                    condition = parseExpression(node.getChildOrThrow(0u)),
                    thenBranch = parseExpression(node.getChildOrThrow(1u)),
                    elseBranch = parseExpression(node.getChildOrThrow(2u))
                )

            "local_variable_expression" ->
                if (node.child(0u)!!.text == "let")
                    AstNode.Let(
                        pattern = parsePattern(node.getChildOrThrow(0u)),
                        value = parseExpression(node.getChildOrThrow(1u)),
                        body = parseExpression(node.getChildOrThrow(2u))
                    )
                else
                    AstNode.Let(
                        pattern = parsePattern(node.getChildOrThrow(2u)),
                        value = parseExpression(node.getChildOrThrow(1u)),
                        body = parseExpression(node.getChildOrThrow(0u))
                    )

            "lambda_expression" -> {
                val branches = parseMultiple(node, { child ->
                    val patternNode = child.getChildOrThrow(0u)
                    val exprNode = child.getChildOrThrow(1u)
                    AstNode.LambdaBranch(
                        pattern = parsePattern(patternNode),
                        expression = parseExpression(exprNode)
                    )
                })
                AstNode.Lambda(branches)
            }

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown expression: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }

    private fun parsePattern(node: TsSyntaxNode): AstNode.Pattern {
        return when (node.type) {
            "pattern" -> {
                if (node.namedChildCount == 1u)
                    parsePattern(node.getChildOrThrow(0u))
                else
                    AstNode.Patterns(parseMultiple(node, ::parsePattern))
            }
            "expression" -> AstNode.PatternExpression(parseExpression(node))
            "wildcard_pattern" -> AstNode.Wildcard()
            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown pattern: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }

    private fun parseType(node: TsSyntaxNode): AstNode.TypeExpr {
        return when (node.type) {
            "type_expression" -> {
                if (node.namedChildCount == 1u)
                    parseType(node.getChildOrThrow(0u))
                else {
                    val func = parseType(node.getChildOrThrow(0u))
                    val args = parseMultiple(node, ::parseType, 1u)
                    AstNode.ApplicationTypes(func, args)
                }
            }

            "binary_type_expression" -> {
                val type1 = parseType(node.getChildOrThrow(0u))
                val type2 = parseType(node.getChildOrThrow(1u))
                if (node.children[1].text == "->")
                    AstNode.PowType(type1, type2)
                else if (node.children[1].text == "++")
                    AstNode.SumType(type1, type2)
                else if (node.children[1].text == "#")
                    AstNode.ProductType(type1, type2)
                else
                    throw RenamerException(StatusSeverity.ERROR,
                        "Unknown ADT: ${node.children[1].text}",
                       node.children[1].startPosition.toPosition()
                    )
            }

            "ident" -> AstNode.IdentType(node.text)

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown type: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }

    private fun <T> parseMultiple(node: TsSyntaxNode, parseFunc: (TsSyntaxNode) -> T, from: UInt = 0u, to: UInt? = null): List<T> {
        val list = mutableListOf<T>()
        for (i in from until (to ?: node.namedChildCount)) {
            list.add(parseFunc(node.getChildOrThrow(i)))
        }
        return list
    }

    private fun parseMultipleIdent(node: TsSyntaxNode) = parseMultiple(node, {child -> child.text})

    private fun TsPoint.toPosition() = RenamerException.RenamerLocation(this.row.toInt(), this.column.toInt())

    private fun TsSyntaxNode.getChildOrThrow(i: UInt, type: String? = null): TsSyntaxNode {
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

    private fun CompilationContext.add(exception: Exception) {
        println("Renaming error: ${exception.message}")
//    context.report(CompilationStatus.Plain(
//                e.severity,
//                e.message ?: "",
//                StatusLocation(e.location.row, e.location.column)
//            ))
    }
}
