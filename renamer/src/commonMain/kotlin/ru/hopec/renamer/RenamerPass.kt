package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.CompilationPass
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode


class RenamerPass : CompilationPass<TreeSitterRepresentation, RenamedRepresentation> {

    override fun run(from: TreeSitterRepresentation, context: CompilationContext): RenamedRepresentation? {
        val rootNode = from.tree.rootNode

        if (rootNode.type != "compilation_unit")
            throw Exception("cannot find compilation_unit")

        val topLevelNodes = mutableListOf<AstNode.TopLevelNode>()

        for (i in 0u until rootNode.namedChildCount) {
            val child = rootNode.namedChild(i) ?: continue

            when (child.type) {
                "module" -> {
                     topLevelNodes.add(parseModule(child))
                }
                else -> {
                    val stmt = parseStatement(child)
                    if (stmt != null) topLevelNodes.add(stmt)
                }
            }
        }

        return RenamedRepresentation(topLevelNodes)
    }

    private fun parseModule(node: TsSyntaxNode): AstNode.Module {
        // seq("module", $.binding, repeat($._statement), "end")

        val bindingNode = node.namedChild(0u)
            ?: error("Module must have a binding name")

        val moduleName = bindingNode.childForFieldName("name")?.text ?: bindingNode.text

        val statements = mutableListOf<AstNode.Statement>()
        for (i in 1u until node.namedChildCount) {
            val child = node.namedChild(i) ?: continue
            parseStatement(child)?.let { statements.add(it) }
        }

        return AstNode.Module(moduleName, statements)
    }

    private fun parseStatement(node: TsSyntaxNode): AstNode.Statement? {
        return when (node.type) {
            "function_equation" -> {
                // seq("---", $.pattern, "<=", $.expression)
                val patternNode = node.namedChild(0u)!!
                val exprNode = node.namedChild(1u)!!
                AstNode.FunctionEquation(
                    pattern = parsePattern(patternNode),
                    body = parseExpression(exprNode)
                )
            }
            "function_declaration" -> {
                val names = mutableListOf<String>()
                //enumeration binding
                for (i in 0u until (node.namedChildCount - 1u)) {
                    names.add(node.namedChild(i)!!.text)
                }

                val typeNode = node.namedChild(node.namedChildCount - 1u)!!
                AstNode.FunctionDeclaration(names, AstNode.TypeExpr(typeNode.text))
            }
            else -> null
        }
    }

    private fun parseExpression(node: TsSyntaxNode): AstNode.Expr {
        return when (node.type) {
            "expression" -> {
                if (node.namedChildCount == 1u) {
                    parseExpression(node.namedChild(0u)!!)
                } else {
                    val func = parseExpression(node.namedChild(0u)!!)
                    val args = mutableListOf<AstNode.Expr>()
                    for (i in 1u until node.namedChildCount) {
                        args.add(parseExpression(node.namedChild(i)!!))
                    }
                    AstNode.Application(func, args)
                }
            }

            "ident" -> AstNode.Ident(node.text)

            "decimal" -> AstNode.Decimal(node.text.toInt())
            "string" -> AstNode.AstString(node.text)
            "char" -> AstNode.AstChar(node.text.first())
            "tuple" -> {
                val elements = mutableListOf<AstNode.Expr>()
                for (i in 0u until node.namedChildCount) {
                    elements.add(parseExpression(node.namedChild(i)!!))
                }
                AstNode.Tuple(elements)
            }

            "conditional_expression" -> {
                // seq("if", $.expression, "then", $.expression, "else", $.expression)
                AstNode.If(
                    condition = parseExpression(node.namedChild(0u)!!),
                    thenBranch = parseExpression(node.namedChild(1u)!!),
                    elseBranch = parseExpression(node.namedChild(2u)!!)
                )
            }

            "local_variable_expression" -> {
                // seq("let", $.pattern, "==", $.expression, "in", $.expression)
                AstNode.Let(
                    pattern = parsePattern(node.namedChild(0u)!!),
                    value = parseExpression(node.namedChild(1u)!!),
                    body = parseExpression(node.namedChild(2u)!!)
                )
            }

            else -> {
                error("Неизвестный тип выражения: ${node.type}")
            }
        }
    }

    private fun parsePattern(node: TsSyntaxNode): AstNode.Pattern {
        return when (node.type) {
            "wildcard_pattern" -> AstNode.Wildcard()
            "binding" -> AstNode.IdentPattern(node.childForFieldName("name")?.text ?: node.text)
            "ident" -> AstNode.IdentPattern(node.text)
            "array_pattern" -> {
                val array = mutableListOf<AstNode.Expr>()
                for (i in 0u until node.namedChildCount) {
                    array.add(parseExpression(node.namedChild(i)!!))
                }
                AstNode.ArrayPattern(array)
            }
            "list_pattern" -> {
                val list = mutableListOf<AstNode.Expr>()
                for (i in 0u until node.namedChildCount) {
                    list.add(parseExpression(node.namedChild(i)!!))
                }
                AstNode.ListPattern(list)
            }
            else -> {
                if (node.namedChildCount > 0u) {
                    parsePattern(node.namedChild(0u)!!)
                } else {
                    error("Неизвестный тип паттерна: ${node.type}")
                }
            }
        }
    }

    private fun findFirstNamedChild(node: TsSyntaxNode, targetType: String): TsSyntaxNode? {
        for (i in 0u until node.namedChildCount) {
            val child = node.namedChild(i)
            if (child?.type == targetType) return child
        }
        return null
    }
}