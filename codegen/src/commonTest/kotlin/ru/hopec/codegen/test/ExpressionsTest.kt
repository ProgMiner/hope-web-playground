package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import kotlin.test.Test
import kotlin.test.assertContains

class ExpressionsTest {
    @Test
    fun `if expression emits s-expression form`() {
        val ifExpr =
            Expr.If(
                Expr.Literal.TruVal(true),
                Expr.Literal.Num(1),
                Expr.Literal.Num(0),
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, ifExpr)))
        assertContains(w, "(if (result i32)")
        assertContains(w, "(then")
        assertContains(w, "(else")
        assertContains(w, "i32.const 1")
        assertContains(w, "i32.const 0")
        assertContains(w, "i32.const 1\n")
    }

    @Test
    fun `let binding emits local set then body`() {
        val letExpr =
            Expr.Let(
                Pattern.Variable(numType, "y"),
                Expr.Literal.Num(10),
                Expr.Variable(numType, "y"),
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, letExpr)))
        assertContains(w, "i32.const 10")
        assertContains(w, "local.set")
        assertContains(w, "local.get")
        assertContains(w, "v_y")
        assertContains(w, "\$let_fail")
    }
}
