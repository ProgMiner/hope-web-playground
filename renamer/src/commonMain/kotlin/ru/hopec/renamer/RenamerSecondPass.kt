package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.parser.treesitter.TsSyntaxNode
import ru.hopec.parser.treesitter.range

class RenamerSecondPass(
    private val from: FirstPassProgram,
    private val importedOperators: Map<String, Map<String, Infix>>,
) {
    val internalOperators =
        mutableMapOf(
            Pair("div", Infix(6, false)),
            Pair("mod", Infix(6, false)),
            Pair("/", Infix(6, false)),
            Pair("*", Infix(6, false)),
            Pair("-", Infix(6, false)),
            Pair("+", Infix(6, false)),
            Pair("::", Infix(5, true)),
            Pair("=", Infix(4, false)),
            Pair("/=", Infix(4, false)),
            Pair("<=", Infix(4, false)),
            Pair(">=", Infix(4, false)),
            Pair("<", Infix(4, false)),
            Pair(">", Infix(4, false)),
            Pair("#", Infix(0, true)),
        )

    data class ParserState(
        val operators: MutableMap<String, Infix> = mutableMapOf(),
        val localOperators: MutableMap<String, Infix> = mutableMapOf(),
        val typeVars: MutableSet<String> = mutableSetOf(),
        val equations: MutableList<Pair<String, MutableList<AstNode.FunctionEquation>>> = mutableListOf(),
    )

    sealed interface ApplicationToken {
        data class Operand<T>(
            val expr: T,
        ) : ApplicationToken

        data class Operator(
            val name: String,
            val infix: Infix,
        ) : ApplicationToken
    }

    fun parse(context: CompilationContext): Program {
        val rootNode = from.list
        val globalParserState = ParserState(operators = internalOperators)
        val topLevelNodes =
            rootNode
                .mapNotNull {
                    try {
                        when (it) {
                            is FirstPassNode.Module -> {
                                parseModule(it, globalParserState)
                            }

                            is FirstPassNode.Statement -> {
                                parseStatementOrInternal(it, globalParserState)
                            }
                        }
                    } catch (e: RenamerException) {
                        context.add(e)
                        AstNode.Error(e)
                    }
                }.toMutableList()

        addEquations(topLevelNodes, globalParserState.equations)

        return Program(topLevelNodes)
    }

    private fun parseModule(
        node: FirstPassNode.Module,
        globalParserState: ParserState,
    ): AstNode.Module {
        val moduleName = node.name
        val moduleOperators = internalOperators
        moduleOperators.putAll(globalParserState.localOperators)
        val parserState = ParserState(operators = moduleOperators)
        val statements =
            node.statements
                .mapNotNull {
                    try {
                        parseStatementOrInternal(it, parserState)
                    } catch (e: RenamerException) {
                        AstNode.Error(e)
                    }
                }.toMutableList()

        @Suppress("UNCHECKED_CAST")
        addEquations(statements as MutableList<AstNode.TopLevelNode>, parserState.equations)

        return AstNode.Module(moduleName, statements)
    }

    private fun addEquations(
        statements: MutableList<AstNode.TopLevelNode>,
        equations: MutableList<Pair<String, MutableList<AstNode.FunctionEquation>>>,
    ) {
        var equationIndex = 0
        statements.forEachIndexed { index, statement ->
            if (statement !is AstNode.FunctionDeclaration) return@forEachIndexed
            statements[index] = statement.copy(equations = equations[equationIndex].second)
            equationIndex++
        }
    }

    private fun parseStatementOrInternal(
        node: FirstPassNode.Statement,
        parserState: ParserState,
    ): AstNode.Statement? =
        parseStatement(
            node,
            parserState,
        ).also { if (it == null) parseInternal(node, parserState) }

    private fun parseStatement(
        node: FirstPassNode.Statement,
        parserState: ParserState,
    ): AstNode.Statement? =
        when (node) {
            is FirstPassNode.Statement.DataDeclaration -> {
                castDataDecl(node)
            }

            is FirstPassNode.Statement.FunctionDeclaration -> {
                parserState.equations.add(Pair(node.name, mutableListOf()))
                castFuncDecl(node)
            }

            is FirstPassNode.Statement.ConstantExportDeclaration -> {
                AstNode.ConstantExportDeclaration(node.constants)
            }

            is FirstPassNode.Statement.ModuleUseDeclaration -> {
                node.modules.forEach {
                    val operators =
                        from.modules[it]
                            ?: importedOperators[it]
                            ?: throw RenamerException(
                                "Module \"$it\" does not exist",
                                node.node.range(),
                            )
                    parserState.operators.putAll(operators)
                }
                AstNode.ModuleUseDeclaration(node.modules)
            }

            is FirstPassNode.Statement.NotParsed -> {
                when (node.node.type) {
                    "type_export_declaration" -> {
                        AstNode.TypeExportDeclaration(parseMultipleIdent(node.node))
                    }

                    "module_use_declaration" -> {
                        val names = parseMultipleIdent(node.node)
                        names.forEach {
                            parserState.operators.putAll(
                                from.modules[it] ?: throw RenamerException(
                                    "Module \"$it\" does not exist",
                                    node.node.range(),
                                ),
                            )
                        }
                        AstNode.ModuleUseDeclaration(parseMultipleIdent(node.node))
                    }

                    else -> {
                        null
                    }
                }
            }

            else -> {
                null
            }
        }

    private fun parseInternal(
        node: FirstPassNode.Statement,
        parserState: ParserState,
    ) {
        when (node) {
            is FirstPassNode.Statement.InfixDeclaration -> {
                parserState.operators.putAll(node.operators)
                parserState.localOperators.putAll(node.operators)
            }

            is FirstPassNode.Statement.NotParsed -> {
                when (node.node.type) {
                    "function_equation" -> {
                        val (functionName, pattern) = parseFunctionPattern(node.node.getChildOrThrow(0u), parserState.operators)
                        val expr = parseExpression(node.node.getChildOrThrow(1u), parserState.operators)
                        val equation = AstNode.FunctionEquation(pattern, expr)
                        val equationList =
                            parserState.equations.findLast { (name, _) -> name == functionName }
                                ?: throw RenamerException(
                                    "Equations without declaration",
                                    node.node.range(),
                                )
                        equationList.second.add(equation)
                    }

                    "infix_declaration" -> {
                    }

                    "type_variable_declaration" -> {
                        parserState.typeVars.addAll(parseMultipleIdent(node.node))
                    }

                    "line_comment" -> {}

                    else -> {
                        throw RenamerException("Unknown statement: ${node.node.type} in node $node", node.node.range(), fatal = true)
                    }
                }
            }

            is FirstPassNode.Statement.Error -> {
                throw node.error
            }

            else -> {
                //  эта ошибка никогда не кидается
                throw IllegalStateException()
            }
        }
    }

    private fun parseExpression(
        node: TsSyntaxNode,
        operators: MutableMap<String, Infix>,
    ): AstNode.Expr =
        when (node.type) {
            "expression" -> {
                if (node.namedChildCount == 1u) {
                    parseExpression(node.getChildOrThrow(0u), operators)
                } else {
                    parseApplication(
                        node = node,
                        infix = operators,
                        parse = { parseExpression(it, operators) },
                        constructOperand = { func, args ->
                            AstNode.ApplicationExpr(
                                func,
                                if (args is AstNode.TupleExpr) args.elements else listOf(args),
                            )
                        },
                        constructOperator = { name, args -> AstNode.ApplicationExpr(AstNode.IdentExpr(name), args) },
                    )
                }
            }

            "ident" -> {
                if (node.text.startsWith("\'") && node.text.endsWith("\'") && node.text.length == 3) {
                    AstNode.CharLiteral(node.text[1])
                } else if (Regex("-?\\d+").matches(node.text)) {
                    AstNode.DecimalLiteral(
                        node.text.toLongOrNull() ?: throw RenamerException(
                            "Can't parse decimal literal",
                            node.range(),
                            fatal = true,
                        ),
                    )
                } else if (node.text == "true" || node.text == "false") {
                    AstNode.TruvalLiteral(node.text.toBoolean())
                } else {
                    AstNode.IdentExpr(node.text)
                }
            }

            "decimal" -> {
                AstNode.DecimalLiteral(node.text.toLong())
            }

            "string" -> {
                AstNode.StringLiteral(node.text.substring(1, node.text.length - 1))
            }

            "char" -> {
                AstNode.CharLiteral(node.text.first())
            }

            "truval" -> {
                AstNode.TruvalLiteral(node.text.toBoolean())
            }

            "tuple" -> {
                AstNode.TupleExpr(parseMultiple(node, { parseExpression(it, operators) }))
            }

            "list_expression" -> {
                AstNode.ListExpr(parseMultiple(node, { parseExpression(it, operators) }))
            }

            "set_expression" -> {
                AstNode.SetExpr(parseMultiple(node, { parseExpression(it, operators) }))
            }

            "conditional_expression" -> {
                if (node.child(1u)!!.text == "if") {
                    AstNode.IfExpr(
                        condition = parseExpression(node.getChildOrThrow(1u), operators),
                        thenBranch = parseExpression(node.getChildOrThrow(0u), operators),
                        elseBranch = parseExpression(node.getChildOrThrow(2u), operators),
                    )
                } else {
                    AstNode.IfExpr(
                        condition = parseExpression(node.getChildOrThrow(0u), operators),
                        thenBranch = parseExpression(node.getChildOrThrow(1u), operators),
                        elseBranch = parseExpression(node.getChildOrThrow(2u), operators),
                    )
                }
            }

            "local_variable_expression" -> {
                if (node.child(0u)!!.text == "let") {
                    AstNode.LetExpr(
                        pattern = parsePattern(node.getChildOrThrow(0u), operators),
                        value = parseExpression(node.getChildOrThrow(1u), operators),
                        body = parseExpression(node.getChildOrThrow(2u), operators),
                    )
                } else {
                    AstNode.LetExpr(
                        pattern = parsePattern(node.getChildOrThrow(1u), operators),
                        value = parseExpression(node.getChildOrThrow(2u), operators),
                        body = parseExpression(node.getChildOrThrow(0u), operators),
                    )
                }
            }

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

            else -> {
                throw RenamerException("Unknown expression: ${node.type} in node $node", node.range(), fatal = true)
            }
        }

    private fun <T> parseApplication(
        node: TsSyntaxNode,
        infix: MutableMap<String, Infix>,
        parse: (TsSyntaxNode) -> T?,
        constructOperand: (T, T) -> T,
        constructOperator: (String, List<T>) -> T,
    ): T {
        val expressions = parseMultiple(node, parse)
        val tokenStack = mutableListOf<T>()
        val tokens = mutableListOf<ApplicationToken>()

        fun popToken() {
            if (tokenStack.size == 1) {
                tokens.add(ApplicationToken.Operand(tokenStack.first()))
            } else if (tokenStack.size == 2) {
                tokens.add(
                    ApplicationToken.Operand(
                        constructOperand(
                            tokenStack[0],
                            tokenStack[1],
                        ),
                    ),
                )
            } else {
                throw RenamerException("Can apply only tuple or 1 argument", node.range(), fatal = true)
            }
            tokenStack.clear()
        }

        if (expressions.size != node.namedChildCount.toInt()) {
            throw RenamerException("Not all nodes were parsed", node.range(), fatal = true)
        }

        for (ind in 0u until node.namedChildCount) {
            val child = node.namedChild(ind)!!
            if (infix.contains(child.text)) {
                if (tokenStack.isEmpty()) {
                    throw RenamerException(
                        "Not fully applied operator ${child.text}",
                        node.range(),
                    )
                }
                popToken()
                tokens.add(ApplicationToken.Operator(child.text, infix[child.text]!!))
            } else {
                tokenStack.add(expressions[ind.toInt()])
            }
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
                            (operators.last().infix.priority == token.infix.priority && !token.infix.isRightAssoc)
                        ) {
                            popOperator()
                        } else {
                            break
                        }
                    }
                    operators.add(token)
                }

                is ApplicationToken.Operand<*> -> {
                    operands.add(token.expr as T)
                }
            }
        }

        repeat(operators.size) { popOperator() }

        return operands.first()
    }

    private fun parseFunctionPattern(
        node: TsSyntaxNode,
        operators: MutableMap<String, Infix>,
    ): Pair<String, AstNode.Pattern?> {
        if (node.namedChildCount == 2u) {
            val functionName = node.getChildOrThrow(0u).text
            var pattern: AstNode.Pattern? = parsePattern(node.getChildOrThrow(1u), operators)
            if (pattern is AstNode.TuplePattern && pattern.tuple.isEmpty()) {
                pattern = null
            }
            return functionName to pattern
        } else if (node.namedChildCount == 1u) {
            val functionName = node.getChildOrThrow(0u).text
            return functionName to null
        } else {
            val functionName = node.getChildOrThrow(1u).text
            val left = parsePattern(node.getChildOrThrow(0u), operators)
            val right = parsePattern(node.getChildOrThrow(2u), operators)
            return functionName to AstNode.TuplePattern(listOf(left, right))
        }
    }

    private fun parsePattern(
        node: TsSyntaxNode,
        operators: MutableMap<String, Infix>,
    ): AstNode.Pattern =
        when (node.type) {
            "pattern" -> {
                parseApplication(
                    node = node,
                    infix = operators,
                    parse = { parsePattern(it, operators) },
                    constructOperand = { func, args ->
                        if (func is AstNode.VariablePattern) {
                            AstNode.ConstructorPattern(
                                func.name,
                                if (args is AstNode.TuplePattern) args.tuple else listOf(args),
                            )
                        } else {
                            throw RenamerException(
                                "Types do not have lambdas",
                                node.range(),
                            )
                        }
                    },
                    constructOperator = { name, args -> AstNode.ConstructorPattern(name, args) },
                )
            }

            "ident" -> {
                if (node.text.startsWith("\'") && node.text.endsWith("\'") && node.text.length == 3) {
                    AstNode.CharLiteral(node.text[1])
                } else if (node.text.startsWith("\"") && node.text.endsWith("\"")) {
                    AstNode.StringLiteral(node.text.substring(1, node.text.length - 1))
                } else if (Regex("-?\\d+").matches(node.text)) {
                    AstNode.DecimalLiteral(
                        node.text.toLongOrNull() ?: throw RenamerException(
                            "Can't parse decimal literal",
                            node.range(),
                            fatal = true,
                        ),
                    )
                } else if (node.text == "true" || node.text == "false") {
                    AstNode.TruvalLiteral(node.text.toBoolean())
                } else {
                    AstNode.VariablePattern(node.text)
                }
            }

            "binding_pattern" -> {
                AstNode.BindingPattern(
                    parsePattern(node.getChildOrThrow(1u), operators),
                    node.getChildOrThrow(0u).text,
                )
            }

            "list_pattern" -> {
                val list = parseMultiple(node, { parsePattern(it, operators) })
                list.foldRight<AstNode.Pattern, AstNode.Pattern>(
                    AstNode.VariablePattern("nil"),
                ) { pattern, acc -> AstNode.ConstructorPattern("::", listOf(pattern, acc)) }
            }

            "tuple_pattern" -> {
                AstNode.TuplePattern(parseMultiple(node, { parsePattern(it, operators) }))
            }

            "wildcard_pattern" -> {
                AstNode.WildcardPattern
            }

            else -> {
                throw RenamerException("Unknown pattern: ${node.type} in node $node", node.range(), fatal = true)
            }
        }

    class TypeDeclarationException(
        node: TsSyntaxNode,
    ) : RenamerException(
            "No data constructor",
            node.range(),
        )
}
