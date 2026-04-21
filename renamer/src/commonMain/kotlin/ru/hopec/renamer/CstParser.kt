package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode
import kotlin.collections.listOf

class CstParser(
    private val from: TreeSitterRepresentation,
    private val moduleOperators: Map<String, Map<String, Infix>>
) {

    data class ParserState(
        val operators: MutableMap<String, Infix> = mutableMapOf(),
        val typeVars: MutableSet<String> = mutableSetOf(),
        val equations: MutableMap<String, MutableList<AstNode.FunctionEquation>> = mutableMapOf(),
    )

    sealed interface ExprToken {
        data class Operand(val expr: AstNode.Expr) : ExprToken
        data class Operator(val name: String, val infix: Infix) : ExprToken
    }

    fun parse(): Program {
        val rootNode = from.tree.rootNode
        val globalParserState = ParserState()
        val topLevelNodes = parseMultiple(rootNode, {
                child ->
            when (child.type) {
                "module" -> parseModule(child, globalParserState)
                else -> parseStatementOrInternal(child, globalParserState)
            }
        })
        return Program(topLevelNodes)
    }

    private fun parseModule(node: TsSyntaxNode, globalParserState: ParserState): AstNode.Module {
        val moduleName = node.getChildOrThrow(0u, "binding").text
        val parserState = ParserState()
        val statements = parseMultiple(node, { parseStatementOrInternal(it, parserState) }, 1u)
        return AstNode.Module(moduleName, statements)
    }

    private fun parseStatementOrInternal(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? =
        parseStatement(node, parserState).also{ if ( it == null) parseInternal(node, parserState) }

    private fun parseStatement(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? {
        return when (node.type) {
            "data_declaration" -> {
                val name =  node.getChildOrThrow(0u).text
                val params = parseMultiple(node, { it.text }, 1u, node.namedChildCount - 1u)
                val typeNode = parseTypeDeclaration(node.getChildOrThrow(node.namedChildCount - 1u), parserState.typeVars)
                AstNode.DataDeclaration(name, params, typeNode)
            }

            "function_declaration" -> {
              val names = node.getChildOrThrow(0u).text
              val typeNode = parseType(node.getChildOrThrow(1u), parserState.typeVars)
              AstNode.FunctionDeclaration(
                  names,
                  mutableListOf(),
                  getBoundVars(typeNode).toList(),
                  typeNode
              )
            }

            "type_export_declaration" -> AstNode.TypeExportDeclaration(parseMultipleIdent(node))
            "constant_export_declaration" -> AstNode.ConstantExportDeclaration(parseMultipleIdent(node))
            "module_use_declaration" -> {
                //TODO: на верхнем уровне потенциально можно вызвать до определения модуля
                val names = parseMultipleIdent(node)
                names.forEach { parserState.operators.putAll(
                    moduleOperators[it] ?: throw RenamerException(
                        StatusSeverity.ERROR,
                        "Module \"$it\" does not exist",
                        node.endPosition.toPosition())
                ) }
                AstNode.ModuleUseDeclaration(parseMultipleIdent(node))
            }
            else -> null
        }
    }

    private fun parseInternal(node: TsSyntaxNode, parserState: ParserState) {
        when(node.type) {
            "function_equation" -> {
                val (functionName, pattern) = parseFunctionPattern(node.getChildOrThrow(0u), parserState.operators)
                val expr = parseExpression(node.getChildOrThrow(1u), parserState.operators)
                val equation = AstNode.FunctionEquation(
                    pattern = pattern,
                    body = expr
                )
                val list = parserState.equations[functionName]
                if (list != null) list += equation
                else parserState.equations[functionName] = mutableListOf(equation)
            }

            "infix_declaration" -> parserState.operators.putAll(parseInfix(node)!!)
            "type_variable_declaration" -> parserState.typeVars.addAll(parseMultipleIdent(node))

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown statement: ${node.type} in node $node",
                node.endPosition.toPosition()
            )
        }
    }

    private fun parseTypeDeclaration(node: TsSyntaxNode, typeVars: MutableSet<String>): List<Pair<String, AstNode.TypeExpr?>> {
        if (node.type != "type_expression")
            throw TypeDeclarationException(node)

        val typeNode = node.getChildOrThrow(0u)
        return when (typeNode.type) {
            "binary_type_expression" -> {
                val op = typeNode.child(1u)!!
                if (op.type == "++")
                    parseTypeDeclaration(typeNode.getChildOrThrow(0u), typeVars) +
                            parseTypeDeclaration(typeNode.getChildOrThrow(1u), typeVars)
                else
                    throw TypeDeclarationException(node)
            }
            else -> {
                if (node.namedChildCount == 1u) {
                    val constructor = node.getChildOrThrow(0u).text
                    listOf( Pair(constructor, null) )
                }
                else if (node.namedChildCount == 2u) {
                    val constructor = node.getChildOrThrow(0u).text
                    listOf( Pair(constructor, parseType(node.getChildOrThrow(1u), typeVars)) )
                } else
                    // TODO: infix data constructor
                    throw TypeDeclarationException(node)
            }
        }
    }

    private fun parseType(node: TsSyntaxNode, typeVars: MutableSet<String>): AstNode.TypeExpr {
        return when (node.type) {
            "type_expression" -> {
                if (node.namedChildCount == 1u)
                    parseType(node.getChildOrThrow(0u), typeVars)
                else {
                    val func = node.getChildOrThrow(0u).text
                    val args = parseMultiple(node, { parseType(it, typeVars) }, 1u)
                    AstNode.NamedType(func, args)
                }
            }

            "binary_type_expression" -> {
                val type1 = parseType(node.getChildOrThrow(0u), typeVars)
                val type2 = parseType(node.getChildOrThrow(1u), typeVars)
                if (node.children[1].text == "->")
                    AstNode.FunctionalType(type1, type2)
                else if (node.children[1].text == "#")
                    AstNode.ProductType(type1, type2)
                else
                    throw RenamerException(StatusSeverity.ERROR,
                        "Unknown ADT: ${node.children[1].text}",
                        node.children[1].startPosition.toPosition()
                    )
            }

            "ident" ->  {
                if (typeVars.contains(node.text))
                    AstNode.VarType(node.text)
                else
                    AstNode.NamedType(node.text, emptyList())
            }

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown type: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }

    private fun getBoundVars(type: AstNode.TypeExpr): Set<String> {
        return when (type) {
            is AstNode.VarType -> setOf(type.name)
            is AstNode.NamedType -> type.arguments.flatMap { getBoundVars(it) }.toSet()
            is AstNode.FunctionalType -> getBoundVars(type.premise) + getBoundVars(type.result)
            is AstNode.ProductType -> getBoundVars(type.left) + getBoundVars(type.right)
        }
    }

    private fun parseExpression(node: TsSyntaxNode, operators: MutableMap<String, Infix>): AstNode.Expr {
        return when (node.type) {
            "expression" -> {
                if (node.namedChildCount == 1u)
                    parseExpression(node.getChildOrThrow(0u), operators)
                else
                    parseApplication(node, operators)
            }

            "ident" -> AstNode.IdentExpr(node.text)

            "decimal" -> AstNode.Decimal(node.text.toInt())
            "string" -> AstNode.AstString(node.text)
            "char" -> AstNode.AstChar(node.text.first())
            "truval" -> AstNode.Truval(node.text.toBoolean())
            "tuple" -> AstNode.TupleExpr(parseMultiple(node, { parseExpression(it, operators) }))
            "list_expression" -> AstNode.ListExpr(parseMultiple(node, { parseExpression(it, operators) }))
            "set_expression" -> AstNode.SetExpr(parseMultiple(node, { parseExpression(it, operators) }))

            "conditional_expression" ->
                AstNode.IfExpr(
                    condition = parseExpression(node.getChildOrThrow(0u), operators),
                    thenBranch = parseExpression(node.getChildOrThrow(1u), operators),
                    elseBranch = parseExpression(node.getChildOrThrow(2u), operators)
                )

            "local_variable_expression" ->
                if (node.child(0u)!!.text == "let")
                    AstNode.LetExpr(
                        pattern = parsePattern(node.getChildOrThrow(0u), operators),
                        value = parseExpression(node.getChildOrThrow(1u), operators),
                        body = parseExpression(node.getChildOrThrow(2u), operators)
                    )
                else
                    AstNode.LetExpr(
                        pattern = parsePattern(node.getChildOrThrow(2u), operators),
                        value = parseExpression(node.getChildOrThrow(1u), operators),
                        body = parseExpression(node.getChildOrThrow(0u), operators)
                    )

            "lambda_expression" -> {
                val branches = parseMultiple(node, { child ->
                    val patternNode = child.getChildOrThrow(0u)
                    val exprNode = child.getChildOrThrow(1u)
                    AstNode.LambdaBranch(
                        pattern = parsePattern(patternNode, operators),
                        expression = parseExpression(exprNode, operators),
                    )
                })
                AstNode.LambdaExpr(branches)
            }

            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown expression: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }

    private fun parseApplication(node: TsSyntaxNode, operators: MutableMap<String, Infix>): AstNode.Expr {
        val expressions = parseMultiple(node, { parseExpression(it, operators) })
        val tokenStack = mutableListOf<AstNode.Expr>()
        val tokens = mutableListOf<ExprToken>()
        for (expr in expressions) {
            if (expr is AstNode.IdentExpr && operators.contains(expr.name)) {
                if (tokenStack.isEmpty())
                    throw RenamerException(StatusSeverity.ERROR, "Not fully applied operator ${expr.name}", node.endPosition.toPosition())
                else if (tokenStack.size == 1)
                    tokens.add(ExprToken.Operand(tokenStack.first()))
                else
                    tokens.add(ExprToken.Operand(AstNode.ApplicationExpr(
                        tokenStack.first(),
                        tokenStack.drop(1),
                    )))
            }
            else
                tokenStack.add(expr)
        }

        val operands = mutableListOf<AstNode.Expr>()
        val operators = mutableListOf<ExprToken.Operator>()

        val pop = {
            val op = operators.removeLast()
            val right = operands.removeLast()
            val left = operands.removeLast()
            operands.add(AstNode.ApplicationExpr(AstNode.IdentExpr(op.name), listOf(left, right)))
        }

        tokens.forEach { token ->
            when (token) {
                is ExprToken.Operator -> {
                    while (operators.isNotEmpty()) {
                        if (operators.last().infix.priority > token.infix.priority ||
                            (operators.last().infix.priority == token.infix.priority && !token.infix.isRightAssoc)) {
                            pop()
                        } else
                            break
                    }
                    operators.add(token)
                }
                is ExprToken.Operand -> operands.add(token.expr)
            }
        }

        repeat(operators.size) { pop() }

        return operands.first()
    }

    private fun parseFunctionPattern(node: TsSyntaxNode, operators: MutableMap<String, Infix>): Pair<String, AstNode.Pattern> = throw NotImplementedError()

    private fun parsePattern(node: TsSyntaxNode, operators: MutableMap<String, Infix>): AstNode.Pattern = throw NotImplementedError()


    private fun TypeDeclarationException(node: TsSyntaxNode) = RenamerException(
        StatusSeverity.ERROR,
        "No data constructor",
        node.endPosition.toPosition()
    )
}