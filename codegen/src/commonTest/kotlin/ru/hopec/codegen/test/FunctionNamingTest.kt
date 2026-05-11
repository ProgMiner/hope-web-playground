package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains

class FunctionNamingTest {
    @Test
    fun `user function gets scoped WAT name`() {
        val w =
            wat(
                singleFuncProgram(
                    name = Function.Name.User("MyModule", "myFunc"),
                    lambda = wildLambda(numType, numType, Expr.Literal.Num(0)),
                ),
            )
        assertContains(w, "\$fn.MyModule.myFunc")
        assertContains(w, "func \$fn.MyModule.myFunc (param \$arg i32) (result i32)")
    }

    @Test
    fun `top-level function gets top scope name`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))
        assertContains(w, "\$fn.top.f")
        assertContains(w, "func \$fn.top.f (param \$arg i32) (result i32)")
    }

    @Test
    fun `operator name is escaped`() {
        val w =
            wat(
                singleFuncProgram(
                    name = Function.Name.User(null, "add+"),
                    lambda = wildLambda(numType, numType, Expr.Literal.Num(0)),
                ),
            )
        assertContains(w, "_plus")
        assertContains(w, "\$fn.top.add_plus")
        assertContains(w, "func \$fn.top.add_plus (param \$arg i32) (result i32)")
    }
}
