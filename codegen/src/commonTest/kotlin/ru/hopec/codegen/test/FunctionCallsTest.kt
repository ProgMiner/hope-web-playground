package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains

class FunctionCallsTest {
    private val plusCore = Function.Name.Core("+")
    private val tupleCtor = Function.Name.Constructor(Type.Data.tuple(Type.Data.num, Type.Data.num).constructor, "#")

    @Test
    fun `user function emits direct call`() {
        val callee = Function.Name.User(null, "helper")
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
                        Function.Name.User(null, "main") to caller,
                        Function.Name.User(null, "helper") to helper,
                    ),
                ),
            )
        val w = wat(program)
        assertContains(w, "call \$fn.top.helper")
        assertContains(w, "i32.const 5")
    }

    @Test
    fun `plus application emits i32 add`() {
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
        assertContains(w, "i32.add")
        assertContains(w, "call \$rt.mk_tuple")
        assertContains(w, "i32.const 3")
        assertContains(w, "i32.const 4")
        assertContains(w, "i32.load offset=0")
        assertContains(w, "i32.load offset=4")
    }
}
