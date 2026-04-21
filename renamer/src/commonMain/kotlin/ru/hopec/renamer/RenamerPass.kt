package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation

class RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {
    override fun run(from: TreeSitterRepresentation, context: CompilationContext) =
        try {
            parse(from)
        } catch (e: Exception) {
            context.add(e)
            null
        }

    private fun parse(from: TreeSitterRepresentation): RenamedRepresentation {
        val modulesOperators = parseModuleInfix(from)
        val cstParser = CstParser(from, modulesOperators)
        return RenamedRepresentation(cstParser.parse())
    }

//
//    private fun parseExpression(node: TsSyntaxNode): AstNode.Expr {
//        return when (node.type) {
//            "expression" -> {
//                if (node.namedChildCount == 1u)
//                    parseExpression(node.getChildOrThrow(0u))
//                else {
//                    val func = parseExpression(node.getChildOrThrow(0u))
//                    val args = parseMultiple(node, ::parseExpression, 1u)
//                    AstNode.Application(func, args)
//                }
//            }
//
//            "ident" -> {
//                if (node.text.contains("@")) {
//                    val name = node.text.substringBefore("@")
//                    val bind = node.text.substringAfter("@")
//                    AstNode.Binding(name, bind)
//                } else
//                    AstNode.Ident(node.text)
//            }
//
//            "decimal" -> AstNode.Decimal(node.text.toInt())
//            "string" -> AstNode.AstString(node.text)
//            "char" -> AstNode.AstChar(node.text.first())
//            "tuple" -> AstNode.Tuple(parseMultiple(node, ::parseExpression))
//            "list_expression" -> AstNode.ListExpr(parseMultiple(node, ::parseExpression))
//            "set_expression" -> AstNode.SetExpr(parseMultiple(node, ::parseExpression))
//
//            "conditional_expression" ->
//                AstNode.If(
//                    condition = parseExpression(node.getChildOrThrow(0u)),
//                    thenBranch = parseExpression(node.getChildOrThrow(1u)),
//                    elseBranch = parseExpression(node.getChildOrThrow(2u))
//                )
//
//            "local_variable_expression" ->
//                if (node.child(0u)!!.text == "let")
//                    AstNode.Let(
//                        pattern = parsePattern(node.getChildOrThrow(0u)),
//                        value = parseExpression(node.getChildOrThrow(1u)),
//                        body = parseExpression(node.getChildOrThrow(2u))
//                    )
//                else
//                    AstNode.Let(
//                        pattern = parsePattern(node.getChildOrThrow(2u)),
//                        value = parseExpression(node.getChildOrThrow(1u)),
//                        body = parseExpression(node.getChildOrThrow(0u))
//                    )
//
//            "lambda_expression" -> {
//                val branches = parseMultiple(node, { child ->
//                    val patternNode = child.getChildOrThrow(0u)
//                    val exprNode = child.getChildOrThrow(1u)
//                    AstNode.LambdaBranch(
//                        pattern = parsePattern(patternNode),
//                        expression = parseExpression(exprNode)
//                    )
//                })
//                AstNode.Lambda(branches)
//            }
//
//            else -> throw RenamerException(StatusSeverity.ERROR,
//                "Unknown expression: ${node.type} in node $node",
//                node.startPosition.toPosition()
//            )
//        }
//    }
//
//    private fun parsePattern(node: TsSyntaxNode): AstNode.Pattern {
//        return when (node.type) {
//            "pattern" -> {
//                if (node.namedChildCount == 1u)
//                    parsePattern(node.getChildOrThrow(0u))
//                else
//                    AstNode.Patterns(parseMultiple(node, ::parsePattern))
//            }
//            "expression" -> AstNode.PatternExpression(parseExpression(node))
//            "wildcard_pattern" -> AstNode.Wildcard()
//            else -> throw RenamerException(StatusSeverity.ERROR,
//                "Unknown pattern: ${node.type} in node $node",
//                node.startPosition.toPosition()
//            )
//        }
//    }
//

//    private fun parseOperandsList(operandsList: List<>): List<AstNode>

    private fun CompilationContext.add(exception: Exception) {
        println("Renaming error: ${exception.message}")
//    context.report(CompilationStatus.Plain(
//                e.severity,
//                e.message ?: "",
//                StatusLocation(e.location.row, e.location.column)
//            ))
    }
}
