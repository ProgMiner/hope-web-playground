package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class PatternsTest {
    private val trueCtor = Function.Name.Constructor(Data.Name.Core.TruVal, "true")
    private val falseCtor = Function.Name.Constructor(Data.Name.Core.TruVal, "false")
    private val nilCtor = Function.Name.Constructor(Data.Name.Core.List, "nil")
    private val consCtor = Function.Name.Constructor(Data.Name.Core.List, "cons")
    private val tupleCtor = Function.Name.Constructor(Data.Name.Core.Tuple, "#")

    @Test
    fun `wildcard pattern emits no br_if`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))
        assertContains(w, "unreachable")
        assertFalse(w.contains("br_if \$skip"))
    }

    @Test
    fun `variable pattern emits local set and get`() {
        val lambda = varLambda("x", numType, numType, Expr.Variable(numType, "x"))
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "local.set")
        assertContains(w, "local.get")
        assertContains(w, "v_x")
    }

    @Test
    fun `TruVal true pattern checks against 1`() {
        val truePat = Pattern.Data(truvalType, trueCtor, emptyList())
        val lambda =
            Expr.Lambda(
                Type.Arrow(truvalType, numType),
                listOf(
                    Expr.Lambda.Branch(truePat, Expr.Literal.Num(1)),
                    Expr.Lambda.Branch(Pattern.Wildcard(truvalType), Expr.Literal.Num(0)),
                ),
            )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "i32.const 1")
        assertContains(w, "i32.ne")
        assertContains(w, "br_if")
        assertContains(w, "\$skip")
        assertContains(w, "\$match_end")
    }

    @Test
    fun `nil pattern checks pointer is zero`() {
        val nilPat = Pattern.Data(Type.Data.list(numType), nilCtor, emptyList())
        val lambda =
            Expr.Lambda(
                Type.Arrow(Type.Data.list(numType), truvalType),
                listOf(
                    Expr.Lambda.Branch(nilPat, Expr.Literal.TruVal(true)),
                    Expr.Lambda.Branch(Pattern.Wildcard(Type.Data.list(numType)), Expr.Literal.TruVal(false)),
                ),
            )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "br_if")
        assertContains(w, "br_if")
        assertContains(w, "call \$rt.malloc")
    }

    @Test
    fun `cons pattern checks pointer is non-zero`() {
        val tuplePat =
            Pattern.Data(
                Type.Data.tuple(numType, Type.Data.list(numType)),
                tupleCtor,
                listOf(Pattern.Wildcard(numType), Pattern.Wildcard(Type.Data.list(numType))),
            )
        val consPat = Pattern.Data(Type.Data.list(numType), consCtor, listOf(tuplePat))
        val lambda =
            Expr.Lambda(
                Type.Arrow(Type.Data.list(numType), truvalType),
                listOf(
                    Expr.Lambda.Branch(consPat, Expr.Literal.TruVal(false)),
                    Expr.Lambda.Branch(Pattern.Wildcard(Type.Data.list(numType)), Expr.Literal.TruVal(true)),
                ),
            )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "i32.eqz")
        assertContains(w, "br_if")
        assertContains(w, "i32.load")
        assertContains(w, "i32.load offset=0")
        assertContains(w, "\$skip")
        assertContains(w, "\$match_end")
    }

    @Test
    fun `tuple pattern loads both fields`() {
        val tuplePat =
            Pattern.Data(
                Type.Data.tuple(numType, numType),
                tupleCtor,
                listOf(Pattern.Variable(numType, "a"), Pattern.Variable(numType, "b")),
            )
        val body = Expr.Variable(numType, "a")
        val lambda =
            Expr.Lambda(
                Type.Arrow(Type.Data.tuple(numType, numType), numType),
                listOf(Expr.Lambda.Branch(tuplePat, body)),
            )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "i32.load offset=0")
        assertContains(w, "i32.load offset=4")
        assertContains(w, "local.set")
        assertContains(w, "local.get")
        assertContains(w, "v_a")
        assertContains(w, "v_b")
    }
}
