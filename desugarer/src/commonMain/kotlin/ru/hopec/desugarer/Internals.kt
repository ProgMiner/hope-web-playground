package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Constructor
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core

val trueConstr =  Constructor(Core.TruVal, "true")
val falseConstr = Constructor(Core.TruVal, "false")
val nilConstr =  Constructor(
    Core.List,
    "nil"
    )
val consConstr = Constructor(Core.List, "cons")
val setConstr = Constructor(Core.Tuple, "setCons")
val emptySetConstr = Constructor(
    Core.Set,
    "emptySet"
    )
val tupleConstr = Constructor(Core.Tuple, "#")

val internalConstructors = setOf(
    trueConstr,
    falseConstr,
    nilConstr,
    consConstr,
    emptySetConstr,
    setConstr,
    tupleConstr,
)

val internalData = mapOf(
    "list" to Core.List,
    "set" to Core.Set,
    "num" to Core.Num,
    "truval" to Core.TruVal,
    "chat" to Core.Char,
    "tuple" to Core.Tuple,
)