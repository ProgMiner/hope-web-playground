package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class UserDefinedAdtTest {
    @Test
    fun `user ADT constructor assigns tags 0 and 1`() {
        val dataName = Data.Name.Defined(null, "Color")
        val redCtor = Function.Name.Constructor(dataName, "Red")
        val blueCtor = Function.Name.Constructor(dataName, "Blue")
        val colorData = Data(mapOf("Red" to emptyList(), "Blue" to emptyList()), 0)
        val colorType = Type.Data(dataName, emptyList())

        val body = Expr.Identifier(colorType, redCtor)
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    mapOf(dataName to colorData),
                    mapOf(userName to Function(wildLambda(numType, colorType, body), 0)),
                ),
            )
        val w = wat(program)
        assertTrue(w.contains("(module"))
        assertContains(w, "call \$rt.mk_closure")
        assertContains(w, "i32.const 0")
        assertContains(w, "\$ctor.top.Color.Red")
    }
}
