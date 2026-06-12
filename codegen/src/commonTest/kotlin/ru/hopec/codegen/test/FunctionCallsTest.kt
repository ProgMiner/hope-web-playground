package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

class FunctionCallsTest {
    private val plusCore = FunName.Core("+")
    private val tupleCtor = FunName.Constructor(Type.Data.tuple(Type.Data.num, Type.Data.num).constructor, "#")

    @Test
    fun `user function emits direct call`() {
        val callee = FunName.User(null, "helper")
        val callExpr =
            Expr.Application(
                numType,
                Expr.Identifier(Type.Arrow(numType, numType), callee),
                Expr.Literal.Num(5),
            )
        val helper = Function(wildLambda(numType, numType, Expr.Literal.Num(0)), 0)
        val caller = Function(wildLambda(numType, numType, callExpr), 0)
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    emptyMap(),
                    mapOf(
                        FunName.User(null, "main") to caller,
                        FunName.User(null, "helper") to helper,
                    ),
                ),
            )
        val w = wat(program)

        assertEquals(
            normalize(
                """
                (func ${'$'}fn.top.main (param ${'$'}arg i32) (result i32)
                  (block ${'$'}match_end0 (result i32)
                    (block ${'$'}skip1
                      (br ${'$'}match_end0
                        (call ${'$'}fn.top.helper
                          (i32.const 5))))
                    (unreachable)))
                """.trimIndent(),
            ),
            normalize(region(w, "(func \$fn.top.main")),
        )

        assertEquals(
            normalize(
                """
                (func ${'$'}fn.top.helper (param ${'$'}arg i32) (result i32)
                  (block ${'$'}match_end2 (result i32)
                    (block ${'$'}skip3
                      (br ${'$'}match_end2
                        (i32.const 0)))
                    (unreachable)))
                """.trimIndent(),
            ),
            normalize(region(w, "(func \$fn.top.helper")),
        )
    }

    @Test
    fun `plus application folds into i32 add of tuple fields`() {
        val tupleExpr =
            Expr.Application(
                Type.Data.tuple(numType, numType),
                Expr.Application(
                    Type.Arrow(numType, Type.Data.tuple(numType, numType)),
                    Expr.Identifier(Type.Arrow(numType, Type.Arrow(numType, Type.Data.tuple(numType, numType))), tupleCtor),
                    Expr.Literal.Num(3),
                ),
                Expr.Literal.Num(4),
            )
        val addExpr =
            Expr.Application(
                numType,
                Expr.Identifier(Type.Arrow(Type.Data.tuple(numType, numType), numType), plusCore),
                tupleExpr,
            )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, addExpr)))

        assertEquals(
            normalize(
                """
                (func ${'$'}fn.top.f (param ${'$'}arg i32) (result i32)
                  (local ${'$'}t_0 i32)
                  (block ${'$'}match_end0 (result i32)
                    (block ${'$'}skip1
                      (br ${'$'}match_end0
                        (i32.add
                          (i32.load offset=0
                            (local.tee ${'$'}t_0
                              (call ${'$'}rt.mk_tuple
                                (i32.const 3)
                                (i32.const 4))))
                          (i32.load offset=4
                            (local.get ${'$'}t_0)))))
                    (unreachable)))
                """.trimIndent(),
            ),
            normalize(region(w, "(func \$fn.top.f")),
        )
    }
}
