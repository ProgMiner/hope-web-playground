package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import kotlin.test.Test
import kotlin.test.assertEquals

class ConstructorsTest {
    private val nilCtor = Function.Name.Constructor(Type.Data.list(numType).constructor, "nil")
    private val consCtor = Function.Name.Constructor(Type.Data.list(numType).constructor, "cons")
    private val tupleCtor = Function.Name.Constructor(Type.Data.tuple(numType, numType).constructor, "#")

    private fun assertFunction(
        w: String,
        expected: String,
    ) = assertEquals(normalize(expected), normalize(region(w, "(func \$fn.top.f")))

    @Test
    fun `nil identifier folds to i32 const 0`() {
        val body = Expr.Identifier(Type.Data.list(numType), nilCtor)
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.list(numType), body)))

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (i32.const 0)))
                (unreachable)))
            """.trimIndent(),
        )
    }

    @Test
    fun `tuple constructor call folds into mk_tuple with both operands`() {
        val tupleExpr =
            Expr.Application(
                Type.Data.tuple(numType, numType),
                Expr.Application(
                    Type.Arrow(numType, Type.Data.tuple(numType, numType)),
                    Expr.Identifier(Type.Arrow(numType, Type.Arrow(numType, Type.Data.tuple(numType, numType))), tupleCtor),
                    Expr.Literal.Num(1),
                ),
                Expr.Literal.Num(2),
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.tuple(numType, numType), tupleExpr)))

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (call ${'$'}rt.mk_tuple
                      (i32.const 1)
                      (i32.const 2))))
                (unreachable)))
            """.trimIndent(),
        )
    }

    @Test
    fun `cons constructor call folds into mk_cons of mk_tuple`() {
        val tupleArg =
            Expr.Application(
                Type.Data.tuple(numType, Type.Data.list(numType)),
                Expr.Application(
                    Type.Arrow(numType, Type.Data.tuple(numType, Type.Data.list(numType))),
                    Expr.Identifier(
                        Type.Arrow(numType, Type.Arrow(Type.Data.list(numType), Type.Data.tuple(numType, Type.Data.list(numType)))),
                        tupleCtor,
                    ),
                    Expr.Literal.Num(1),
                ),
                Expr.Identifier(Type.Data.list(numType), nilCtor),
            )
        val consExpr =
            Expr.Application(
                Type.Data.list(numType),
                Expr.Identifier(Type.Arrow(Type.Data.tuple(numType, Type.Data.list(numType)), Type.Data.list(numType)), consCtor),
                tupleArg,
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.list(numType), consExpr)))

        assertFunction(
            w,
            """
            (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
              (block ${'$'}match_end0 (result i32)
                (block ${'$'}skip1
                  (br ${'$'}match_end0
                    (call ${'$'}rt.mk_cons
                      (call ${'$'}rt.mk_tuple
                        (i32.const 1)
                        (i32.const 0)))))
                (unreachable)))
            """.trimIndent(),
        )
    }
}
