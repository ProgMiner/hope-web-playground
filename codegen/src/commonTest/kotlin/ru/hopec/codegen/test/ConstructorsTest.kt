package ru.hopec.codegen.test

import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains

class ConstructorsTest {
    private val trueCtor = Function.Name.Constructor(Type.Data.truval.constructor, "true")
    private val nilCtor = Function.Name.Constructor(Type.Data.list(numType).constructor, "nil")
    private val consCtor = Function.Name.Constructor(Type.Data.list(numType).constructor, "cons")
    private val tupleCtor = Function.Name.Constructor(Type.Data.tuple(numType, numType).constructor, "#")

    @Test
    fun `nil identifier emits i32 const 0`() {
        val body = Expr.Identifier(Type.Data.list(numType), nilCtor)
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.list(numType), body)))
        assertContains(w, "i32.const 0")
    }

    @Test
    fun `true identifier emits i32 const 1`() {
        val body = Expr.Identifier(truvalType, trueCtor)
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, truvalType, body)))
        assertContains(w, "i32.const 1")
    }

    @Test
    fun `tuple constructor call emits mk_tuple`() {
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
        assertContains(w, "call \$rt.mk_tuple")
        assertContains(w, "i32.const 1")
        assertContains(w, "i32.const 2")
    }

    @Test
    fun `cons constructor call emits mk_cons`() {
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
        assertContains(w, "call \$rt.mk_cons")
        assertContains(w, "call \$rt.mk_tuple")
        assertContains(w, "i32.const 1")
        assertContains(w, "i32.const 0")
    }
}
