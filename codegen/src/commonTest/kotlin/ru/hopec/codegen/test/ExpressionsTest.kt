package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionsTest {
    @Test
    fun `if expression generates folded s-expression function`() {
        val ifExpr =
            Expr.If(
                Expr.Literal.TruVal(true),
                Expr.Literal.Num(1),
                Expr.Literal.Num(0),
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, ifExpr)))

        val expected =
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (if (result i32)
                      (i32.const 1)
                      (then
                        (i32.const 1))
                      (else
                        (i32.const 0)))))
                (unreachable)))
            """.trimIndent()

        assertEquals(normalize(expected), normalize(region(w, "(func \$fn.top.f")))
    }

    @Test
    fun `let binding generates folded local set then body`() {
        val letExpr =
            Expr.Let(
                Pattern.Variable(numType, "y"),
                Expr.Literal.Num(10),
                Expr.Variable(numType, "y"),
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, letExpr)))

        val expected =
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (local ${'$'}v_y_0 i32)
              (local ${'$'}t_0 i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (block (result i32)
                      (local.set ${'$'}t_0
                        (i32.const 10))
                      (block ${'$'}let_fail2
                        (local.set ${'$'}v_y_0
                          (local.get ${'$'}t_0)))
                      (local.get ${'$'}v_y_0))))
                (unreachable)))
            """.trimIndent()

        assertEquals(normalize(expected), normalize(region(w, "(func \$fn.top.f")))
    }
}
