package ru.hopec.typecheck

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.math.max
import kotlin.math.min

private typealias LetRange = Pair<Int, Int>

private val nullRage = 0 to 0

internal fun annotate(repr: DesugaredRepresentation): TypedRepresentation {
    val signature = Signature.core.extendAll(repr.modules.map { it.value })
    val modules =
        repr.modules.map { it.key to TypecheckingContext.runModule(signature.extendLocal(it.value), it.value) }.toMap()
    val topLevel = TypecheckingContext.runDeclarations(signature.extend(repr.topLevel), repr.topLevel)
    return TypedRepresentation(modules, topLevel)
}

internal class TypecheckingContext private constructor(
    val signature: Signature,
) {
    private data class BoundVariable(
        val typeVariable: Int,
        val letRange: LetRange,
    )

    companion object {
        fun runModule(
            signature: Signature,
            module: DesugaredRepresentation.Module,
        ): TypedRepresentation.Module = TypecheckingContext(signature).runModule(module)

        fun runDeclarations(
            signature: Signature,
            declarations: DesugaredRepresentation.Declarations,
        ): TypedRepresentation.Declarations = TypecheckingContext(signature).runDeclarations(declarations)

        fun runFunction(
            signature: Signature,
            function: DesugaredRepresentation.Declarations.Function,
        ): TypedRepresentation.Declarations.Function? = TypecheckingContext(signature).runFunction(function)
    }

    private var substitution: ArrayList<Type> = arrayListOf()
    private var boundVariables: ArrayList<BoundVariable> = arrayListOf()

    private fun runModule(module: DesugaredRepresentation.Module): TypedRepresentation.Module =
        TypedRepresentation.Module(runDeclarations(module.public), runDeclarations(module.private))

    private fun runDeclarations(declarations: DesugaredRepresentation.Declarations): TypedRepresentation.Declarations =
        TypedRepresentation.Declarations(
            declarations.data,
            declarations.functions
                .toList()
                .flatMap { (k, v) -> runFunction(v).nullAsList().map { k to it } }
                .toMap(),
        )

    private fun runFunction(function: DesugaredRepresentation.Declarations.Function): TypedRepresentation.Declarations.Function? {
        val lambda = infer(function.lambda) ?: return null
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

                is Type.Arrow -> Type.Arrow(fullWalk(type.argument), fullWalk(type.result))
                is Type.Data -> Type.Data(type.constructor, type.args.map { fullWalk(it) }.toList())
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
                is Type.Data -> Type.Data(walked.constructor, walked.args.map(::rename).toList())
                is Type.Arrow -> Type.Arrow(rename(walked.argument), rename(walked.result))
            }

        fun rename(pattern: TypedRepresentation.Pattern): TypedRepresentation.Pattern =
            when (pattern) {
                is TypedRepresentation.Pattern.Data ->
                    TypedRepresentation.Pattern.Data(
                        rename(pattern.type) as Type.Data,
                        pattern.constructor,
                        pattern.args.map(::rename).toList(),
                    )

                is TypedRepresentation.Pattern.Variable ->
                    TypedRepresentation.Pattern.Variable(
                        rename(pattern.type),
                        pattern.name,
                    )

                is TypedRepresentation.Pattern.Wildcard -> TypedRepresentation.Pattern.Wildcard(rename(pattern.type))
                is TypedRepresentation.Pattern.NamedData ->
                    TypedRepresentation.Pattern.NamedData(
                        pattern.name,
                        rename(pattern.data) as TypedRepresentation.Pattern.Data,
                    )
            }

        fun rename(term: TypedRepresentation.Expr): TypedRepresentation.Expr =
            when (term) {
                is TypedRepresentation.Expr.Literal -> term
                is TypedRepresentation.Expr.If ->
                    TypedRepresentation.Expr.If(
                        rename(term.condition),
                        rename(term.positive),
                        rename(term.negative),
                    )

                is TypedRepresentation.Expr.Variable ->
                    TypedRepresentation.Expr.Variable(
                        rename(term.type),
                        term.name,
                    )

                is TypedRepresentation.Expr.Lambda ->
                    TypedRepresentation.Expr.Lambda(
                        rename(term.type) as Type.Arrow,
                        term.branches.map {
                            TypedRepresentation.Expr.Lambda.Branch(rename(it.pattern), rename(it.body))
                        },
                    )

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

                is TypedRepresentation.Expr.Identifier ->
                    TypedRepresentation.Expr.Identifier(
                        rename(term.type),
                        term.name,
                    )

                is TypedRepresentation.Expr.Application ->
                    TypedRepresentation.Expr.Application(
                        rename(term.type),
                        rename(term.left),
                        rename(term.right),
                    )
            }

        unify(fullWalk(lambda.type), function.type.type)
        if (!unifyOk) return null

        return TypedRepresentation.Declarations.Function(
            rename(lambda) as TypedRepresentation.Expr.Lambda,
            function.type.boundTypeVariables,
        )
    }

    private fun infer(term: DesugaredRepresentation.Expr): TypedRepresentation.Expr? {
        return when (term) {
            is DesugaredRepresentation.Expr.Identifier -> {
                val type = signature.functions[term.name] ?: return null
                val shift = shiftValue()
                bindTypes(type.boundTypeVariables)
                TypedRepresentation.Expr.Identifier(type.type.shift(shift), term.name)
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
                            val (pattern, body) =
                                withBoundPattern(branch.pattern, nullRage) {
                                    infer(branch.body)
                                } ?: return null
                            unify(result, Type.Arrow(pattern.type, body?.type ?: return null)) ?: return@map null
                            TypedRepresentation.Expr.Lambda.Branch(pattern, body)
                        }.sequence()
                        ?.toList() ?: return null

                TypedRepresentation.Expr.Lambda(result, branches)
            }

            is DesugaredRepresentation.Expr.Let -> {
                val (matcher, range) = bindRange { infer(term.matcher) }
                val (pattern, body) =
                    withBoundPattern(term.pattern, range) { pattern ->
                        unify(matcher?.type, pattern.type) ?: return@withBoundPattern null
                        infer(term.body)
                    } ?: return null
                TypedRepresentation.Expr.Let(pattern, matcher ?: return null, body ?: return null)
            }

            is DesugaredRepresentation.Expr.Application -> {
                val left = infer(term.left)
                val right = infer(term.right) ?: return null
                val resultType = bindType()
                unify(left?.type, Type.Arrow(right.type, resultType)) ?: return null
                TypedRepresentation.Expr.Application(resultType, left ?: return null, right)
            }

            is DesugaredRepresentation.Expr.Literal.Num -> TypedRepresentation.Expr.Literal.Num(term.value)
            is DesugaredRepresentation.Expr.Literal.Char -> TypedRepresentation.Expr.Literal.Char(term.value)
            is DesugaredRepresentation.Expr.Literal.String -> TypedRepresentation.Expr.Literal.String(term.value)
            is DesugaredRepresentation.Expr.Literal.TruVal -> TypedRepresentation.Expr.Literal.TruVal(term.value)

            is DesugaredRepresentation.Expr.If -> {
                val condition = infer(term.condition)
                val positive = infer(term.positive)
                val negative = infer(term.negative)
                (
                    unify(condition?.type, Type.Data.truval) join
                        unify(
                            positive?.type,
                            negative?.type,
                        )
                ) ?: return null
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
                val func = signature.functions[pattern.constructor] ?: return null
                val (args, type) = func.type.constructorArguments() ?: return null
                if (pattern.args.size != args.size) return null
                val shift = shiftValue()
                val boundArgTypes = bindTypes(func.boundTypeVariables)
                val patternArgs =
                    pattern.args
                        .zip(args.map { it.shift(shift) })
                        .map { (pat, arg) ->
                            val pattern = bindPattern(pat, letRange) ?: return@map null
                            unify(pattern.type, arg) ?: return@map null
                            pattern
                        }.sequence() ?: return null
                TypedRepresentation.Pattern.Data(
                    Type.Data(type.constructor, boundArgTypes),
                    pattern.constructor,
                    patternArgs,
                )
            }

            is DesugaredRepresentation.Pattern.NamedData -> {
                val typed = (bindPattern(pattern.data, letRange) ?: return null) as TypedRepresentation.Pattern.Data
                unify(bindVariable(letRange), typed.type) ?: return null
                TypedRepresentation.Pattern.NamedData(pattern.name, typed)
            }

            is DesugaredRepresentation.Pattern.Variable -> {
                TypedRepresentation.Pattern.Variable(bindVariable(letRange), pattern.name)
            }

            is DesugaredRepresentation.Pattern.Wildcard -> {
                TypedRepresentation.Pattern.Wildcard(bindType())
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

                    else -> substitution[walkedLeft.index] = walkedRight
                }
            }

            is Type.Arrow -> {
                when (walkedRight) {
                    is Type.Variable -> {
                        substitution[walkedRight.index] = walkedLeft
                    }

                    is Type.Arrow ->
                        unify(walkedLeft.argument, walkedRight.argument) join
                            unify(
                                walkedLeft.result,
                                walkedRight.result,
                            )

                    else -> null
                }
            }

            is Type.Data -> {
                when (walkedRight) {
                    is Type.Variable -> {
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

                    else -> null
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
                is Type.Arrow -> Type.Arrow(go(wtype.argument), go(wtype.result))
                is Type.Data -> Type.Data(wtype.constructor, wtype.args.map { go(it) })
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

                    else -> image
                }
            }

            else -> type
        }
}
