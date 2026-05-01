package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.parser.TreeSitterRepresentation
import ru.hopec.parser.treesitter.TsSyntaxNode
import kotlin.collections.listOf

class CstParser(
    private val from: TreeSitterRepresentation,
    private val moduleOperators: Map<String, Map<String, Infix>>
) {
    val internalOperators = mutableMapOf(
        Pair("::", Infix(5, true)),
        Pair("<>", Infix(6, true)),
    )

    data class ParserState(
        val operators: MutableMap<String, Infix> = mutableMapOf(),
        val localOperators: MutableMap<String, Infix> = mutableMapOf(),
        val typeVars: MutableSet<String> = mutableSetOf(),
        val equations: MutableList<Pair<String, MutableList<AstNode.FunctionEquation>>> = mutableListOf(),
    )

    sealed interface ApplicationToken {
        data class Operand<T>(val expr: T) : ApplicationToken
        data class Operator(val name: String, val infix: Infix) : ApplicationToken
    }

    fun parse(): Program {
        val rootNode = from.tree.rootNode
        val globalParserState = ParserState(operators = internalOperators)
        val topLevelNodes =
            parseMultiple(rootNode, { child ->
                try {
                    when (child.type) {
                        "module" -> parseModule(child, globalParserState)
                        else -> parseStatementOrInternal(child, globalParserState)
                    }
                } catch (e: RenamerException) {
                    AstNode.Error(e)
                }
            }).toMutableList()

        addEquations(topLevelNodes, globalParserState.equations)

        return Program(topLevelNodes)
    }

    private fun parseModule(node: TsSyntaxNode, globalParserState: ParserState): AstNode.Module {
        val moduleName = node.getChildOrThrow(0u).text
        val moduleOperators = internalOperators
        moduleOperators.putAll(globalParserState.localOperators)
        val parserState = ParserState(operators = moduleOperators)
        val statements =
            parseMultipleOrError(
                node,
                { node -> parseStatementOrInternal(node, parserState) },
                { AstNode.Error(it) },
                1u,
            ).toMutableList()

        @Suppress("UNCHECKED_CAST")
        addEquations(statements as MutableList<AstNode.TopLevelNode>, parserState.equations)

        return AstNode.Module(moduleName, statements)
    }

    private fun addEquations(statements: MutableList<AstNode.TopLevelNode>, equations: MutableList<Pair<String, MutableList<AstNode.FunctionEquation>>>) {
        var equationIndex = 0
        statements.forEachIndexed { index, statement ->
            if (statement !is AstNode.FunctionDeclaration) return@forEachIndexed
            statements[index] = statement.copy(equations = equations[equationIndex].second)
            equationIndex++
        }
    }

    private fun parseStatementOrInternal(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? =
        parseStatement(
            node,
            parserState,
        ).also { if (it == null) parseInternal(node, parserState) }

    private fun parseStatement(node: TsSyntaxNode, parserState: ParserState): AstNode.Statement? {
        return when (node.type) {
            "data_declaration" -> {
                val name = node.getChildOrThrow(0u).text
                val params = parseMultiple(node, { it.text }, 1u, node.namedChildCount - 1u)
                val typeNode = parseTypeDeclaration(node.getChildOrThrow(node.namedChildCount - 1u), parserState.operators, params.toMutableSet())
                AstNode.DataDeclaration(name, params, typeNode)
            }

            "function_declaration" -> {
                val name = node.getChildOrThrow(0u).text
                val typeNode = parseType(node.getChildOrThrow(1u), parserState.typeVars)

                parserState.equations.add(Pair(name, mutableListOf()))
                AstNode.FunctionDeclaration(
                    name,
                    mutableListOf(),
                    getBoundVars(typeNode).toList(),
                    typeNode
                )
            }

            "type_export_declaration" -> AstNode.TypeExportDeclaration(parseMultipleIdent(node))
            "constant_export_declaration" -> AstNode.ConstantExportDeclaration(parseMultipleIdent(node))
            "module_use_declaration" -> {
                val names = parseMultipleIdent(node)
                names.forEach {
                    parserState.operators.putAll(
                        moduleOperators[it] ?: throw RenamerException(
                            StatusSeverity.ERROR,
                            "Module \"$it\" does not exist",
                            node.endPosition.toPosition()
                        )
                    )
                }
                AstNode.ModuleUseDeclaration(parseMultipleIdent(node))
            }

            else -> null
        }
    }

    private fun parseInternal(node: TsSyntaxNode, parserState: ParserState) {
        when (node.type) {
            "function_equation" -> {
                val (functionName, pattern) = parseFunctionPattern(node.getChildOrThrow(0u), parserState.operators)
                val expr = parseExpression(node.getChildOrThrow(1u), parserState.operators)
                val equation = AstNode.FunctionEquation(pattern, expr)
                val equationList = parserState.equations.findLast { (name, _) -> name == functionName } ?:
                    throw RenamerException(
                        StatusSeverity.ERROR,
                        "Equations without declaration",
                        node.endPosition.toPosition()
                    )
                equationList.second.add(equation)
            }

            "infix_declaration" -> {
                parserState.operators.putAll(parseInfix(node)!!)
                parserState.localOperators.putAll(parseInfix(node)!!)
            }

            "type_variable_declaration" -> parserState.typeVars.addAll(parseMultipleIdent(node))
            "line_comment" -> {}
            else -> throw IllegalStateException("Unknown statement: ${node.type} in node $node")
        }
    }

    private fun parseTypeDeclaration(
        node: TsSyntaxNode,
        operators: MutableMap<String, Infix>,
        typeVars: MutableSet<String>
    ): List<Pair<String, AstNode.TypeExpr?>> {
        if (node.type != "type_expression")
            throw TypeDeclarationException(node)

        val typeNode = node.getChildOrThrow(0u)
        return when (typeNode.type) {
            "binary_type_expression" -> {
                val op = typeNode.child(1u)!!
                if (op.type == "++")
                    parseTypeDeclaration(typeNode.getChildOrThrow(0u), operators, typeVars) +
                            parseTypeDeclaration(typeNode.getChildOrThrow(1u), operators, typeVars)
                else
                    throw TypeDeclarationException(node)
            }

            else -> {
                if (node.namedChildCount == 1u) {
                    val constructor = node.getChildOrThrow(0u).text
                    listOf(Pair(constructor, null))
                } else if (node.namedChildCount == 2u) {
                    val constructor = node.getChildOrThrow(0u).text
                    listOf(Pair(constructor, parseType(node.getChildOrThrow(1u), typeVars)))
                } else {
                    val operatorIndex =
                        node.namedChildren.indexOfFirst { operators.contains(it.text) }.toUInt()
                    val operator = node.namedChild(operatorIndex) ?: throw RenamerException(
                        StatusSeverity.ERROR,
                        "Unknown operator",
                        node.endPosition.toPosition()
                    )

                    val left = parseFunctionalType(node, typeVars, 0u, operatorIndex)
                    val right =
                        parseFunctionalType(node, typeVars, operatorIndex + 1u, node.namedChildCount)

                    listOf(Pair(operator.text, AstNode.ProductType(left, right)))
                }
            }
        }
    }

    private fun parseType(node: TsSyntaxNode, typeVars: MutableSet<String>): AstNode.TypeExpr {
        return when (node.type) {
            "type_expression" -> parseFunctionalType(node, typeVars, 0u, node.namedChildCount)

            "binary_type_expression" -> {
                val type1 = parseType(node.getChildOrThrow(0u), typeVars)
                val type2 = parseType(node.getChildOrThrow(1u), typeVars)
                when (node.children[1].text) {
                    "->" -> AstNode.FunctionalType(type1, type2)
                    "#" -> AstNode.ProductType(type1, type2)
                    else -> throw IllegalStateException("Unknown ADT: ${node.children[1].text}")
                }
            }

            "ident" -> {
                if (typeVars.contains(node.text))
                    AstNode.VarType(node.text)
                else
                    AstNode.NamedType(node.text, emptyList())
            }

            else -> throw IllegalStateException(
                "Unknown type: ${node.type} in node $node",
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
                    parseApplication(
                        node = node,
                        infix = operators,
                        parse = { parseExpression(it, operators) },
                        constructOperand = { func, args ->
                                                AstNode.ApplicationExpr(
                                                    func,
                                                    if (args is AstNode.TupleExpr) args.elements else listOf(args)
                                                )
                                           },
                        constructOperator = { name, args -> AstNode.ApplicationExpr(AstNode.IdentExpr(name), args) }
                    )
            }


            // FIXME: временное решение, пока в грамматике есть проблемы
            "ident" -> {
                if (node.text.startsWith("\'") && node.text.endsWith("\'") && node.text.length == 3)
                    AstNode.CharLiteral(node.text[1])
                else if (Regex("-?\\d+").matches(node.text))
                    AstNode.DecimalLiteral(node.text.toInt())
                else if (node.text == "true" || node.text == "false")
                    AstNode.TruvalLiteral(node.text.toBoolean())
                else
                    AstNode.IdentExpr(node.text)
            }

            "decimal" -> AstNode.DecimalLiteral(node.text.toInt())
            "string" -> AstNode.StringLiteral(node.text.substring(1, node.text.length - 1))
            "char" -> AstNode.CharLiteral(node.text.first())
            "truval" -> AstNode.TruvalLiteral(node.text.toBoolean())
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
                val branches =
                    parseMultiple(node, { child ->
                        val patternNode = child.getChildOrThrow(0u)
                        val exprNode = child.getChildOrThrow(1u)
                        AstNode.LambdaBranch(
                            pattern = parsePattern(patternNode, operators),
                            expression = parseExpression(exprNode, operators),
                        )
                    })
                AstNode.LambdaExpr(branches)
            }

            else -> throw IllegalStateException("Unknown expression: ${node.type} in node $node")
        }
    }

    private fun <T> parseApplication(
        node: TsSyntaxNode,
        infix: MutableMap<String, Infix>,
        parse: (TsSyntaxNode) -> T?,
        constructOperand: (T, T) -> T,
        constructOperator: (String, List<T>) -> T
    ): T {
        val expressions = parseMultiple(node, parse)
        val tokenStack = mutableListOf<T>()
        val tokens = mutableListOf<ApplicationToken>()

        fun popToken() {
            if (tokenStack.size == 1)
                tokens.add(ApplicationToken.Operand(tokenStack.first()))
            else if (tokenStack.size == 2) {
                tokens.add(
                    ApplicationToken.Operand(
                        constructOperand(
                            tokenStack[0],
                            tokenStack[1],
                        )
                    )
                )
            }
            else
                throw IllegalStateException("Can apply only tuple or 1 argument")
            tokenStack.clear()
        }

        if (expressions.size != node.namedChildCount.toInt())
            throw IllegalStateException("Not all nodes were parsed")

        for (ind in 0u until node.namedChildCount) {
            val child = node.namedChild(ind)!!
            if (infix.contains(child.text)) {
                if (tokenStack.isEmpty())
                    throw RenamerException(
                        StatusSeverity.ERROR,
                        "Not fully applied operator ${child.text}",
                        node.endPosition.toPosition()
                    )
                popToken()
                tokens.add(ApplicationToken.Operator(child.text, infix[child.text]!!))
            } else
                tokenStack.add(expressions[ind.toInt()])
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
            @Suppress("UNCHECKED_CAST")
            when (token) {
                is ApplicationToken.Operator -> {
                    while (operators.isNotEmpty()) {
                        if (operators.last().infix.priority > token.infix.priority ||
                            (operators.last().infix.priority == token.infix.priority && !token.infix.isRightAssoc))
                            popOperator()
                        else break
                    }
                    operators.add(token)
                }
                is ApplicationToken.Operand<*> -> operands.add(token.expr as T)
            }
        }

        repeat(operators.size) { popOperator() }

        return operands.first()
    }

    private fun parseFunctionPattern(
        node: TsSyntaxNode,
        operators: MutableMap<String, Infix>
    ): Pair<String, AstNode.Pattern> {
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
            "pattern" -> parseApplication(
                node = node,
                infix = operators,
                parse = { parsePattern(it, operators) },
                constructOperand = { func, args ->
                    if (func is AstNode.BindingPattern && func.pattern is AstNode.WildcardPattern)
                        AstNode.ConstructorPattern(
                            func.bindName,
                            if (args is AstNode.TuplePattern) args.tuple else listOf(args)
                        )
                    else
                        throw RenamerException(
                            StatusSeverity.ERROR, "Types do not have lambdas",
                            node.endPosition.toPosition()
                        )
                },
                constructOperator = { name, args -> AstNode.ConstructorPattern(name, args) }
            )

            "expression" -> parsePattern(node.getChildOrThrow(0u), operators)
            "ident" -> AstNode.BindingPattern(AstNode.WildcardPattern, node.text)
            "binding_pattern" -> AstNode.BindingPattern(
                parsePattern(node.getChildOrThrow(1u), operators),
                node.getChildOrThrow(0u).text
            )
            "list_pattern" -> {
                val list = parseMultiple(node, { parsePattern(it, operators) })
                list.foldRight<AstNode.Pattern, AstNode.Pattern>(AstNode.BindingPattern(AstNode.WildcardPattern, "nil")
                ) { pattern, acc -> AstNode.ConstructorPattern("::", listOf(pattern, acc)) }
            }
            "tuple_pattern" -> AstNode.TuplePattern(parseMultiple(node, { parsePattern(it, operators) }))
            "wildcard_pattern" -> AstNode.WildcardPattern
            else -> throw IllegalStateException("Unknown pattern: ${node.type} in node $node")
        }
    }

    class TypeDeclarationException(node: TsSyntaxNode) :
        RenamerException(
            StatusSeverity.ERROR,
            "No data constructor",
            node.endPosition.toPosition()
        )
}