package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains

class MultiBranchMatchingTest {
    private val trueCtor = Function.Name.Constructor(Data.Name.Core.TruVal, "true")
    private val falseCtor = Function.Name.Constructor(Data.Name.Core.TruVal, "false")

    @Test
    fun `multiple branches emit multiple skip blocks`() {
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
        val skipCount = w.split("\$skip").size - 1
        assertTrue(skipCount >= 2, "Expected at least 2 skip labels, got $skipCount")
        assertContains(w, "i32.const 1")
        assertContains(w, "i32.const 0")
        assertContains(w, "\$match_end")
        assertContains(w, "br \$match_end")
    }
}
