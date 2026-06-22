package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

class ZeroArgCallTest {
    @Test
    fun `nullary user function referenced by name emits direct call`() {
        val zeroArg = FunName.User(null, "zero_arg")
        val main = FunName.User(null, "main")
        val zeroArgFn = Function(wildLambda(numType, numType, Expr.Literal.Num(42)), 0)
        val mainFn =
            Function(
                wildLambda(
                    numType,
                    numType,
                    Expr.Identifier(numType, zeroArg),
                ),
                0,
            )
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    emptyMap(),
                    mapOf(
                        zeroArg to zeroArgFn,
                        main to mainFn,
                    ),
                ),
            )
        val w = wat(program)
        val mainBody = region(w, "(func \$fn.top.main")

        assertContains(mainBody, "(call \$fn.top.zero_arg")
        assertFalse(mainBody.contains("\$rt.mk_closure"))
    }
}
