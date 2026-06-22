package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name.Core
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Constructor
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Core as CoreFunction

val plus = CoreFunction("+")
val minus = CoreFunction("-")
val times = CoreFunction("*")
val divide = CoreFunction("div")
val modulo = CoreFunction("mod")
val less = CoreFunction("<")
val lessEq = CoreFunction("<=")
val greater = CoreFunction(">")
val greaterEq = CoreFunction(">=")
val equal = CoreFunction("=")
val trueConstr = Constructor(Core.TruVal, "true")
val falseConstr = Constructor(Core.TruVal, "false")
val nilConstr =
    Constructor(
        Core.List,
        "nil",
    )
val consConstr = Constructor(Core.List, "cons")
val setConstr = Constructor(Core.Set, "setCons")
val emptySetConstr =
    Constructor(
        Core.Set,
        "emptySet",
    )
val tupleConstr = Constructor(Core.Tuple, "#")

val internalFunctions =
    setOf(
        plus,
        minus,
        times,
        divide,
        modulo,
        less,
        lessEq,
        greater,
        greaterEq,
        equal,
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
        "char" to Core.Char as Data.Name,
        "tuple" to Core.Tuple as Data.Name,
        "unit" to Core.Unit as Data.Name,
    ).toMutableMap()

fun getInternalConstructors() =
    internalConstructors
        .associate {
            it.constructor to mutableSetOf(it)
        }.toMutableMap()
        .also {
            it["::"] = mutableSetOf(consConstr)
        }

fun getInternalFunctions() =
    internalFunctions
        .associate {
            it.name to mutableSetOf(it as DesugaredRepresentation.Declarations.Function.Name)
        }.toMutableMap()
