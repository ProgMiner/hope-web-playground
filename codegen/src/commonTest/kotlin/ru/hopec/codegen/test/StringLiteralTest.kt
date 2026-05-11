package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class StringLiteralTest {
    @Test
    fun `string literal emits mk_cons and mk_tuple for each char`() {
        val body = Expr.Literal.String("hi")
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.string, body)))
        val tupleCount = w.split("call \$rt.mk_tuple").size - 1
        val consCount = w.split("call \$rt.mk_cons").size - 1
        assertTrue(tupleCount >= 2, "Expected at least 2 mk_tuple calls, got $tupleCount")
        assertTrue(consCount >= 2, "Expected at least 2 mk_cons calls, got $consCount")
        assertContains(w, "i32.const 104") // 'h'
        assertContains(w, "i32.const 105") // 'i'
        assertContains(w, "i32.const 0")
    }
}
