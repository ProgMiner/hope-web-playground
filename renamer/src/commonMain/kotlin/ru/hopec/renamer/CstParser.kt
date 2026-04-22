package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode
import kotlin.collections.listOf
import kotlin.contracts.contract

class CstParser(
    private val from: TreeSitterRepresentation,
    private val moduleOperators: Map<String, Map<String, Infix>>
) {

    data class ParserState(
        val operators: MutableMap<String, Infix> = mutableMapOf(),
        val typeVars: MutableSet<String> = mutableSetOf(),
        val equations: MutableMap<String, MutableList<AstNode.FunctionEquation>> = mutableMapOf(),
    )

    sealed interface ApplicationToken {
        data class Operand<T>(val expr: T) : ApplicationToken
        data class Operator(val name: String, val infix: Infix) : ApplicationToken
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

        topLevelNodes.filterIsInstance<AstNode.FunctionDeclaration>().forEach {
            it.equations.addAll(globalParserState.equations[it.name] ?: emptyList())
        }

        return Program(topLevelNodes)
    }

    private fun parseModule(node: TsSyntaxNode, globalParserState: ParserState): AstNode.Module {
        val moduleName = node.getChildOrThrow(0u, "binding").text
        val parserState = ParserState()
        val statements = parseMultiple(node, { parseStatementOrInternal(it, parserState) }, 1u)

        statements.filterIsInstance<AstNode.FunctionDeclaration>().forEach {
            it.equations.addAll(parserState.equations[it.name] ?: emptyList())
        }

        return AstNode.Module(moduleName, statements)
    }

    private fun parseStatementOrInternal(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? =
        parseStatement(node, parserState).also{ if ( it == null) parseInternal(node, parserState) }

    private fun parseStatement(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? {
        return when (node.type) {
            "data_declaration" -> {
                val name =  node.getChildOrThrow(0u).text
                val params = parseMultiple(node, { it.text }, 1u, node.namedChildCount - 1u)
                val typeNode = parseTypeDeclaration(node.getChildOrThrow(node.namedChildCount - 1u), parserState)
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

    private fun parseTypeDeclaration(node: TsSyntaxNode, parserState: ParserState): List<Pair<String, AstNode.TypeExpr?>> {
        if (node.type != "type_expression")
            throw TypeDeclarationException(node)

        val typeNode = node.getChildOrThrow(0u)
        return when (typeNode.type) {
            "binary_type_expression" -> {
                val op = typeNode.child(1u)!!
                if (op.type == "++")
                    parseTypeDeclaration(typeNode.getChildOrThrow(0u), parserState) +
                            parseTypeDeclaration(typeNode.getChildOrThrow(1u), parserState)
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
                    listOf( Pair(constructor, parseType(node.getChildOrThrow(1u), parserState.typeVars)) )
                } else {
                    val operatorIndex = node.namedChildren.indexOfFirst { it.text in parserState.operators.keys }.toUInt()
                    val operator = node.namedChild(operatorIndex)!!

                    val left = parseFunctionalType(node, parserState.typeVars, 0u, operatorIndex - 1u)
                    val right = parseFunctionalType(node, parserState.typeVars, operatorIndex + 1u, node.namedChildCount - 1u)

                    listOf( Pair(operator.text, AstNode.ProductType(left, right)) )
                }
            }
        }
    }

    private fun parseType(node: TsSyntaxNode, typeVars: MutableSet<String>): AstNode.TypeExpr {
        return when (node.type) {
            "type_expression" -> parseFunctionalType(node, typeVars, 0u, node.namedChildCount - 1u)

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

    private fun parseFunctionalType(node: TsSyntaxNode, typeVars: MutableSet<String>, from: UInt, to: UInt) =
        if (node.namedChildCount == 1u)
            parseType(node.getChildOrThrow(from), typeVars)
        else {
            val func = node.getChildOrThrow(from).text
            val args = parseMultiple(node, { parseType(it, typeVars) }, from + 1u, to)
            AstNode.NamedType(func, args)
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
                    parseApplication(node = node,
                                    infix = operators,
                                    parse = { parseExpression(it, operators) },
                                    constructOperand = { func, args -> AstNode.ApplicationExpr(func, args) },
                                    constructOperator = { name, args -> AstNode.ApplicationExpr(AstNode.IdentExpr(name), args) }
                    )
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

    private fun <T> parseApplication(node: TsSyntaxNode,
                                        infix: MutableMap<String, Infix>,
                                        parse: (TsSyntaxNode) -> T?,
                                        constructOperand: (T, List<T>) -> T,
                                        constructOperator: (String, List<T>) -> T): T {
        val expressions = parseMultiple(node, parse)
        val tokenStack = mutableListOf<T>()
        val tokens = mutableListOf<ApplicationToken>()

        fun popToken() {
            if (tokenStack.size == 1)
                tokens.add(ApplicationToken.Operand(tokenStack.first()))
            else if (tokenStack.size > 1)
                tokens.add(ApplicationToken.Operand(
                    constructOperand(
                        tokenStack.first(),
                        tokenStack.drop(1),
                    )
                ))
            tokenStack.clear()
        }

        for (expr in expressions) {
            if (expr is AstNode.IdentExpr && infix.contains(expr.name)) {
                if (tokenStack.isEmpty())
                    throw RenamerException(StatusSeverity.ERROR, "Not fully applied operator ${expr.name}", node.endPosition.toPosition())
                popToken()
                tokens.add(ApplicationToken.Operator(expr.name, infix[expr.name]!!))
            }
            else
                tokenStack.add(expr)
        }

        if (tokenStack.isNotEmpty()) popToken()

        val operands = mutableListOf<T>()
        val operators = mutableListOf<ApplicationToken.Operator>()

        fun popOperator() {
            val op = operators.removeLast()
            val right = operands.removeLast()
            val left = operands.removeLast()
            operands.add(constructOperator(op.name, listOf(left, right)))
        }

        tokens.forEach { token ->
            when (token) {
                is ApplicationToken.Operator -> {
                    while (operators.isNotEmpty()) {
                        if (operators.last().infix.priority > token.infix.priority ||
                            (operators.last().infix.priority == token.infix.priority && !token.infix.isRightAssoc)) {
                            popOperator()
                        } else
                            break
                    }
                    operators.add(token)
                }
                is ApplicationToken.Operand<*> -> operands.add(token.expr as T)
            }
        }

        repeat(operators.size) { popOperator() }

        return operands.first()
    }

    private fun parseFunctionPattern(node: TsSyntaxNode, operators: MutableMap<String, Infix>): Pair<String, AstNode.Pattern> {
        if (node.namedChildCount == 2u) {
            val functionName = node.getChildOrThrow(0u).text
            return Pair(functionName, parsePattern(node.getChildOrThrow(1u), operators))
        } else {
            val functionName = node.getChildOrThrow(1u).text
            val left = parsePattern(node.getChildOrThrow(0u), operators)
            val right = parsePattern(node.getChildOrThrow(2u), operators)
            return Pair(functionName, AstNode.TuplePattern(listOf(left, right)))
        }
    }

    private fun parsePattern(node: TsSyntaxNode, operators: MutableMap<String, Infix>): AstNode.Pattern {
        return when (node.type) {
            "expression" -> parsePattern(node.getChildOrThrow(0u), operators)
            "ident" -> AstNode.VarPattern(node.text)
            "tuple" -> AstNode.TuplePattern(parseMultiple(node, { parsePattern(it, operators) }))
            "wildcard_pattern" -> AstNode.WildcardPattern
            else -> throw RenamerException(StatusSeverity.ERROR,
                "Unknown pattern: ${node.type} in node $node",
                node.startPosition.toPosition()
            )
        }
    }

    private fun TypeDeclarationException(node: TsSyntaxNode) = RenamerException(
        StatusSeverity.ERROR,
        "No data constructor",
        node.endPosition.toPosition()
    )
}