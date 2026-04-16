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
        val moduleName = node.namedChild(0u)!!.text
        val statements = mutableListOf<AstNode.Statement>()
        for (i in 1u until node.namedChildCount) {
            val child = node.namedChild(i) ?: continue
            parseStatement(child)?.let { statements.add(it) }
        }
        return AstNode.Module(moduleName, statements)
    }

    private fun parseStatement(node: TsSyntaxNode): AstNode.Statement? {
        return when (node.type) {
            "data_declaration" -> {
                val name = node.namedChild(0u)!!.text
                val params = parseMultiple(node, {child -> child.text}, 1u, node.namedChildCount - 1u)
                val typeNode = parseType(node.namedChild(node.namedChildCount - 1u)!!)
                AstNode.DataDeclaration(name, params, typeNode)
            }
            "function_equation" -> {
                val patternNode = node.namedChild(0u)!!
                val exprNode = node.namedChild(1u)!!
                AstNode.FunctionEquation(
                    pattern = parsePattern(patternNode),
                    body = parseExpression(exprNode)
                )
            }
            "function_declaration" -> {
                val names = parseMultiple(node, {child -> child.text}, 0u, node.namedChildCount - 1u)
                val typeNode = parseType(node.namedChild(node.namedChildCount - 1u)!!)
                AstNode.FunctionDeclaration(names, typeNode)
            }

            "type_variable_declaration" -> AstNode.TypeVaribleDeclaration(parseMultipleIdent(node))
            "type_export_declaration" -> AstNode.TypeExportDeclaration(parseMultipleIdent(node))
            "constant_export_declaration" -> AstNode.ConstantExportDeclaration(parseMultipleIdent(node))
            "module_use_declaration" -> AstNode.ModuleUseDeclaration(parseMultipleIdent(node))

            else -> error("Неизвестный statement: ${node.type}")
        }
    }

    private fun parseExpression(node: TsSyntaxNode): AstNode.Expr {
        return when (node.type) {
            "expression" -> {
                if (node.namedChildCount == 1u)
                    parseExpression(node.namedChild(0u)!!)
                else {
                    val func = parseExpression(node.namedChild(0u)!!)
                    val args = parseMultiple(node, ::parseExpression, 1u)
                    AstNode.Application(func, args)
                }
            }

            "ident" -> AstNode.Ident(node.text)

            "decimal" -> AstNode.Decimal(node.text.toInt())
            "string" -> AstNode.AstString(node.text)
            "char" -> AstNode.AstChar(node.text.first())
            "tuple" -> AstNode.Tuple(parseMultiple(node, ::parseExpression))
            "list_expression" -> AstNode.ListExpr(parseMultiple(node, ::parseExpression))
            "set_expression" -> AstNode.SetExpr(parseMultiple(node, ::parseExpression))

            "conditional_expression" ->
                AstNode.If(
                    condition = parseExpression(node.namedChild(0u)!!),
                    thenBranch = parseExpression(node.namedChild(1u)!!),
                    elseBranch = parseExpression(node.namedChild(2u)!!)
                )

            "local_variable_expression" ->
                AstNode.Let(
                    pattern = parsePattern(node.namedChild(0u)!!),
                    value = parseExpression(node.namedChild(1u)!!),
                    body = parseExpression(node.namedChild(2u)!!)
                )

            else -> error("Неизвестный тип выражения: ${node.type}")
        }
    }

    private fun parsePattern(node: TsSyntaxNode): AstNode.Pattern {
        return when (node.type) {
            "pattern" -> {
                if (node.namedChildCount == 1u)
                    parsePattern(node.namedChild(0u)!!)
                else
                    AstNode.Patterns(parseMultiple(node, ::parsePattern))
            }

            "expression" -> AstNode.PatternExpression(parseExpression(node))

            "wildcard_pattern" -> AstNode.Wildcard()

            else -> error("Неизвестный тип паттерна: ${node.type}")

        }
    }

    private fun parseType(node: TsSyntaxNode): AstNode.TypeExpr {
        return when (node.type) {
            "type_expression" -> {
                if (node.namedChildCount == 1u) {
                    parseType(node.namedChild(0u)!!)
                } else {
                    val func = parseType(node.namedChild(0u)!!)
                    val args = mutableListOf<AstNode.TypeExpr>()
                    for (i in 1u until node.namedChildCount) {
                        args.add(parseType(node.namedChild(i)!!))
                    }
                    AstNode.ApplicationTypes(func, args)
                }
            }

            "binary_type_expression" -> {
                val type1 = parseType(node.namedChild(0u)!!)
                val type2 = parseType(node.namedChild(1u)!!)
                AstNode.BinaryType(type1, type2)
            }

            "ident" -> AstNode.IdentType(node.text)

            else -> error("Неизвестный тип: ${node.type}")
        }
    }

    private fun <T> parseMultiple(node: TsSyntaxNode, parseFunc: (TsSyntaxNode) -> T, from: UInt = 0u, to: UInt? = null): List<T> {
        val list = mutableListOf<T>()
        for (i in from until (to ?: node.namedChildCount)) {
            list.add(parseFunc(node.namedChild(i)!!))
        }
        return list
    }

    private fun parseMultipleIdent(node: TsSyntaxNode) = parseMultiple(node, {child -> child.text})
}