package ru.hopec.typecheck

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.IoBuiltins
import kotlin.math.max
import kotlin.math.min

private typealias LetRange = Pair<Int, Int>

private val nullRage = 0 to 0

internal fun annotate(
    repr: DesugaredRepresentation,
    onError: (String) -> Unit = {},
): TypedRepresentation? {
    val signature = Signature.core.extendAll(repr.modules.map { it.value })
    val modules =
        repr.modules
            .map { (name, module) ->
                val result = TypecheckingContext.runModule(signature.extendLocal(module), module, onError)
                if (result == null) {
                    onError("Type checking failed in module '$name'")
                    return null
                }
                name to result
            }.toMap()
    val topLevel =
        TypecheckingContext.runDeclarations(signature.extend(repr.topLevel), repr.topLevel, onError)
    if (topLevel == null) {
        return null
    }
    return TypedRepresentation(modules, topLevel)
}

internal class TypecheckingContext private constructor(
    val signature: Signature,
    private val onError: (String) -> Unit = {},
) {
    private data class BoundVariable(
        val typeVariable: Int,
        val letRange: LetRange,
    )

    companion object {
        fun runModule(
            signature: Signature,
            module: DesugaredRepresentation.Module,
            onError: (String) -> Unit = {},
        ): TypedRepresentation.Module? = TypecheckingContext(signature, onError).runModule(module)

        fun runDeclarations(
            signature: Signature,
            declarations: DesugaredRepresentation.Declarations,
            onError: (String) -> Unit = {},
        ): TypedRepresentation.Declarations? = TypecheckingContext(signature, onError).runDeclarations(declarations)

        fun runFunction(
            signature: Signature,
            function: DesugaredRepresentation.Declarations.Function,
            name: DesugaredRepresentation.Declarations.Function.Name =
                DesugaredRepresentation.Declarations.Function.Name
                    .User(null, "test"),
            onError: (String) -> Unit = {},
        ): TypedRepresentation.Declarations.Function? = TypecheckingContext(signature, onError).runFunction(name, function)
    }

    private var substitution: ArrayList<Type> = arrayListOf()
    private var boundVariables: ArrayList<BoundVariable> = arrayListOf()

    private fun displayType(type: Type): String =
        when (val walked = walk(type)) {
            is Type.Variable -> {
                if (substitution[walked.index] == walked) {
                    "'t${walked.index}"
                } else {
                    displayType(substitution[walked.index])
                }
            }
            is Type.Arrow -> {
                val arg = displayType(walked.argument)
                val res = displayType(walked.result)
                val argStr =
                    if (walked.argument is Type.Arrow) "($arg)" else arg
                "$argStr -> $res"
            }
            is Type.Data -> {
                val name = displayDataName(walked.constructor)
                if (walked.args.isEmpty()) {
                    name
                } else if (walked.constructor == Core.Tuple && walked.args.size == 2) {
                    "(${displayType(walked.args[0])} # ${displayType(walked.args[1])})"
                } else {
                    "$name(${walked.args.joinToString(", ") { displayType(it) }})"
                }
            }
        }

    private fun displayDataName(name: DesugaredRepresentation.Declarations.Data.Name): String =
        when (name) {
            is Core.Num -> "num"
            is Core.Char -> "char"
            is Core.TruVal -> "truval"
            is Core.List -> "list"
            is Core.Set -> "set"
            is Core.Tuple -> "tuple"
            is Core.Unit -> "unit"
            is DesugaredRepresentation.Declarations.Data.Name.Defined -> {
                if (name.module != null) "${name.module}.${name.name}" else name.name
            }
        }

    private fun displayFunctionName(name: DesugaredRepresentation.Declarations.Function.Name): String =
        when (name) {
            is DesugaredRepresentation.Declarations.Function.Name.Core -> name.name
            is DesugaredRepresentation.Declarations.Function.Name.User -> {
                if (name.module != null) "${name.module}.${name.name}" else name.name
            }
            is DesugaredRepresentation.Declarations.Function.Name.Constructor -> name.constructor
        }

    private fun runModule(module: DesugaredRepresentation.Module): TypedRepresentation.Module? {
        val public = runDeclarations(module.public) ?: return null
        val private = runDeclarations(module.private) ?: return null
        return TypedRepresentation.Module(public, private)
    }

    private fun runDeclarations(declarations: DesugaredRepresentation.Declarations): TypedRepresentation.Declarations? {
        val functions =
            declarations.functions
                .toList()
                .map { (k, v) -> k to (runFunction(k, v) ?: return null) }
                .toMap()
        return TypedRepresentation.Declarations(declarations.data, functions)
    }

    private fun runFunction(
        name: DesugaredRepresentation.Declarations.Function.Name,
        function: DesugaredRepresentation.Declarations.Function,
    ): TypedRepresentation.Declarations.Function? {
        if (name is DesugaredRepresentation.Declarations.Function.Name.Core &&
            IoBuiltins.isBuiltinName(name.name.split('.').last())
        ) {
            return trustedIoBuiltin(function)
        }

        val lambda = infer(function.lambda)
        if (lambda == null) {
            onError(
                "Type error in function '${displayFunctionName(name)}': " +
                    "could not infer type of function body",
            )
            return null
        }
        val variableMapping = hashMapOf<Int, Type>()

        fun fullWalk(type: Type): Type =
            when (type) {
                is Type.Variable -> {
                    if (substitution[type.index] == type) {
                        type
                    } else {
                        fullWalk(substitution[type.index])
                    }
                }

                is Type.Arrow -> {
                    Type.Arrow(fullWalk(type.argument), fullWalk(type.result))
                }

                is Type.Data -> {
                    Type.Data(type.constructor, type.args.map { fullWalk(it) }.toList())
                }
            }

        var unifyOk = true
        var newVariable = function.type.boundTypeVariables

        fun unify(
            actual: Type,
            target: Type,
        ) {
            when (actual) {
                is Type.Variable -> {
                    val image = variableMapping[actual.index]
                    if (image == null) {
                        variableMapping[actual.index] = target
                    } else {
                        unifyOk = unifyOk && image == target
                    }
                }

                is Type.Arrow -> {
                    if (target is Type.Arrow) {
                        unify(actual.result, target.result)
                        unify(actual.argument, target.argument)
                    } else {
                        unifyOk = false
                    }
                }

                is Type.Data -> {
                    if (target !is Type.Data ||
                        target.constructor != actual.constructor ||
                        target.args.size != actual.args.size
                    ) {
                        unifyOk = false
                        return
                    }
                    actual.args.zip(target.args).forEach { (actual, target) -> unify(actual, target) }
                }
            }
        }

        fun rename(type: Type): Type =
            when (val walked = walk(type)) {
                is Type.Variable -> {
                    val image = variableMapping[walked.index]
                    if (image != null) {
                        image
                    } else {
                        variableMapping[walked.index] = Type.Variable(newVariable)
                        newVariable += 1
                        Type.Variable(newVariable - 1)
                    }
                }

                is Type.Data -> {
                    Type.Data(walked.constructor, walked.args.map(::rename).toList())
                }

                is Type.Arrow -> {
                    Type.Arrow(rename(walked.argument), rename(walked.result))
                }
            }

        fun rename(pattern: TypedRepresentation.Pattern): TypedRepresentation.Pattern =
            when (pattern) {
                is TypedRepresentation.Pattern.Data -> {
                    TypedRepresentation.Pattern.Data(
                        rename(pattern.type) as Type.Data,
                        pattern.constructor,
                        pattern.args.map(::rename).toList(),
                    )
                }

                is TypedRepresentation.Pattern.Variable -> {
                    TypedRepresentation.Pattern.Variable(
                        rename(pattern.type),
                        pattern.name,
                    )
                }

                is TypedRepresentation.Pattern.Wildcard -> {
                    TypedRepresentation.Pattern.Wildcard(rename(pattern.type))
                }

                is TypedRepresentation.Pattern.NamedData -> {
                    TypedRepresentation.Pattern.NamedData(
                        pattern.name,
                        rename(pattern.data) as TypedRepresentation.Pattern.Data,
                    )
                }

                else -> {
                    pattern
                }
            }

        fun rename(term: TypedRepresentation.Expr): TypedRepresentation.Expr =
            when (term) {
                is TypedRepresentation.Expr.Literal -> {
                    term
                }

                is TypedRepresentation.Expr.If -> {
                    TypedRepresentation.Expr.If(
                        rename(term.condition),
                        rename(term.positive),
                        rename(term.negative),
                    )
                }

                is TypedRepresentation.Expr.Variable -> {
                    TypedRepresentation.Expr.Variable(
                        rename(term.type),
                        term.name,
                    )
                }

                is TypedRepresentation.Expr.Lambda -> {
                    TypedRepresentation.Expr.Lambda(
                        rename(term.type) as Type.Arrow,
                        term.branches.map {
                            TypedRepresentation.Expr.Lambda.Branch(rename(it.pattern), rename(it.body))
                        },
                    )
                }

                is TypedRepresentation.Expr.Let -> {
                    val nv = newVariable
                    val renamedMatcher = rename(term.matcher)
                    newVariable = nv
                    TypedRepresentation.Expr.Let(
                        rename(term.pattern),
                        renamedMatcher,
                        rename(term.body),
                    )
                }

                is TypedRepresentation.Expr.Identifier -> {
                    TypedRepresentation.Expr.Identifier(
                        rename(term.type),
                        term.name,
                    )
                }

                is TypedRepresentation.Expr.Application -> {
                    TypedRepresentation.Expr.Application(
                        rename(term.type),
                        rename(term.left),
                        rename(term.right),
                    )
                }
            }

        val declaredType = function.type.type
        val inferredType = fullWalk(lambda.type)
        val typeToUnify =
            if (declaredType !is Type.Arrow && inferredType is Type.Arrow) {
                inferredType.result
            } else {
                inferredType
            }
        unify(typeToUnify, declaredType)
        if (!unifyOk) {
            onError(
                "Type error in function '${displayFunctionName(name)}': " +
                    "declared type '${displayType(declaredType)}' " +
                    "does not match inferred type '${displayType(inferredType)}'",
            )
            return null
        }

        return TypedRepresentation.Declarations.Function(
            rename(lambda) as TypedRepresentation.Expr.Lambda,
            function.type.boundTypeVariables,
        )
    }

    private fun trustedIoBuiltin(function: DesugaredRepresentation.Declarations.Function): TypedRepresentation.Declarations.Function {
        val declared = function.type.type
        val lambda =
            if (declared is Type.Arrow) {
                val body =
                    if (declared.result == Type.Data.unit) {
                        TypedRepresentation.Expr.Literal.Num(0)
                    } else {
                        TypedRepresentation.Expr.Literal.Char('\u0000')
                    }
                TypedRepresentation.Expr.Lambda(
                    declared,
                    listOf(
                        TypedRepresentation.Expr.Lambda.Branch(
                            TypedRepresentation.Pattern.Wildcard(declared.argument),
                            body,
                        ),
                    ),
                )
            } else {
                TypedRepresentation.Expr.Lambda(
                    Type.Arrow(Type.Data.num, declared),
                    listOf(
                        TypedRepresentation.Expr.Lambda.Branch(
                            TypedRepresentation.Pattern.Wildcard(Type.Data.num),
                            TypedRepresentation.Expr.Literal.Char('\u0000'),
                        ),
                    ),
                )
            }
        return TypedRepresentation.Declarations.Function(lambda, function.type.boundTypeVariables)
    }

    private fun infer(term: DesugaredRepresentation.Expr): TypedRepresentation.Expr? {
        return when (term) {
            is DesugaredRepresentation.Expr.Identifier -> {
                val candidates = term.name.filter { it in signature.functions }
                if (candidates.isEmpty()) {
                    onError(
                        "Unknown identifier: " +
                            "none of {${term.name.joinToString(", ") { displayFunctionName(it) }}} " +
                            "is defined in the current scope",
                    )
                    return null
                }
                if (candidates.size > 1) {
                    onError(
                        "Ambiguous identifier: " +
                            "multiple definitions match " +
                            "{${candidates.joinToString(", ") { displayFunctionName(it) }}}",
                    )
                    return null
                }
                val name = candidates.single()
                val type = signature.functions[name] ?: return null
                val shift = shiftValue()
                bindTypes(type.boundTypeVariables)
                TypedRepresentation.Expr.Identifier(type.type.shift(shift), name)
            }

            is DesugaredRepresentation.Expr.Variable -> {
                TypedRepresentation.Expr.Variable(refresh(boundVariables.size - term.binder - 1), term.name)
            }

            is DesugaredRepresentation.Expr.Lambda -> {
                val argType = bindType()
                val resultType = bindType()
                val result = Type.Arrow(argType, resultType)
                val branches =
                    term.branches
                        .map { branch ->
                            val branchPattern = branch.pattern ?: DesugaredRepresentation.Pattern.Wildcard
                            val (pattern, body) =
                                withBoundPattern(branchPattern, nullRage) {
                                    infer(branch.body)
                                } ?: return null
                            val bodyExpr = body ?: return@map null
                            val unified = unify(result, Type.Arrow(pattern.type, bodyExpr.type))
                            if (unified == null) {
                                onError(
                                    "Type error in lambda branch: " +
                                        "pattern type '${displayType(pattern.type)}' -> " +
                                        "'${displayType(bodyExpr.type)}' " +
                                        "does not match expected " +
                                        "'${displayType(argType)}' -> '${displayType(resultType)}'",
                                )
                                return@map null
                            }
                            TypedRepresentation.Expr.Lambda.Branch(pattern, bodyExpr)
                        }.sequence()
                        ?.toList() ?: return null

                TypedRepresentation.Expr.Lambda(result, branches)
            }

            is DesugaredRepresentation.Expr.Let -> {
                val (matcher, range) = bindRange { infer(term.matcher) }
                val (pattern, body) =
                    withBoundPattern(term.pattern, range) { pattern ->
                        val unified = unify(matcher?.type, pattern.type)
                        if (unified == null && matcher != null) {
                            onError(
                                "Type error in let binding: " +
                                    "pattern type '${displayType(pattern.type)}' " +
                                    "does not match expression type " +
                                    "'${displayType(matcher.type)}'",
                            )
                            return@withBoundPattern null
                        }
                        if (unified == null) return@withBoundPattern null
                        infer(term.body)
                    } ?: return null
                TypedRepresentation.Expr.Let(pattern, matcher ?: return null, body ?: return null)
            }

            is DesugaredRepresentation.Expr.Application -> {
                term.args.fold(infer(term.function)) { fn, arg ->
                    val right = infer(arg) ?: return@fold null
                    val resultType = bindType()
                    val unified = unify(fn?.type, Type.Arrow(right.type, resultType))
                    if (unified == null && fn != null) {
                        onError(
                            "Type error in function application: " +
                                "cannot apply argument of type " +
                                "'${displayType(right.type)}' " +
                                "to '${displayType(fn.type)}'",
                        )
                        return@fold null
                    }
                    if (unified == null) return@fold null
                    TypedRepresentation.Expr.Application(resultType, fn ?: return@fold null, right)
                }
            }

            is DesugaredRepresentation.Literal.Num -> {
                TypedRepresentation.Expr.Literal.Num(term.value)
            }

            is DesugaredRepresentation.Literal.Char -> {
                TypedRepresentation.Expr.Literal.Char(term.value)
            }

            is DesugaredRepresentation.Literal.String -> {
                TypedRepresentation.Expr.Literal.String(term.value)
            }

            is DesugaredRepresentation.Literal.TruVal -> {
                TypedRepresentation.Expr.Literal.TruVal(term.value)
            }

            is DesugaredRepresentation.Expr.If -> {
                val condition = infer(term.condition)
                val positive = infer(term.positive)
                val negative = infer(term.negative)
                val condUnify = unify(condition?.type, Type.Data.truval)
                val branchUnify = unify(positive?.type, negative?.type)
                if (condUnify == null && condition != null) {
                    onError(
                        "Type error in if-expression: " +
                            "condition has type '${displayType(condition.type)}' " +
                            "but expected 'truval'",
                    )
                }
                if (branchUnify == null && positive != null && negative != null) {
                    onError(
                        "Type error in if-expression: " +
                            "then-branch has type '${displayType(positive.type)}' " +
                            "but else-branch has type '${displayType(negative.type)}'",
                    )
                }
                if ((condUnify join branchUnify) == null) return null
                TypedRepresentation.Expr.If(
                    condition ?: return null,
                    positive ?: return null,
                    negative ?: return null,
                )
            }
        }
    }

    private fun <T> bindRange(block: TypecheckingContext.() -> T): Pair<T, LetRange> {
        val start = shiftValue()
        val result = block()
        val end = shiftValue()
        return result to (start to end)
    }

    private fun <T> withBoundPattern(
        pattern: DesugaredRepresentation.Pattern,
        letRange: LetRange,
        block: TypecheckingContext.(TypedRepresentation.Pattern) -> T,
    ): Pair<TypedRepresentation.Pattern, T>? {
        val boundBefore = boundVariables.size
        val patternType = bindPattern(pattern, letRange)
        val boundAfter = boundVariables.size
        val result = patternType?.let { it to block(it) }
        unbindVariables(boundAfter - boundBefore)
        return result
    }

    private fun bindPattern(
        pattern: DesugaredRepresentation.Pattern,
        letRange: LetRange,
    ): TypedRepresentation.Pattern? {
        return when (pattern) {
            is DesugaredRepresentation.Pattern.Data -> {
                val candidates = pattern.constructor.filter { it in signature.functions }
                if (candidates.isEmpty()) {
                    onError(
                        "Unknown constructor in pattern: " +
                            "none of {${pattern.constructor.joinToString(", ") { it.constructor }}} " +
                            "is defined in the current scope",
                    )
                    return null
                }
                if (candidates.size > 1) {
                    onError(
                        "Ambiguous constructor in pattern: " +
                            "multiple definitions match " +
                            "{${candidates.joinToString(", ") { it.constructor }}}",
                    )
                    return null
                }
                val constructor = candidates.single()
                val func = signature.functions[constructor] ?: return null
                val ctorArgs = func.type.constructorArguments()
                if (ctorArgs == null) {
                    onError(
                        "Type error in pattern: '${constructor.constructor}' " +
                            "is not a valid data constructor",
                    )
                    return null
                }
                val (args, type) = ctorArgs
                if (pattern.args.size != args.size) {
                    onError(
                        "Type error in pattern: constructor '${constructor.constructor}' " +
                            "expects ${args.size} argument(s) but got ${pattern.args.size}",
                    )
                    return null
                }
                val shift = shiftValue()
                val boundArgTypes = bindTypes(func.boundTypeVariables)

                val patternArgs =
                    pattern.args
                        .zip(args.map { arg -> arg.shift(shift) })
                        .map { (pat, arg) ->
                            val patResult = bindPattern(pat, letRange) ?: return@map null
                            val unified = unify(patResult.type, arg)
                            if (unified == null) {
                                onError(
                                    "Type error in pattern: " +
                                        "expected type '${displayType(arg)}' " +
                                        "but got '${displayType(patResult.type)}'",
                                )
                                return@map null
                            }
                            patResult
                        }.sequence() ?: return null
                TypedRepresentation.Pattern.Data(
                    Type.Data(type.constructor, boundArgTypes),
                    constructor,
                    patternArgs,
                )
            }

            is DesugaredRepresentation.Pattern.NamedData -> {
                val typed =
                    (bindPattern(pattern.data, letRange) ?: return null) as TypedRepresentation.Pattern.Data
                val unified = unify(bindVariable(letRange), typed.type)
                if (unified == null) {
                    onError(
                        "Type error in named pattern '${pattern.name}': " +
                            "could not unify binding type with " +
                            "'${displayType(typed.type)}'",
                    )
                    return null
                }
                TypedRepresentation.Pattern.NamedData(pattern.name, typed)
            }

            is DesugaredRepresentation.Pattern.Variable -> {
                TypedRepresentation.Pattern.Variable(bindVariable(letRange), pattern.name)
            }

            is DesugaredRepresentation.Pattern.Wildcard -> {
                TypedRepresentation.Pattern.Wildcard(bindType())
            }

            is DesugaredRepresentation.Literal.Num -> {
                TypedRepresentation.Expr.Literal.Num(pattern.value)
            }

            is DesugaredRepresentation.Literal.Char -> {
                TypedRepresentation.Expr.Literal.Char(pattern.value)
            }

            is DesugaredRepresentation.Literal.TruVal -> {
                TypedRepresentation.Expr.Literal.TruVal(pattern.value)
            }

            is DesugaredRepresentation.Literal.String -> {
                TypedRepresentation.Expr.Literal.String(pattern.value)
            }
        }
    }

    private fun bindTypes(binders: Int): List<Type> = generateSequence { bindType() }.take(binders).toList()

    private fun bindType(): Type.Variable {
        val typeVariable = shiftValue()
        val type = Type.Variable(typeVariable)
        substitution.add(type)
        return type
    }

    private fun bindVariable(letRange: LetRange): Type {
        val type = bindType()
        boundVariables.add(BoundVariable(type.index, letRange))
        return type
    }

    private fun unbindVariables(binders: Int) {
        repeat(binders) { boundVariables.removeLast() }
    }

    private fun shiftValue() = substitution.size

    private fun occurs(
        index: Int,
        type: Type,
    ): Boolean =
        when (val walked = walk(type)) {
            is Type.Variable -> walked.index == index
            is Type.Arrow -> occurs(index, walked.argument) || occurs(index, walked.result)
            is Type.Data -> walked.args.any { occurs(index, it) }
        }

    private fun unify(
        left: Type?,
        right: Type?,
    ): Unit? {
        if (left == null || right == null) return null
        val walkedLeft = walk(left)
        val walkedRight = walk(right)
        return when (walkedLeft) {
            is Type.Variable -> {
                when (walkedRight) {
                    is Type.Variable -> {
                        substitution[max(walkedLeft.index, walkedRight.index)] =
                            Type.Variable(
                                min(walkedLeft.index, walkedRight.index),
                            )
                    }

                    else -> {
                        if (occurs(walkedLeft.index, walkedRight)) return null
                        substitution[walkedLeft.index] = walkedRight
                    }
                }
            }

            is Type.Arrow -> {
                when (walkedRight) {
                    is Type.Variable -> {
                        if (occurs(walkedRight.index, walkedLeft)) return null
                        substitution[walkedRight.index] = walkedLeft
                    }

                    is Type.Arrow -> {
                        unify(walkedLeft.argument, walkedRight.argument) join
                            unify(
                                walkedLeft.result,
                                walkedRight.result,
                            )
                    }

                    else -> {
                        null
                    }
                }
            }

            is Type.Data -> {
                when (walkedRight) {
                    is Type.Variable -> {
                        if (occurs(walkedRight.index, walkedLeft)) return null
                        substitution[walkedRight.index] = walkedLeft
                    }

                    is Type.Data -> {
                        if (walkedLeft.args.size != walkedRight.args.size) return null
                        if (walkedLeft.constructor != walkedRight.constructor) return null
                        walkedLeft.args
                            .zip(walkedRight.args)
                            .map { (left, right) ->
                                unify(left, right)
                            }.joinAll()
                        Unit
                    }

                    else -> {
                        null
                    }
                }
            }
        }
    }

    fun rangeContains(
        range: Pair<Int, Int>,
        binder: Int,
    ) = binder in range.first..<range.second

    private fun refresh(binder: Int): Type {
        val range = boundVariables[binder].letRange
        val varMapping = hashMapOf<Int, Int>()

        fun go(type: Type): Type =
            when (val wtype = walk(type)) {
                is Type.Arrow -> {
                    Type.Arrow(go(wtype.argument), go(wtype.result))
                }

                is Type.Data -> {
                    Type.Data(wtype.constructor, wtype.args.map { go(it) })
                }

                is Type.Variable -> {
                    if (!rangeContains(range, wtype.index)) {
                        wtype
                    } else {
                        Type.Variable(varMapping.getOrPut(wtype.index) { bindType().index })
                    }
                }
            }

        return go(Type.Variable(boundVariables[binder].typeVariable))
    }

    private fun walk(type: Type): Type =
        when (type) {
            is Type.Variable -> {
                when (val image = substitution[type.index]) {
                    is Type.Variable -> {
                        if (image.index == type.index || image.index < 0) {
                            image
                        } else {
                            walk(image)
                        }
                    }

                    else -> {
                        image
                    }
                }
            }

            else -> {
                type
            }
        }
}
