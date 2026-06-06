package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

class PatternsTest {
    private val trueCtor = FunName.Constructor(Data.Name.Core.TruVal, "true")
    private val falseCtor = FunName.Constructor(Data.Name.Core.TruVal, "false")
    private val nilCtor = FunName.Constructor(Data.Name.Core.List, "nil")
    private val consCtor = FunName.Constructor(Data.Name.Core.List, "cons")
    private val tupleCtor = FunName.Constructor(Data.Name.Core.Tuple, "#")

    private fun assertFunction(
        w: String,
        expected: String,
    ) = assertEquals(normalize(expected), normalize(region(w, "(func \$fn.top.f")))

    @Test
    fun `wildcard pattern matches unconditionally`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (i32.const 0)))
                (unreachable)))
            """.trimIndent(),
        )
    }

    @Test
    fun `variable pattern binds arg to local`() {
        val lambda = varLambda("x", numType, numType, Expr.Variable(numType, "x"))
        val w = wat(singleFuncProgram(lambda = lambda))

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (local ${'$'}v_x_0 i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (local.set ${'$'}v_x_0
                    (local.get ${'$'}arg))
                  (br ${'$'}match_end0
                    (local.get ${'$'}v_x_0)))
                (unreachable)))
            """.trimIndent(),
        )
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

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br_if ${'$'}skip1
                    (i32.ne
                      (local.get ${'$'}arg)
                      (i32.const 1)))
                  (br ${'$'}match_end0
                    (i32.const 1)))
                (block ${'$'}skip2
                  (br ${'$'}match_end0
                    (i32.const 0)))
                (unreachable)))
            """.trimIndent(),
        )
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

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br_if ${'$'}skip1
                    (local.get ${'$'}arg))
                  (br ${'$'}match_end0
                    (i32.const 1)))
                (block ${'$'}skip2
                  (br ${'$'}match_end0
                    (i32.const 0)))
                (unreachable)))
            """.trimIndent(),
        )
    }

    @Test
    fun `cons pattern checks non-zero then destructures tuple`() {
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

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (local ${'$'}t_0 i32)
              (local ${'$'}t_1 i32)
              (local ${'$'}t_2 i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br_if ${'$'}skip1
                    (i32.eqz
                      (local.get ${'$'}arg)))
                  (local.set ${'$'}t_0
                    (i32.load offset=0
                      (local.get ${'$'}arg)))
                  (local.set ${'$'}t_1
                    (i32.load offset=0
                      (local.get ${'$'}t_0)))
                  (local.set ${'$'}t_2
                    (i32.load offset=4
                      (local.get ${'$'}t_0)))
                  (br ${'$'}match_end0
                    (i32.const 0)))
                (block ${'$'}skip2
                  (br ${'$'}match_end0
                    (i32.const 1)))
                (unreachable)))
            """.trimIndent(),
        )
    }

    @Test
    fun `tuple pattern loads both fields into bound variables`() {
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

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (local ${'$'}v_a_0 i32)
              (local ${'$'}v_b_1 i32)
              (local ${'$'}t_0 i32)
              (local ${'$'}t_1 i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (local.set ${'$'}t_0
                    (i32.load offset=0
                      (local.get ${'$'}arg)))
                  (local.set ${'$'}v_a_0
                    (local.get ${'$'}t_0))
                  (local.set ${'$'}t_1
                    (i32.load offset=4
                      (local.get ${'$'}arg)))
                  (local.set ${'$'}v_b_1
                    (local.get ${'$'}t_1))
                  (br ${'$'}match_end0
                    (local.get ${'$'}v_a_0)))
                (unreachable)))
            """.trimIndent(),
        )
    }
}
