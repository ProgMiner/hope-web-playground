package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

class UserDefinedAdtTest {
    @Test
    fun `nullary ADT constructor reference builds tagged value`() {
        val dataName = Data.Name.Defined(null, "Color")
        val redCtor = FunName.Constructor(dataName, "Red")
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

        assertEquals(
            normalize(
                """
                (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
                  (block ${'$'}match_end0 (result i32)
                    (block ${'$'}skip1
                      (br ${'$'}match_end0
                        (call ${'$'}rt.mk_adt
                          (i32.const 0)
                          (i32.const 0))))
                    (unreachable)))
                """.trimIndent(),
            ),
            normalize(region(w, "(func \$fn.top.f")),
        )

        assertEquals("(table (export \"table\") 0 funcref)", region(w, "(table"))
    }

    @Test
    fun `second nullary constructor gets distinct tag`() {
        val dataName = Data.Name.Defined(null, "Color")
        val blueCtor = FunName.Constructor(dataName, "Blue")
        val colorData = Data(mapOf("Red" to emptyList(), "Blue" to emptyList()), 0)
        val colorType = Type.Data(dataName, emptyList())

        val body = Expr.Identifier(colorType, blueCtor)
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    mapOf(dataName to colorData),
                    mapOf(userName to Function(wildLambda(numType, colorType, body), 0)),
                ),
            )
        val w = wat(program)

        assertEquals(
            normalize(
                """
                (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
                  (block ${'$'}match_end0 (result i32)
                    (block ${'$'}skip1
                      (br ${'$'}match_end0
                        (call ${'$'}rt.mk_adt
                          (i32.const 0)
                          (i32.const 1))))
                    (unreachable)))
                """.trimIndent(),
            ),
            normalize(region(w, "(func \$fn.top.f")),
        )
    }
}
