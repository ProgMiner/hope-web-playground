package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains

class LiteralsTest {
    @Test
    fun `Num literal emits i32 const`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(42))))
        assertContains(w, "i32.const 42")
    }

    @Test
    fun `TruVal true literal emits i32 const 1`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, truvalType, Expr.Literal.TruVal(true))))
        assertContains(w, "i32.const 1")
    }

    @Test
    fun `TruVal false literal emits i32 const 0`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, truvalType, Expr.Literal.TruVal(false))))
        assertContains(w, "i32.const 0")
    }

    @Test
    fun `Char literal emits its code point`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, charType, Expr.Literal.Char('A'))))
        assertContains(w, "i32.const 65") // 'A' = 65
    }

    @Test
    fun `negative Num literal emits correct value`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(-1))))
        assertContains(w, "i32.const -1")
    }
}
