package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import kotlin.test.Test
import kotlin.test.assertEquals

class StringLiteralTest {
    @Test
    fun `string literal builds reversed cons list of char codes`() {
        val body = Expr.Literal.String("hi")
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.string, body)))

        val expected =
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (local ${'$'}t_0 i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (block (result i32)
                      (local.set ${'$'}t_0
                        (i32.const 0))
                      (local.set ${'$'}t_0
                        (call ${'$'}rt.mk_cons
                          (call ${'$'}rt.mk_tuple
                            (i32.const 105)
                            (local.get ${'$'}t_0))))
                      (local.set ${'$'}t_0
                        (call ${'$'}rt.mk_cons
                          (call ${'$'}rt.mk_tuple
                            (i32.const 104)
                            (local.get ${'$'}t_0))))
                      (local.get ${'$'}t_0))))
                (unreachable)))
            """.trimIndent()

        assertEquals(normalize(expected), normalize(region(w, "(func \$fn.top.f")))
    }
}
