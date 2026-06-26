package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function
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
    val localModuleDeclarations: MutableMap<String, ModuleDeclarations> = mutableMapOf(),
) {
    val publicFunctionsMap: MutableMap<String, MutableSet<Function.Name>> = mutableMapOf()
    val publicConstructorsMap: MutableMap<String, MutableSet<Constructor>> = mutableMapOf()
    val publicDataTypesMap: MutableMap<String, Data.Name.Defined> = mutableMapOf()
    private var identCounter = 0

    fun resolveModule(module: AstNode.Module): DesugaredRepresentation.Module {
        val name = module.name
        if (globalContext.moduleDeclarations.containsKey(name)) {
            throw IllegalStateException("Module '$name' already defined")
        }
        val statements = module.statements
        val dataTypes: MutableMap<Data.Name.Defined, Data> = mutableMapOf()
        val functions: MutableMap<Function.Name, Function> = mutableMapOf()
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

                is AstNode.ConstantExportDeclaration -> {}

                is AstNode.TypeExportDeclaration -> {}
            }
        }

        statements.forEach { statement ->
            when (statement) {
                is AstNode.ConstantExportDeclaration -> {
                    statement.constants.forEach { exportConstant(it) }
                }

                is AstNode.TypeExportDeclaration -> {
                    statement.types.forEach { exportDataType(it) }
                }

                else -> {}
            }
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
                dataTypes.filter { publicDataTypes.contains(it.key) },
                functions.filter { publicConstants.contains(it.key) },
            ),
            Declarations(
                dataTypes.filter { privateData.contains(it.key) },
                functions.filter { privateConstants.contains(it.key) },
            ),
        )
    }

    fun resolveModuleLocally(module: AstNode.Module): DesugaredRepresentation.Module {
        val name = module.name
        if (localModuleDeclarations.containsKey(name)) {
            throw IllegalStateException("Module '$name' already defined in this file")
        }
        val statements = module.statements
        val dataTypes: MutableMap<Data.Name.Defined, Data> = mutableMapOf()
        val functions: MutableMap<Function.Name, Function> = mutableMapOf()
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

                is AstNode.ConstantExportDeclaration -> {}

                is AstNode.TypeExportDeclaration -> {}
            }
        }

        statements.forEach { statement ->
            when (statement) {
                is AstNode.ConstantExportDeclaration -> {
                    statement.constants.forEach { exportConstant(it) }
                }

                is AstNode.TypeExportDeclaration -> {
                    statement.types.forEach { exportDataType(it) }
                }

                else -> {}
            }
        }

        localModuleDeclarations[name] =
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
                dataTypes.filter { publicDataTypes.contains(it.key) },
                functions.filter { publicConstants.contains(it.key) },
            ),
            Declarations(
                dataTypes.filter { privateData.contains(it.key) },
                functions.filter { privateConstants.contains(it.key) },
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
    ): Pair<Function.Name, Function> {
        if (module in IoBuiltins.MODULES && IoBuiltins.isBuiltinName(function.name)) {
            val builtin = IoBuiltins.coreName(function.name)
            extendModuleFunction(function.name, builtin)
            val type = PolymorphicType(resolveType(function.typeExpr, function.boundVars), function.boundVars.size)
            val stubBody = DesugaredRepresentation.Literal.Num(0)
            return builtin to
                Function(
                    Expr.Lambda(listOf(Expr.Lambda.Branch(null, stubBody))),
                    type,
                )
        }

        val newName =
            if (module == null && function.name == "main") {
                function.name
            } else {
                function.name.generateNewName()
            }
        val functionName = Function.Name.User(module, newName)
        extendModuleFunction(function.name, functionName)
        return functionName to resolveFunction(function)
    }

    private fun resolveFunction(data: AstNode.FunctionDeclaration): Function {
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
        return Function(lambda, type)
    }

    private fun resolveExpression(expr: AstNode.Expr): Expr {
        fun listToExpr(
            list: List<AstNode.Expr>,
            nil: Function.Name,
            cons: Function.Name,
        ): Expr =
            if (list.isNotEmpty()) {
                Expr.Application(
                    Expr.Identifier(setOf(cons)),
                    listOf(
                        Expr.Application(
                            Expr.Identifier(setOf(tupleConstr)),
                            listOf(
                                newScope().resolveExpression(list.first()),
                                listToExpr(list.drop(1), nil, cons),
                            ),
                        ),
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
                    val matcher = scope.resolveExpression(expr.value)
                    val pattern = scope.resolvePattern(expr.pattern)
                    val body = scope.resolveExpression(expr.body)
                    Expr.Let(pattern, matcher, body)
                }
            }

            is AstNode.DecimalLiteral -> {
                DesugaredRepresentation.Literal.Num(expr.value)
            }

            is AstNode.CharLiteral -> {
                DesugaredRepresentation.Literal.Char(expr.char)
            }

            is AstNode.StringLiteral -> {
                DesugaredRepresentation.Literal.String(expr.string)
            }

            is AstNode.TruvalLiteral -> {
                DesugaredRepresentation.Literal.TruVal(expr.bool)
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
                val args = expr.arguments.map { resolveExpression(it) }
                val packed =
                    when (args.size) {
                        0 -> emptyList()
                        1 -> args
                        else -> listOf(packLeftExpr(args))
                    }
                Expr.Application(
                    resolveExpression(expr.function),
                    packed,
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
                when (pattern.tuple.size) {
                    0 -> Pattern.Data(setOf(tupleConstr), emptyList())
                    1 -> resolvePattern(pattern.tuple.single())
                    else -> packLeftPattern(pattern.tuple.map { resolvePattern(it) })
                }
            }

            is AstNode.WildcardPattern -> {
                Pattern.Wildcard
            }

            is AstNode.CharLiteral -> {
                DesugaredRepresentation.Literal.Char(pattern.char)
            }

            is AstNode.DecimalLiteral -> {
                DesugaredRepresentation.Literal.Num(pattern.value)
            }

            is AstNode.TruvalLiteral -> {
                DesugaredRepresentation.Literal.TruVal(pattern.bool)
            }

            is AstNode.StringLiteral -> {
                DesugaredRepresentation.Literal.String(pattern.string)
            }

            is AstNode.ConstructorPattern -> {
                val args = pattern.arguments.map { resolvePattern(it) }
                val packed =
                    when (args.size) {
                        0 -> emptyList()
                        1 -> args
                        else -> listOf(packLeftPattern(args))
                    }
                Pattern.Data(
                    resolvePattern(pattern.constructor).idents,
                    args = packed,
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
                    "unit" -> Type.Data.unit
                    "string" -> Type.Data.string
                    "list" -> Type.Data.list(resolveType(type.arguments[0], boundVars))
                    "set" -> Type.Data.set(resolveType(type.arguments[0], boundVars))
                    else -> Type.Data(resolveData(type.type), type.arguments.map { resolveType(it, boundVars) })
                }
            }

            is AstNode.VarType -> {
                Type.Variable(boundVars.size - boundVars.indexOf(type.name) - 1)
            }
        }

    fun extendLocal(vars: List<String>) {
        localContext.extendLocal(vars)
    }

    fun extendModuleFunction(
        name: String,
        function: Function.Name,
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
        if (moduleContext.moduleDataTypes.containsKey(name.name)) {
            throw IllegalStateException("Data declaration $name already defined")
        }
        moduleContext.extendModuleData(name)
    }

    fun importModule(name: String) {
        val localDecl = localModuleDeclarations[name]
        if (localDecl != null) {
            moduleContext.extendGlobalFunctions(localDecl.functions)
            moduleContext.extendGlobalConstructors(localDecl.constructors)
            moduleContext.extendGlobalData(localDecl.dataTypes)
            return
        }

        val fileDecl = globalContext.fileDeclarations[name]
        if (fileDecl != null) {
            moduleContext.extendGlobalFunctions(fileDecl.functions)
            moduleContext.extendGlobalConstructors(fileDecl.constructors)
            moduleContext.extendGlobalData(fileDecl.dataTypes)
            return
        }

        val declarations =
            globalContext.moduleDeclarations[name]
                ?: throw IllegalStateException("No module or file found for '$name'")
        val functions = declarations.functions
        val dataTypes = declarations.dataTypes
        moduleContext.extendGlobalFunctions(functions)
        moduleContext.extendGlobalConstructors(declarations.constructors)
        moduleContext.extendGlobalData(dataTypes)
    }

    fun exportConstant(name: String) {
        val constructorSet = moduleContext.moduleConstructors[name] ?: emptySet()
        val functionsSet = moduleContext.moduleFunctions[name] ?: emptySet()

        val globalConstructorSet = moduleContext.globalConstructors[name] ?: emptySet()
        val globalFunctionsSet = moduleContext.globalFunctions[name] ?: emptySet()

        val allConstructors = constructorSet + globalConstructorSet
        val allFunctions = functionsSet + globalFunctionsSet

        if (allConstructors.isEmpty() && allFunctions.isEmpty()) {
            throw IllegalArgumentException("No constant found for '$name' (neither local nor imported)")
        }
        publicConstructorsMap.getOrPut(name) { mutableSetOf() }.addAll(allConstructors)
        publicFunctionsMap.getOrPut(name) { mutableSetOf() }.addAll(allFunctions)
    }

    fun exportDataType(name: String) {
        publicDataTypesMap[name] = moduleContext.moduleDataTypes[name]
            ?: throw IllegalStateException("No module data found for $name")
    }

    fun resolveExpr(name: String) =
        localContext.getLocalVar(name) ?: moduleContext.resolveExpr(name)
            ?: throw IllegalArgumentException("$name not found")

    fun resolvePattern(name: String) = moduleContext.resolvePattern(name) ?: throw IllegalArgumentException("$name not found")

    fun resolvePatternNullary(name: String) = moduleContext.resolvePattern(name) ?: localContext.resolvePatternVar(name)

    fun resolveData(name: String) = moduleContext.resolveData(name) ?: throw IllegalArgumentException("$name not found")

    private fun String.generateNewName() = (this + "_" + identCounter.toString()).also { identCounter++ }

    private fun packLeftExpr(args: List<Expr>): Expr =
        args.reduce { left, right ->
            Expr.Application(Expr.Identifier(setOf(tupleConstr)), listOf(left, right))
        }

    private fun packLeftPattern(args: List<Pattern>): Pattern =
        args.reduce { left, right ->
            Pattern.Data(setOf(tupleConstr), listOf(left, right))
        }
}

private fun ModuleDesugarer.newScope() =
    ModuleDesugarer(this.globalContext, this.moduleContext, this.localContext.fork(), this.localModuleDeclarations)
