package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Constructor
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Core as CoreFunction

val plus = CoreFunction("plus")
val trueConstr = Constructor(Core.TruVal, "true")
val falseConstr = Constructor(Core.TruVal, "false")
val nilConstr =
    Constructor(
        Core.List,
        "nil",
    )
val consConstr = Constructor(Core.List, "cons")
val setConstr = Constructor(Core.Tuple, "setCons")
val emptySetConstr =
    Constructor(
        Core.Set,
        "emptySet",
    )
val tupleConstr = Constructor(Core.Tuple, "#")

val internalFunctions =
    setOf(
        plus,
    )

val internalConstructors =
    setOf(
        trueConstr,
        falseConstr,
        nilConstr,
        consConstr,
        emptySetConstr,
        setConstr,
        tupleConstr,
    )

val internalData =
    mapOf(
        "list" to Core.List as Data.Name,
        "set" to Core.Set as Data.Name,
        "num" to Core.Num as Data.Name,
        "truval" to Core.TruVal as Data.Name,
        "chat" to Core.Char as Data.Name,
        "tuple" to Core.Tuple as Data.Name,
    ).toMutableMap()

fun getInternalConstructors() =
    internalConstructors
        .associate {
            it.constructor to mutableSetOf(it)
        }.toMutableMap()

fun getInternalFunctions() =
    internalFunctions
        .associate {
            it.name to mutableSetOf(it as DesugaredRepresentation.Declarations.Function.Name)
        }.toMutableMap()
