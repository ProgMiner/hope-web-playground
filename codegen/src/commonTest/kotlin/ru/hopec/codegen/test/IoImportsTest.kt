package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class IoImportsTest {
    private val printCore = FunName.Core("io.print")

    @Test
    fun `empty program does not declare io imports`() {
        val w = wat(emptyProgram())
        assertFalse(w.contains("(import \"env\" \"print\""))
        assertFalse(w.contains("(import \"env\" \"getChar\""))
    }

    @Test
    fun `wat module declares env import when print is used`() {
        val hello = Expr.Literal.String("Hi")
        val body =
            Expr.Application(
                Type.Data.unit,
                Expr.Identifier(Type.Arrow(Type.Data.string, Type.Data.unit), printCore),
                hello,
            )
        val program =
            singleFuncProgram(
                lambda = wildLambda(numType, numType, body),
            )
        val w = wat(program)
        assertContains(w, "(import \"env\" \"print\" (func \$import.io.print (param i32)))")
        assertContains(w, "call \$import.io.print")
    }
}
