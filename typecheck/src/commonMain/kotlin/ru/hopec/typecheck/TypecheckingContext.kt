package ru.hopec.typecheck

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.desugarer.IoBuiltins
import ru.hopec.desugarer.tupleConstr
import kotlin.math.max
import kotlin.math.min

private typealias LetRange = Pair<Int, Int>

private val nullRage = 0 to 0

internal fun annotate(
    program: DesugaredRepresentation,
    onError: (String) -> Unit = {},
): TypedRepresentation? {
    val repr = program.withPrelude()
    val declarations = repr.allDeclarations()
    val signature =
        Signature.core
            .extendAll(repr.modules.map { it.value })
            .withInstances(declarations)
    if (!checkClassDeclarations(declarations, signature.extend(repr.topLevel), onError)) {
        return null
    }
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

    private data class DictionaryHole(
        val cls: DesugaredRepresentation.Declarations.Class.Name,
        val type: Type,
    )

    private var substitution: ArrayList<Type> = arrayListOf()
    private var boundVariables: ArrayList<BoundVariable> = arrayListOf()
    private val holes: LinkedHashMap<String, DictionaryHole> = linkedMapOf()
    private var holeCounter = 0
    private var selectionCounter = 0

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

    private fun displayDeclaredType(type: Type): String =
        when (type) {
            is Type.Variable -> {
                "'t${type.index}"
            }
            is Type.Arrow -> {
                val arg = displayDeclaredType(type.argument)
                val res = displayDeclaredType(type.result)
                val argStr =
                    if (type.argument is Type.Arrow) "($arg)" else arg
                "$argStr -> $res"
            }
            is Type.Data -> {
                val name = displayDataName(type.constructor)
                if (type.args.isEmpty()) {
                    name
                } else if (type.constructor == Core.Tuple && type.args.size == 2) {
                    "(${displayDeclaredType(type.args[0])} # ${displayDeclaredType(type.args[1])})"
                } else {
                    "$name(${type.args.joinToString(", ") { displayDeclaredType(it) }})"
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

    private fun displayClassName(name: DesugaredRepresentation.Declarations.Class.Name): String =
        when (name) {
            is DesugaredRepresentation.Declarations.Class.Name.Core -> name.name
            is DesugaredRepresentation.Declarations.Class.Name.Defined -> {
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
        val dictionaries =
            declarations.instances
                .flatMap { instance -> (runInstance(instance) ?: return null).toList() }
                .toMap()
        return TypedRepresentation.Declarations(declarations.data, functions + dictionaries)
    }

    private fun runInstance(
        instance: DesugaredRepresentation.Declarations.Instance,
    ): Map<DesugaredRepresentation.Declarations.Function.Name, TypedRepresentation.Declarations.Function>? {
        val methods =
            signature.classes
                .getValue(instance.cls)
                .methods.keys
        val single = methods.size == 1
        val functions =
            methods.associate { method ->
                val name: DesugaredRepresentation.Declarations.Function.Name =
                    if (single) instance.dictionary else instance.method(method)
                val function =
                    DesugaredRepresentation.Declarations.Function(
                        instance.methods.getValue(method),
                        signature.instanceMethodType(instance, method),
                    )
                val result = runFunction(name, function)
                if (result == null) {
                    onError(
                        "Type error in instance '${displayClassName(instance.cls)} ${displayDataName(instance.head.constructor)}': " +
                            "could not check method '${displayFunctionName(method)}'",
                    )
                    return null
                }
                name to result
            }
        if (single) {
            return functions
        }
        return functions + (instance.dictionary to instanceDictionary(instance, functions))
    }

    private fun instanceDictionary(
        instance: DesugaredRepresentation.Declarations.Instance,
        methods: Map<DesugaredRepresentation.Declarations.Function.Name, TypedRepresentation.Declarations.Function>,
    ): TypedRepresentation.Declarations.Function {
        val parameters =
            instance.context.map { predicate ->
                TypedRepresentation.Expr.Variable(
                    signature.dictionaryType(predicate.cls, Type.Variable(predicate.variable)),
                    dictionaryParameter(predicate),
                )
            }
        val implementations =
            methods.map { (name, function) ->
                parameters.fold(
                    TypedRepresentation.Expr.Identifier(function.lambda.type, name) as TypedRepresentation.Expr,
                ) { implementation, parameter ->
                    TypedRepresentation.Expr.Application((implementation.type as Type.Arrow).result, implementation, parameter)
                }
            }
        val dictionary =
            implementations.reduceRight { method, rest ->
                val type = Type.Data.tuple(method.type, rest.type)
                TypedRepresentation.Expr.Application(
                    type,
                    TypedRepresentation.Expr.Application(
                        Type.Arrow(rest.type, type),
                        TypedRepresentation.Expr.Identifier(Type.Arrow(method.type, Type.Arrow(rest.type, type)), tupleConstr),
                        method,
                    ),
                    rest,
                )
            }
        val lambda =
            if (instance.context.isEmpty()) {
                TypedRepresentation.Expr.Lambda(
                    Type.Arrow(Type.Data.num, dictionary.type),
                    listOf(TypedRepresentation.Expr.Lambda.Branch(TypedRepresentation.Pattern.Wildcard(Type.Data.num), dictionary)),
                )
            } else {
                abstractDictionaries(instance.context, dictionary) as TypedRepresentation.Expr.Lambda
            }
        return TypedRepresentation.Declarations.Function(lambda, instance.boundTypeVariables)
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

        val context = function.type.context
        if (context.isNotEmpty() && function.type.type !is Type.Arrow) {
            onError(
                "Type error in function '${displayFunctionName(name)}': " +
                    "class constraints are supported only for functions with arguments",
            )
            return null
        }

        holes.clear()
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

        val renamedLambda = rename(lambda) as TypedRepresentation.Expr.Lambda
        val holeTypes = holes.mapValues { (_, hole) -> hole.cls to rename(hole.type) }
        val resolved =
            resolveDictionaries(name, renamedLambda, holeTypes, function.type.boundTypeVariables, context)
                ?: return null

        return TypedRepresentation.Declarations.Function(
            resolved,
            function.type.boundTypeVariables,
        )
    }

    private fun dictionaryParameter(predicate: DesugaredRepresentation.Predicate) = "dict.${predicate.cls.name}.${predicate.variable}"

    private fun resolveDictionaries(
        name: DesugaredRepresentation.Declarations.Function.Name,
        lambda: TypedRepresentation.Expr.Lambda,
        holeTypes: Map<String, Pair<DesugaredRepresentation.Declarations.Class.Name, Type>>,
        boundTypeVariables: Int,
        context: List<DesugaredRepresentation.Predicate>,
    ): TypedRepresentation.Expr.Lambda? {
        fun entail(
            cls: DesugaredRepresentation.Declarations.Class.Name,
            type: Type,
        ): TypedRepresentation.Expr? {
            val clsName = displayClassName(cls)
            return when (type) {
                is Type.Variable -> {
                    val predicate = DesugaredRepresentation.Predicate(cls, type.index)
                    if (type.index >= boundTypeVariables) {
                        onError(
                            "Type error in function '${displayFunctionName(name)}': " +
                                "ambiguous type variable in constraint '$clsName ${displayDeclaredType(type)}'",
                        )
                        null
                    } else if (predicate !in context) {
                        onError(
                            "Type error in function '${displayFunctionName(name)}': " +
                                "no instance for '$clsName ${displayDeclaredType(type)}', " +
                                "add 'given $clsName ${displayDeclaredType(type)}' to its declaration",
                        )
                        null
                    } else {
                        TypedRepresentation.Expr.Variable(signature.dictionaryType(cls, type), dictionaryParameter(predicate))
                    }
                }

                is Type.Data -> {
                    val primitive = signature.primitiveInstances[cls to type.constructor]
                    if (primitive != null) {
                        return TypedRepresentation.Expr.Identifier(signature.dictionaryType(cls, type), primitive)
                    }
                    val instance = signature.instances[cls to type.constructor]
                    if (instance == null) {
                        onError(
                            "Type error in function '${displayFunctionName(name)}': " +
                                "no instance for '$clsName ${displayDataName(type.constructor)}'",
                        )
                        return null
                    }
                    val headArgs = MutableList<Type>(instance.boundTypeVariables) { Type.Data.unit }
                    instance.head.args.zip(type.args).forEach { (variable, arg) ->
                        headArgs[(variable as Type.Variable).index] = arg
                    }
                    val dictionaryType = signature.instanceDictionaryType(instance).type.substitute(headArgs)
                    instance.context.fold(
                        TypedRepresentation.Expr.Identifier(dictionaryType, instance.dictionary) as TypedRepresentation.Expr,
                    ) { dictionary, predicate ->
                        val argument = entail(predicate.cls, headArgs[predicate.variable]) ?: return null
                        TypedRepresentation.Expr.Application((dictionary.type as Type.Arrow).result, dictionary, argument)
                    }
                }

                is Type.Arrow -> {
                    onError(
                        "Type error in function '${displayFunctionName(name)}': " +
                            "no instance for '$clsName' at function type '${displayDeclaredType(type)}'",
                    )
                    null
                }
            }
        }

        val dictionaries =
            holeTypes.mapValues { (_, hole) -> entail(hole.first, hole.second) ?: return null }
        val body = substituteHoles(lambda, dictionaries)

        return abstractDictionaries(context, body) as TypedRepresentation.Expr.Lambda
    }

    private fun abstractDictionaries(
        context: List<DesugaredRepresentation.Predicate>,
        body: TypedRepresentation.Expr,
    ): TypedRepresentation.Expr =
        context.foldRight(body) { predicate, acc ->
            val dictionaryType = signature.dictionaryType(predicate.cls, Type.Variable(predicate.variable))
            TypedRepresentation.Expr.Lambda(
                Type.Arrow(dictionaryType, acc.type),
                listOf(
                    TypedRepresentation.Expr.Lambda.Branch(
                        TypedRepresentation.Pattern.Variable(dictionaryType, dictionaryParameter(predicate)),
                        acc,
                    ),
                ),
            )
        }

    private fun selectMethod(
        method: DesugaredRepresentation.Declarations.Function.Name,
        cls: DesugaredRepresentation.Declarations.Class.Name,
        type: Type,
        dictionary: TypedRepresentation.Expr,
    ): TypedRepresentation.Expr {
        val methods =
            signature.classes
                .getValue(cls)
                .methods.keys
                .toList()
        if (methods.size == 1) {
            return dictionary
        }
        val types = signature.methodTypes(cls, type)
        val index = methods.indexOf(method)
        val name = "method.${selectionCounter++}"

        fun pattern(i: Int): TypedRepresentation.Pattern {
            val component =
                if (i == index) {
                    TypedRepresentation.Pattern.Variable(types[i], name)
                } else {
                    TypedRepresentation.Pattern.Wildcard(types[i])
                }
            if (i == methods.size - 1) {
                return component
            }
            val rest = pattern(i + 1)
            return TypedRepresentation.Pattern.Data(Type.Data.tuple(component.type, rest.type), tupleConstr, listOf(component, rest))
        }

        return TypedRepresentation.Expr.Let(pattern(0), dictionary, TypedRepresentation.Expr.Variable(types[index], name))
    }

    private fun substituteHoles(
        term: TypedRepresentation.Expr,
        dictionaries: Map<String, TypedRepresentation.Expr>,
    ): TypedRepresentation.Expr {
        fun go(term: TypedRepresentation.Expr): TypedRepresentation.Expr =
            when (term) {
                is TypedRepresentation.Expr.Literal -> {
                    term
                }

                is TypedRepresentation.Expr.Identifier -> {
                    term
                }

                is TypedRepresentation.Expr.Variable -> {
                    dictionaries[term.name] ?: term
                }

                is TypedRepresentation.Expr.Application -> {
                    TypedRepresentation.Expr.Application(
                        term.type,
                        go(term.left),
                        go(term.right),
                    )
                }

                is TypedRepresentation.Expr.Lambda -> {
                    TypedRepresentation.Expr.Lambda(
                        term.type,
                        term.branches.map {
                            TypedRepresentation.Expr.Lambda.Branch(it.pattern, go(it.body))
                        },
                    )
                }

                is TypedRepresentation.Expr.If -> {
                    TypedRepresentation.Expr.If(
                        go(term.condition),
                        go(term.positive),
                        go(term.negative),
                    )
                }

                is TypedRepresentation.Expr.Let -> {
                    TypedRepresentation.Expr.Let(
                        term.pattern,
                        go(term.matcher),
                        go(term.body),
                    )
                }
            }
        return go(term)
    }

    private fun newHole(
        cls: DesugaredRepresentation.Declarations.Class.Name,
        type: Type,
    ): TypedRepresentation.Expr.Variable {
        val name = "?dict${holeCounter++}"
        holes[name] = DictionaryHole(cls, type)
        return TypedRepresentation.Expr.Variable(signature.dictionaryType(cls, type), name)
    }

    private fun constrainedVariables(): Set<Int> {
        fun go(type: Type): Set<Int> =
            when (val walked = walk(type)) {
                is Type.Variable -> setOf(walked.index)
                is Type.Arrow -> go(walked.argument) + go(walked.result)
                is Type.Data -> walked.args.flatMap { go(it) }.toSet()
            }
        return holes.values.flatMap { go(it.type) }.toSet()
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
                val instantiated = type.type.shift(shift)
                if (type.context.isEmpty()) {
                    return TypedRepresentation.Expr.Identifier(instantiated, name)
                }
                val dictionaries =
                    type.context.map { predicate -> newHole(predicate.cls, Type.Variable(shift + predicate.variable)) }
                val cls = signature.methods[name]
                if (cls != null) {
                    return selectMethod(name, cls, Type.Variable(shift), dictionaries.single())
                }
                val functionType = dictionaries.foldRight(instantiated) { dictionary, acc -> Type.Arrow(dictionary.type, acc) }
                dictionaries.fold(TypedRepresentation.Expr.Identifier(functionType, name) as TypedRepresentation.Expr) { fn, dictionary ->
                    TypedRepresentation.Expr.Application((fn.type as Type.Arrow).result, fn, dictionary)
                }
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
        val constrained = constrainedVariables()

        fun go(type: Type): Type =
            when (val wtype = walk(type)) {
                is Type.Arrow -> {
                    Type.Arrow(go(wtype.argument), go(wtype.result))
                }

                is Type.Data -> {
                    Type.Data(wtype.constructor, wtype.args.map { go(it) })
                }

                is Type.Variable -> {
                    if (!rangeContains(range, wtype.index) || wtype.index in constrained) {
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
