package ru.hopec.codegen.test

import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FunName

class TailCallTest {
    @Test
    fun `self tail call rewrites to loop`() {
        val self = FunName.User(null, "sum_acc")
        val selfType = Type.Arrow(numType, numType)
        val tailCall =
            Expr.Application(
                numType,
                Expr.Identifier(selfType, self),
                Expr.Literal.Num(1),
            )
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    emptyMap(),
                    mapOf(
                        self to
                            Function(
                                Expr.Lambda(
                                    selfType,
                                    listOf(
                                        Expr.Lambda.Branch(Pattern.Wildcard(numType), Expr.Literal.Num(0)),
                                        Expr.Lambda.Branch(Pattern.Wildcard(numType), tailCall),
                                    ),
                                ),
                                0,
                            ),
                    ),
                ),
            )
        val w = wat(program)
        val body = region(w, "(func \$fn.top.sum_acc")

        assertContains(body, "loop \$tail_loop")
        assertContains(body, "local.set \$arg")
        assertContains(body, "br \$tail_loop")
        assertFalse(body.contains("(call \$fn.top.sum_acc"))
        assertFalse(body.contains("return_call"))
    }

    @Test
    fun `self tail call with multiple args nests tuple left`() {
        val self = FunName.User(null, "foldl")
        val pairType = Type.Data.tuple(numType, numType)
        val argType = Type.Data.tuple(pairType, numType)
        val fnType = Type.Arrow(argType, numType)
        val tailCall =
            Expr.Application(
                numType,
                Expr.Application(
                    numType,
                    Expr.Application(
                        numType,
                        Expr.Identifier(Type.Arrow(numType, numType), self),
                        Expr.Literal.Num(1),
                    ),
                    Expr.Literal.Num(2),
                ),
                Expr.Literal.Num(3),
            )
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    emptyMap(),
                    mapOf(
                        self to
                            Function(
                                Expr.Lambda(
                                    fnType,
                                    listOf(
                                        Expr.Lambda.Branch(Pattern.Wildcard(argType), Expr.Literal.Num(0)),
                                        Expr.Lambda.Branch(Pattern.Wildcard(argType), tailCall),
                                    ),
                                ),
                                0,
                            ),
                    ),
                ),
            )
        val w = wat(program)
        val body = region(w, "(func \$fn.top.foldl")

        assertContains(body, "loop \$tail_loop")
        assertContains(body, "(local.set \$arg")
        assertContains(body, "(call \$rt.mk_tuple")
        assertContains(body, "(i32.const 1)")
        assertContains(body, "(i32.const 2)")
        assertContains(body, "(i32.const 3)")
    }

    @Test
    fun `self tail call through if rewrites to loop`() {
        val self = FunName.User(null, "loop_if")
        val argType = Type.Data.tuple(numType, numType)
        val selfType = Type.Arrow(argType, numType)
        val thenTail =
            Expr.Application(
                numType,
                Expr.Application(
                    numType,
                    Expr.Identifier(selfType, self),
                    Expr.Literal.Num(1),
                ),
                Expr.Literal.Num(2),
            )
        val elseTail =
            Expr.Application(
                numType,
                Expr.Application(
                    numType,
                    Expr.Identifier(selfType, self),
                    Expr.Literal.Num(3),
                ),
                Expr.Literal.Num(4),
            )
        val program =
            TypedRepresentation(
                emptyMap(),
                Declarations(
                    emptyMap(),
                    mapOf(
                        self to
                            Function(
                                Expr.Lambda(
                                    selfType,
                                    listOf(
                                        Expr.Lambda.Branch(
                                            Pattern.Wildcard(argType),
                                            Expr.If(
                                                Expr.Literal.TruVal(true),
                                                thenTail,
                                                elseTail,
                                            ),
                                        ),
                                    ),
                                ),
                                0,
                            ),
                    ),
                ),
            )
        val w = wat(program)
        val body = region(w, "(func \$fn.top.loop_if")

        assertContains(body, "loop \$tail_loop")
        assertContains(body, "br \$tail_loop")
        assertFalse(body.contains("(call \$fn.top.loop_if"))
    }
}
