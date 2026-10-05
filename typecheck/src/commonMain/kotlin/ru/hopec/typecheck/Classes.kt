package ru.hopec.typecheck

import ru.hopec.desugarer.DesugaredRepresentation
import ru.hopec.desugarer.DesugaredRepresentation.Declarations
import ru.hopec.desugarer.DesugaredRepresentation.Type

internal fun DesugaredRepresentation.allDeclarations(): List<Declarations> =
    modules.values.flatMap { listOf(it.public, it.private) } + topLevel

internal fun Signature.withInstances(declarations: List<Declarations>): Signature =
    copy(instances = instances + declarations.flatMap { it.instances }.associateBy { it.cls to it.head.constructor })

internal fun checkClassDeclarations(
    declarations: List<Declarations>,
    signature: Signature,
    onError: (String) -> Unit,
): Boolean {
    var ok = true

    fun error(message: String) {
        onError(message)
        ok = false
    }

    fun checkContext(
        where: String,
        context: List<DesugaredRepresentation.Predicate>,
        boundTypeVariables: Int,
    ) {
        context.forEach { predicate ->
            if (predicate.cls !in signature.classes) {
                error("Unknown class '${predicate.cls.name}' in $where")
            }
            if (predicate.variable !in 0 until boundTypeVariables) {
                error("Constraint '${predicate.cls.name}' in $where refers to an unknown type variable")
            }
        }
    }

    declarations.flatMap { it.classes.entries }.forEach { (name, cls) ->
        if (cls.methods.isEmpty()) {
            error("Class '${name.name}' must declare at least one method")
        }
        cls.methods.forEach { (method, type) ->
            if (type !is Type.Arrow) {
                error("Method '${method.display()}' of class '${name.name}' must be a function")
            }
            if (type.freeVariables() != setOf(0)) {
                error("Type of method '${method.display()}' must mention the class type variable and no other type variables")
            }
        }
    }

    val seen = mutableSetOf<Pair<Declarations.Class.Name, Declarations.Data.Name>>()
    declarations.flatMap { it.instances }.forEach { instance ->
        val where = "instance '${instance.cls.name}' for '${instance.head.constructor.display()}'"
        val cls = signature.classes[instance.cls]
        if (cls == null) {
            error("Unknown class '${instance.cls.name}' in $where")
            return@forEach
        }
        if (!seen.add(instance.cls to instance.head.constructor) ||
            (instance.cls to instance.head.constructor) in signature.primitiveInstances
        ) {
            error("Overlapping instances: $where is declared more than once")
        }
        val variables = instance.head.args.map { (it as? Type.Variable)?.index }
        if (variables.any { it == null } ||
            variables.toSet().size != variables.size ||
            variables.toSet() != (0 until instance.boundTypeVariables).toSet()
        ) {
            error("Head of $where must be a type constructor applied to distinct type variables")
        }
        checkContext(where, instance.context, instance.boundTypeVariables)
        if (instance.methods.keys != cls.methods.keys) {
            error("$where must define exactly the methods {${cls.methods.keys.joinToString(", ") { it.display() }}}")
        }
    }

    declarations.flatMap { it.functions.entries }.forEach { (name, function) ->
        checkContext("declaration of '$name'", function.type.context, function.type.boundTypeVariables)
    }

    return ok
}

internal fun Declarations.Function.Name.display(): String =
    when (this) {
        is Declarations.Function.Name.Core -> name
        is Declarations.Function.Name.User -> if (module != null) "$module.$name" else name
        is Declarations.Function.Name.Constructor -> constructor
    }

internal fun Declarations.Data.Name.display(): String =
    when (this) {
        is Declarations.Data.Name.Core.Char -> "char"
        is Declarations.Data.Name.Core.TruVal -> "truval"
        is Declarations.Data.Name.Core.Num -> "num"
        is Declarations.Data.Name.Core.List -> "list"
        is Declarations.Data.Name.Core.Set -> "set"
        is Declarations.Data.Name.Core.Tuple -> "#"
        is Declarations.Data.Name.Core.Unit -> "unit"
        is Declarations.Data.Name.Defined -> if (module != null) "$module.$name" else name
    }
