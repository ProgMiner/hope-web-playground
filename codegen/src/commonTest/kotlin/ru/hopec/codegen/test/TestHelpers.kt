package ru.hopec.codegen.test

import ru.hopec.codegen.WatGenerator
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

val numType = Type.Data.num
val truvalType = Type.Data.truval
val charType = Type.Data.char

val userName = FunName.User(null, "f")
val trueCtor = FunName.Constructor(Type.Data.truval.constructor, "true")
val falseCtor = FunName.Constructor(Type.Data.truval.constructor, "false")
val nilCtor = FunName.Constructor(Type.Data.list(numType).constructor, "nil")
val consCtor = FunName.Constructor(Type.Data.list(numType).constructor, "cons")
val tupleCtor = FunName.Constructor(Type.Data.tuple(numType, numType).constructor, "#")
val plusCore = FunName.Core("+")

fun emptyProgram() = TypedRepresentation(emptyMap(), Declarations(emptyMap(), emptyMap()))

fun singleFuncProgram(
    name: FunName = userName,
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

fun region(
    wat: String,
    head: String,
): String {
    val marker = wat.indexOf(head)
    require(marker >= 0) { "Не найден участок, начинающийся с: $head\nWAT:\n$wat" }
    val lineStart = wat.lastIndexOf('\n', marker) + 1

    var depth = 0
    var end = -1
    var i = lineStart
    while (i < wat.length) {
        when (wat[i]) {
            '(' -> {
                depth++
            }

            ')' -> {
                depth--
                if (depth == 0) {
                    end = i + 1
                    break
                }
            }
        }
        i++
    }
    require(end >= 0) { "Несбалансированные скобки в участке: $head" }
    return wat.substring(lineStart, end).trimIndent()
}

fun normalize(s: String): String =
    s
        .trim()
        .lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
