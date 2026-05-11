package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import kotlin.test.Test
import kotlin.test.assertContains

class ExportsTest {
    @Test
    fun `top-level user function is exported by name`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))
        assertContains(w, "(export \"f\" (func \$fn.top.f))")
        assertContains(w, "func \$fn.top.f (param \$arg i32) (result i32)")
    }

    @Test
    fun `main function is exported`() {
        val w =
            wat(
                singleFuncProgram(
                    name = Function.Name.User(null, "main"),
                    lambda = wildLambda(numType, numType, Expr.Literal.Num(0)),
                ),
            )
        assertContains(w, "(export \"main\" (func \$fn.top.main))")
        assertContains(w, "func \$fn.top.main (param \$arg i32) (result i32)")
    }
}
