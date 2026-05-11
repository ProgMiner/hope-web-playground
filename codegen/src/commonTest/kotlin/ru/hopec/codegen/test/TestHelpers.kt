package ru.hopec.codegen.test

import ru.hopec.codegen.WatGenerator
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Type

// ─── Test helpers ──────────────────────────────────────────────────────────────

val numType = Type.Data.num
val truvalType = Type.Data.truval
val charType = Type.Data.char

val userName = Function.Name.User(null, "f")
val trueCtor = Function.Name.Constructor(Type.Data.truval.name, "true")
val falseCtor = Function.Name.Constructor(Type.Data.truval.name, "false")
val nilCtor = Function.Name.Constructor(Type.Data.list(numType).name, "nil")
val consCtor = Function.Name.Constructor(Type.Data.list(numType).name, "cons")
val tupleCtor = Function.Name.Constructor(Type.Data.tuple(numType, numType).name, "#")
val plusCore = Function.Name.Core("+")

fun emptyProgram() = TypedRepresentation(emptyMap(), Declarations(emptyMap(), emptyMap()))

fun singleFuncProgram(
    name: Function.Name = userName,
    lambda: Expr.Lambda,
) = TypedRepresentation(
    emptyMap(),
    Declarations(emptyMap(), mapOf(name to Function(lambda, 0))),
)

fun wildLambda(
    argType: Type,
    resultType: Type,
    body: Expr,
) = Expr.Lambda(
    Type.Arrow(argType, resultType),
    listOf(Expr.Lambda.Branch(Pattern.Wildcard(argType), body)),
)

fun varLambda(
    varName: String,
    argType: Type,
    resultType: Type,
    body: Expr,
) = Expr.Lambda(
    Type.Arrow(argType, resultType),
    listOf(Expr.Lambda.Branch(Pattern.Variable(argType, varName), body)),
)

fun wat(program: TypedRepresentation) = WatGenerator(program).generate()
