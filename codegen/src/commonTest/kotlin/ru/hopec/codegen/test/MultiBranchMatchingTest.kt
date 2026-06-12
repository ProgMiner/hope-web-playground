package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

class MultiBranchMatchingTest {
    private val trueCtor = FunName.Constructor(Data.Name.Core.TruVal, "true")
    private val falseCtor = FunName.Constructor(Data.Name.Core.TruVal, "false")

    @Test
    fun `two boolean branches generate two skip blocks with checks`() {
        val lambda =
            Expr.Lambda(
                Type.Arrow(truvalType, numType),
                listOf(
                    Expr.Lambda.Branch(
                        Pattern.Data(truvalType, trueCtor, emptyList()),
                        Expr.Literal.Num(1),
                    ),
                    Expr.Lambda.Branch(
                        Pattern.Data(truvalType, falseCtor, emptyList()),
                        Expr.Literal.Num(0),
                    ),
                ),
            )
        val w = wat(singleFuncProgram(lambda = lambda))

        val expected =
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
                  (br_if ${'$'}skip2
                    (i32.ne
                      (local.get ${'$'}arg)
                      (i32.const 0)))
                  (br ${'$'}match_end0
                    (i32.const 0)))
                (unreachable)))
            """.trimIndent()

        assertEquals(normalize(expected), normalize(region(w, "(func \$fn.top.f")))
    }
}
