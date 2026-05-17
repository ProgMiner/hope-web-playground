package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Constructor
import ru.hopec.desugarer.DesugaredRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Pattern
import ru.hopec.desugarer.DesugaredRepresentation.PolymorphicType
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.context.DesugarerGlobalContext
import ru.hopec.desugarer.context.DesugarerLocalContext
import ru.hopec.desugarer.context.DesugarerModuleContext
import ru.hopec.desugarer.context.ModuleDeclarations
import ru.hopec.renamer.AstNode

open class ModuleDesugarer(
    val globalContext: DesugarerGlobalContext = DesugarerGlobalContext(),
    val moduleContext: DesugarerModuleContext = DesugarerModuleContext(),
    val localContext: DesugarerLocalContext = DesugarerLocalContext(),
) {
    val publicConstants: MutableSet<String> = mutableSetOf()
    val publicDataTypes: MutableSet<String> = mutableSetOf()
    private var identCounter = 0

    fun resolveModule(module: AstNode.Module): DesugaredRepresentation.Module {
        val name = module.name
        val statements = module.statements
        val dataTypes: MutableMap<Data.Name.Defined, Data> = mutableMapOf()
        val functions: MutableMap<Declarations.Function.Name, Declarations.Function> = mutableMapOf()
        statements.forEach { statement ->
            when (statement) {
                is AstNode.Error -> {}

                is AstNode.DataDeclaration -> {
                    val dataType = resolveDataDecl(statement, name)
                    dataTypes[dataType.first] = dataType.second
                }

                is AstNode.FunctionDeclaration -> {
                    val function = resolveFunctionDecl(statement, name)
                    functions[function.first] = function.second
                }

                is AstNode.ModuleUseDeclaration -> {
                    statement.modules.forEach { importModule(it) }
                }

                is AstNode.ConstantExportDeclaration -> {
                    statement.constants.forEach { exportConstant(it) }
                }

                is AstNode.TypeExportDeclaration -> {
                    statement.types.forEach { exportDataType(it) }
                }
            }
        }

        val publicFunctionsMap = moduleContext.moduleFunctions.filterKeys { publicConstants.contains(it) }.toMutableMap()
        val publicConstructorsMap = moduleContext.moduleConstructors.filterKeys { publicConstants.contains(it) }.toMutableMap()
        val publicDataTypesMap = moduleContext.moduleDataTypes.filterKeys { publicConstants.contains(it) }.toMutableMap()
        if (globalContext.moduleDeclarations.containsKey(name)) {
            throw IllegalStateException("Module declarations $name already defined")
        }
        globalContext.moduleDeclarations[name] =
            ModuleDeclarations(
                publicFunctionsMap,
                publicConstructorsMap,
                publicDataTypesMap,
            )

        val publicConstants = uniteSet(publicFunctionsMap, publicConstructorsMap).values.flatten()
        val publicDataTypes = publicDataTypesMap.values.toSet()
        val moduleFunctions =
            moduleContext.moduleFunctions.values
                .flatten()
                .toSet()
        val moduleConstructors =
            moduleContext.moduleConstructors.values
                .flatten()
                .toSet()
        val privateConstants = ((moduleConstructors + moduleFunctions) - publicConstants.toSet())
        val privateData =
            moduleContext.moduleDataTypes
                .map { it.value }
                .toSet()
                .minus(publicDataTypes)

        return DesugaredRepresentation.Module(
            Declarations(
                dataTypes.filter { (name, _) -> publicDataTypes.contains(name) },
                functions.filter { (name, _) -> publicConstants.contains(name) },
            ),
            Declarations(
                dataTypes.filter { (name, _) -> privateData.contains(name) },
                functions.filter { (name, _) -> privateConstants.contains(name) },
            ),
        )
    }

    protected fun resolveDataDecl(
        data: AstNode.DataDeclaration,
        module: String?,
    ): Pair<Data.Name.Defined, Data> {
        val dataName = Data.Name.Defined(module, data.name)
        extendModuleData(dataName)
        return dataName to
            Data(
                data.dataConstructors
                    .map { (name, type) ->
                        val newName = name.generateNewName()
                        extendModuleConstructor(
                            name,
                            Constructor(dataName, newName),
                        )
                        resolveDataDeclType(newName, type, data.boundVars)
                    }.toMap(),
                data.boundVars.size,
            )
    }

    private fun resolveDataDeclType(
        name: String,
        type: AstNode.TypeExpr?,
        boundVars: List<String>,
    ): Pair<String, List<Type>> {
        if (type == null) {
            return name to emptyList()
        }

        fun pairToList(pair: AstNode.TypeExpr): List<AstNode.TypeExpr> =
            when (pair) {
                is AstNode.ProductType -> {
                    listOf(pair.left) + pairToList(pair.right)
                }

                else -> {
                    listOf(pair)
                }
            }

        return Pair(name, pairToList(type).map { resolveType(it, boundVars) })
    }

    protected fun resolveFunctionDecl(
        function: AstNode.FunctionDeclaration,
        module: String?,
    ): Pair<Declarations.Function.Name, Declarations.Function> {
        val newName = function.name.generateNewName()
        val functionName = Declarations.Function.Name.User(module, newName)
        extendModuleFunction(function.name, functionName)
        return functionName to resolveFunction(function)
    }

    private fun resolveFunction(data: AstNode.FunctionDeclaration): Declarations.Function {
        val type = PolymorphicType(resolveType(data.typeExpr, data.boundVars), data.boundVars.size)
        val lambda =
            Expr.Lambda(
                data.equations.map { equation ->
                    newScope().let {
                        val pattern = equation.pattern?.let { resolvePattern(it) }
                        Expr.Lambda.Branch(pattern, resolveExpression(equation.body))
                    }
                },
            )
        return Declarations.Function(lambda, type)
    }

    private fun resolveExpression(expr: AstNode.Expr): Expr {
        fun listToExpr(
            list: List<AstNode.Expr>,
            nil: Declarations.Function.Name,
            cons: Declarations.Function.Name,
        ): Expr =
            if (list.isNotEmpty()) {
                Expr.Application(
                    Expr.Identifier(setOf(cons)),
                    listOf(
                        newScope().resolveExpression(list.first()),
                        listToExpr(list.drop(1), nil, cons),
                    ),
                )
            } else {
                Expr.Identifier(setOf(nil))
            }

        fun listToTuple(list: List<AstNode.Expr>): Expr =
            if (list.size > 1) {
                Expr.Application(
                    Expr.Identifier(setOf(tupleConstr)),
                    listOf(
                        newScope().resolveExpression(list.first()),
                        listToTuple(list.drop(1)),
                    ),
                )
            } else if (list.size == 1) {
                newScope().resolveExpression(list.first())
            } else {
                Expr.Application(Expr.Identifier(setOf(tupleConstr)), emptyList())
            }

        return when (expr) {
            is AstNode.LambdaExpr -> {
                Expr.Lambda(
                    expr.branches.map { branch ->
                        newScope().let { scope ->
                            val pattern = scope.resolvePattern(branch.pattern)
                            Expr.Lambda.Branch(pattern, scope.resolveExpression(branch.expression))
                        }
                    },
                )
            }

            is AstNode.IfExpr -> {
                Expr.If(
                    resolveExpression(expr.condition),
                    resolveExpression(expr.thenBranch),
                    resolveExpression(expr.elseBranch),
                )
            }

            is AstNode.LetExpr -> {
                newScope().let { scope ->
                    val matcher = newScope().resolveExpression(expr.value)
                    val pattern = scope.resolvePattern(expr.pattern)
                    val body = scope.resolveExpression(expr.body)
                    Expr.Let(pattern, matcher, body)
                }
            }

            is AstNode.DecimalLiteral -> {
                Expr.Literal.Num(expr.value)
            }

            is AstNode.CharLiteral -> {
                Expr.Literal.Char(expr.char)
            }

            is AstNode.StringLiteral -> {
                Expr.Literal.String(expr.string)
            }

            is AstNode.TruvalLiteral -> {
                Expr.Literal.TruVal(expr.bool)
            }

            is AstNode.SetExpr -> {
                listToExpr(expr.list, emptySetConstr, setConstr)
            }

            is AstNode.ListExpr -> {
                listToExpr(expr.list, nilConstr, consConstr)
            }

            is AstNode.TupleExpr -> {
                listToTuple(expr.elements)
            }

            is AstNode.ApplicationExpr -> {
                Expr.Application(
                    newScope().resolveExpression(expr.function),
                    expr.arguments.map { newScope().resolveExpression(it) },
                )
            }

            is AstNode.IdentExpr -> {
                when (val resolved = resolveExpr(expr.name)) {
                    is ResolvedExpr.Local -> {
                        Expr.Variable(expr.name, resolved.level)
                    }

                    is ResolvedExpr.GlobalSet -> {
                        Expr.Identifier(resolved.idents)
                    }
                }
            }
        }
    }

    private fun resolvePattern(pattern: AstNode.Pattern): Pattern =
        when (pattern) {
            is AstNode.VariablePattern -> {
                when (val resolved = resolvePatternNullary(pattern.name)) {
                    is ResolvedPattern.Var -> Pattern.Variable(pattern.name)
                    is ResolvedPattern.GlobalSet -> Pattern.Data(resolved.idents, emptyList())
                }
            }

            is AstNode.BindingPattern -> {
                Pattern.NamedData(pattern.bindName, resolvePattern(pattern.pattern) as Pattern.Data).also {
                    extendLocal(listOf(pattern.bindName))
                }
            }

            is AstNode.TuplePattern -> {
                Pattern.Data(
                    setOf(tupleConstr),
                    args = pattern.tuple.map { resolvePattern(it) },
                )
            }

            is AstNode.WildcardPattern -> {
                Pattern.Wildcard
            }

            is AstNode.ConstructorPattern -> {
                Pattern.Data(
                    resolvePattern(pattern.constructor).idents,
                    args = pattern.arguments.map { resolvePattern(it) },
                )
            }
        }

    private fun resolveType(
        type: AstNode.TypeExpr,
        boundVars: List<String>,
    ): Type =
        when (type) {
            is AstNode.ProductType -> {
                Type.Data.tuple(resolveType(type.left, boundVars), resolveType(type.right, boundVars))
            }

            is AstNode.FunctionalType -> {
                Type.Arrow(resolveType(type.premise, boundVars), resolveType(type.result, boundVars))
            }

            is AstNode.NamedType -> {
                when (type.type) {
                    "char" -> Type.Data.char
                    "truval" -> Type.Data.truval
                    "num" -> Type.Data.num
                    "string" -> Type.Data.string
                    "list" -> Type.Data.list(resolveType(type.arguments[0], boundVars))
                    "set" -> Type.Data.set(resolveType(type.arguments[0], boundVars))
                    else -> Type.Data(resolveData(type.type), type.arguments.map { resolveType(it, boundVars) })
                }
            }

            is AstNode.VarType -> {
                Type.Variable(boundVars.size - boundVars.indexOf(type.name))
            }
        }

    fun extendLocal(vars: List<String>) {
        localContext.extendLocal(vars)
    }

    fun extendModuleFunction(
        name: String,
        function: Declarations.Function.Name.User,
    ) {
        moduleContext.extendModuleFunction(name, function)
    }

    fun extendModuleConstructor(
        name: String,
        constructor: Constructor,
    ) {
        moduleContext.extendModuleConstructor(name, constructor)
    }

    fun extendModuleData(name: Data.Name.Defined) {
        moduleContext.extendModuleData(name)
    }

    fun importModule(name: String) {
        val declarations =
            globalContext.moduleDeclarations[name]
                ?: throw IllegalStateException("No module found for $name")
        val functions = declarations.functions
        val dataTypes = declarations.dataTypes
        moduleContext.extendGlobalFunctions(functions)
        moduleContext.extendGlobalData(dataTypes)
    }

    fun exportConstant(name: String) {
        publicConstants.add(name)
    }

    fun exportDataType(name: String) {
        publicDataTypes.add(name)
    }

    fun resolveExpr(name: String) =
        localContext.getLocalVar(name) ?: moduleContext.resolveExpr(name) ?: throw IllegalArgumentException("$name not found")

    fun resolvePattern(name: String) = moduleContext.resolvePattern(name) ?: throw IllegalArgumentException("$name not found")

    fun resolvePatternNullary(name: String) = moduleContext.resolvePattern(name) ?: localContext.resolvePatternVar(name)

    fun resolveData(name: String) = moduleContext.resolveData(name) ?: throw IllegalArgumentException("$name not found")

    private fun String.generateNewName() = (this + "_" + identCounter.toString()).also { identCounter++ }
}

private fun ModuleDesugarer.newScope() = ModuleDesugarer(this.globalContext, this.moduleContext, this.localContext.copy())
