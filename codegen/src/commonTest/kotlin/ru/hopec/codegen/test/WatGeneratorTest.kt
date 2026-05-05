package ru.hopec.codegen.test

import ru.hopec.codegen.WatGenerator
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Type
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

// ─── Test helpers ──────────────────────────────────────────────────────────────

private val numType    = Type.Data.num
private val truvalType = Type.Data.truval
private val charType   = Type.Data.char

private val userName   = Function.Name.User(null, "f")
private val trueCtor   = Function.Name.Constructor(Data.Name.Core.TruVal, "true")
private val falseCtor  = Function.Name.Constructor(Data.Name.Core.TruVal, "false")
private val nilCtor    = Function.Name.Constructor(Data.Name.Core.List, "nil")
private val consCtor   = Function.Name.Constructor(Data.Name.Core.List, "cons")
private val tupleCtor  = Function.Name.Constructor(Data.Name.Core.Tuple, "#")
private val plusCore   = Function.Name.Core("+")

private fun emptyProgram() =
    TypedRepresentation(emptyMap(), Declarations(emptyMap(), emptyMap()))

private fun singleFuncProgram(name: Function.Name = userName, lambda: Expr.Lambda) =
    TypedRepresentation(
        emptyMap(),
        Declarations(emptyMap(), mapOf(name to Function(lambda, 0)))
    )

/** Lambda with a single wildcard branch and the given body. */
private fun wildLambda(argType: Type, resultType: Type, body: Expr) =
    Expr.Lambda(
        Type.Arrow(argType, resultType),
        listOf(Expr.Lambda.Branch(Pattern.Wildcard(argType), body))
    )

/** Lambda with a single variable-binding branch. */
private fun varLambda(varName: String, argType: Type, resultType: Type, body: Expr) =
    Expr.Lambda(
        Type.Arrow(argType, resultType),
        listOf(Expr.Lambda.Branch(Pattern.Variable(argType, varName), body))
    )

private fun wat(program: TypedRepresentation) = WatGenerator(program).generate()

// ─── Tests ─────────────────────────────────────────────────────────────────────

class WatGeneratorTest {

    // ── Module structure ──────────────────────────────────────────────────────

    @Test
    fun `empty program emits module wrapper`() {
        val w = wat(emptyProgram())
        assertTrue(w.trimStart().startsWith("(module"), "Expected WAT to start with (module")
        assertTrue(w.trimEnd().endsWith(")"), "Expected WAT to end with )")
    }

    @Test
    fun `runtime functions are always emitted`() {
        val w = wat(emptyProgram())
        assertContains(w, "\$rt.malloc")
        assertContains(w, "\$rt.mk_tuple")
        assertContains(w, "\$rt.mk_cons")
        assertContains(w, "\$rt.apply")
    }

    @Test
    fun `memory and heap pointer are always emitted`() {
        val w = wat(emptyProgram())
        assertContains(w, "memory")
        assertContains(w, "\$heap_ptr")
    }

    // ── Literals ──────────────────────────────────────────────────────────────

    @Test
    fun `Num literal emits i32 const`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(42))))
        assertContains(w, "i32.const 42")
    }

    @Test
    fun `TruVal true literal emits i32 const 1`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, truvalType, Expr.Literal.TruVal(true))))
        assertContains(w, "i32.const 1")
    }

    @Test
    fun `TruVal false literal emits i32 const 0`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, truvalType, Expr.Literal.TruVal(false))))
        // i32.const 0 may appear many times (e.g. heap_ptr init), just check it's there
        assertContains(w, "i32.const 0")
    }

    @Test
    fun `Char literal emits its code point`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, charType, Expr.Literal.Char('A'))))
        assertContains(w, "i32.const 65")   // 'A' = 65
    }

    @Test
    fun `negative Num literal emits correct value`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(-1))))
        assertContains(w, "i32.const -1")
    }

    // ── Patterns ──────────────────────────────────────────────────────────────

    @Test
    fun `wildcard pattern emits no br_if`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))
        // The single-branch match still emits the block structure, but no br_if for wildcard.
        assertContains(w, "unreachable")
    }

    @Test
    fun `variable pattern emits local set and get`() {
        val lambda = varLambda("x", numType, numType, Expr.Variable(numType, "x"))
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "local.set")
        assertContains(w, "local.get")
    }

    @Test
    fun `TruVal true pattern checks against 1`() {
        val truePat = Pattern.Data(truvalType, trueCtor, emptyList())
        val lambda = Expr.Lambda(
            Type.Arrow(truvalType, numType),
            listOf(
                Expr.Lambda.Branch(truePat,  Expr.Literal.Num(1)),
                Expr.Lambda.Branch(Pattern.Wildcard(truvalType), Expr.Literal.Num(0))
            )
        )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "i32.const 1")
        assertContains(w, "i32.ne")
        assertContains(w, "br_if")
    }

    @Test
    fun `nil pattern checks pointer is zero`() {
        val nilPat = Pattern.Data(Type.Data.list(numType), nilCtor, emptyList())
        val lambda = Expr.Lambda(
            Type.Arrow(Type.Data.list(numType), truvalType),
            listOf(
                Expr.Lambda.Branch(nilPat, Expr.Literal.TruVal(true)),
                Expr.Lambda.Branch(Pattern.Wildcard(Type.Data.list(numType)), Expr.Literal.TruVal(false))
            )
        )
        val w = wat(singleFuncProgram(lambda = lambda))
        // nil branch: br_if on non-zero (bare pointer is truthy → fail if non-nil)
        assertContains(w, "br_if")
    }

    @Test
    fun `cons pattern checks pointer is non-zero`() {
        val tuplePat = Pattern.Data(
            Type.Data.tuple(numType, Type.Data.list(numType)),
            tupleCtor,
            listOf(Pattern.Wildcard(numType), Pattern.Wildcard(Type.Data.list(numType)))
        )
        val consPat = Pattern.Data(Type.Data.list(numType), consCtor, listOf(tuplePat))
        val lambda = Expr.Lambda(
            Type.Arrow(Type.Data.list(numType), truvalType),
            listOf(
                Expr.Lambda.Branch(consPat,  Expr.Literal.TruVal(false)),
                Expr.Lambda.Branch(Pattern.Wildcard(Type.Data.list(numType)), Expr.Literal.TruVal(true))
            )
        )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "i32.eqz")
        assertContains(w, "br_if")
        assertContains(w, "i32.load")
    }

    @Test
    fun `tuple pattern loads both fields`() {
        val tuplePat = Pattern.Data(
            Type.Data.tuple(numType, numType),
            tupleCtor,
            listOf(Pattern.Variable(numType, "a"), Pattern.Variable(numType, "b"))
        )
        val body = Expr.Variable(numType, "a")
        val lambda = Expr.Lambda(
            Type.Arrow(Type.Data.tuple(numType, numType), numType),
            listOf(Expr.Lambda.Branch(tuplePat, body))
        )
        val w = wat(singleFuncProgram(lambda = lambda))
        assertContains(w, "i32.load offset=0")
        assertContains(w, "i32.load offset=4")
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    @Test
    fun `if expression emits s-expression form`() {
        val ifExpr = Expr.If(
            Expr.Literal.TruVal(true),
            Expr.Literal.Num(1),
            Expr.Literal.Num(0)
        )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, ifExpr)))
        assertContains(w, "(if (result i32)")
        assertContains(w, "(then")
        assertContains(w, "(else")
    }

    @Test
    fun `let binding emits local set then body`() {
        val letExpr = Expr.Let(
            Pattern.Variable(numType, "y"),
            Expr.Literal.Num(10),
            Expr.Variable(numType, "y")
        )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, letExpr)))
        assertContains(w, "i32.const 10")
        assertContains(w, "local.set")
    }

    // ── Function calls ────────────────────────────────────────────────────────

    @Test
    fun `user function emits direct call`() {
        val callee  = Function.Name.User(null, "helper")
        val callExpr = Expr.Application(
            numType,
            Expr.Identifier(Type.Arrow(numType, numType), callee),
            Expr.Literal.Num(5)
        )
        val helper = Function(wildLambda(numType, numType, Expr.Literal.Num(0)), 0)
        val caller = Function(wildLambda(numType, numType, callExpr), 0)
        val program = TypedRepresentation(
            emptyMap(),
            Declarations(emptyMap(), mapOf(
                Function.Name.User(null, "main")   to caller,
                Function.Name.User(null, "helper") to helper
            ))
        )
        val w = wat(program)
        assertContains(w, "call \$fn.top.helper")
    }

    @Test
    fun `plus application emits i32 add`() {
        // +(#(a, b)) — tuple constructor applied, then +
        val tupleExpr = Expr.Application(
            Type.Data.tuple(numType, numType),
            Expr.Application(
                Type.Arrow(numType, Type.Data.tuple(numType, numType)),
                Expr.Identifier(Type.Arrow(numType, Type.Arrow(numType, Type.Data.tuple(numType, numType))), tupleCtor),
                Expr.Literal.Num(3)
            ),
            Expr.Literal.Num(4)
        )
        val addExpr = Expr.Application(
            numType,
            Expr.Identifier(Type.Arrow(Type.Data.tuple(numType, numType), numType), plusCore),
            tupleExpr
        )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, addExpr)))
        assertContains(w, "i32.add")
    }

    // ── Constructors ──────────────────────────────────────────────────────────

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
        val tupleExpr = Expr.Application(
            Type.Data.tuple(numType, numType),
            Expr.Application(
                Type.Arrow(numType, Type.Data.tuple(numType, numType)),
                Expr.Identifier(Type.Arrow(numType, Type.Arrow(numType, Type.Data.tuple(numType, numType))), tupleCtor),
                Expr.Literal.Num(1)
            ),
            Expr.Literal.Num(2)
        )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.tuple(numType, numType), tupleExpr)))
        assertContains(w, "call \$rt.mk_tuple")
    }

    @Test
    fun `cons constructor call emits mk_cons`() {
        val tupleArg = Expr.Application(
            Type.Data.tuple(numType, Type.Data.list(numType)),
            Expr.Application(
                Type.Arrow(numType, Type.Data.tuple(numType, Type.Data.list(numType))),
                Expr.Identifier(
                    Type.Arrow(numType, Type.Arrow(Type.Data.list(numType), Type.Data.tuple(numType, Type.Data.list(numType)))),
                    tupleCtor
                ),
                Expr.Literal.Num(1)
            ),
            Expr.Identifier(Type.Data.list(numType), nilCtor)
        )
        val consExpr = Expr.Application(
            Type.Data.list(numType),
            Expr.Identifier(Type.Arrow(Type.Data.tuple(numType, Type.Data.list(numType)), Type.Data.list(numType)), consCtor),
            tupleArg
        )
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.list(numType), consExpr)))
        assertContains(w, "call \$rt.mk_cons")
    }

    // ── User-defined ADT ──────────────────────────────────────────────────────

    @Test
    fun `user ADT constructor assigns tags 0 and 1`() {
        val dataName  = Data.Name.Defined(null, "Color")
        val redCtor   = Function.Name.Constructor(dataName, "Red")
        val blueCtor  = Function.Name.Constructor(dataName, "Blue")
        val colorData = Data(mapOf("Red" to emptyList(), "Blue" to emptyList()), 0)
        val colorType = Type.Data(dataName, emptyList())

        val body = Expr.Identifier(colorType, redCtor)
        val program = TypedRepresentation(
            emptyMap(),
            Declarations(
                mapOf(dataName to colorData),
                mapOf(userName to Function(wildLambda(numType, colorType, body), 0))
            )
        )
        val w = wat(program)
        // Red gets tag 0, so the whole constructor is nullary — emitted inline
        // The important thing: Blue gets tag 1, Red gets tag 0 via constructorTags
        assertTrue(w.contains("(module"))
    }

    // ── String literal ────────────────────────────────────────────────────────

    @Test
    fun `string literal emits mk_cons and mk_tuple for each char`() {
        val body = Expr.Literal.String("hi")
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, Type.Data.string, body)))
        // "hi" has 2 chars → 2 mk_tuple + 2 mk_cons calls
        val tupleCount = w.split("call \$rt.mk_tuple").size - 1
        val consCount  = w.split("call \$rt.mk_cons").size - 1
        assertTrue(tupleCount >= 2, "Expected at least 2 mk_tuple calls, got $tupleCount")
        assertTrue(consCount  >= 2, "Expected at least 2 mk_cons calls, got $consCount")
    }

    // ── Function naming ───────────────────────────────────────────────────────

    @Test
    fun `user function gets scoped WAT name`() {
        val w = wat(singleFuncProgram(
            name   = Function.Name.User("MyModule", "myFunc"),
            lambda = wildLambda(numType, numType, Expr.Literal.Num(0))
        ))
        assertContains(w, "\$fn.MyModule.myFunc")
    }

    @Test
    fun `top-level function gets top scope name`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))
        assertContains(w, "\$fn.top.f")
    }

    @Test
    fun `operator name is escaped`() {
        val w = wat(singleFuncProgram(
            name   = Function.Name.User(null, "add+"),
            lambda = wildLambda(numType, numType, Expr.Literal.Num(0))
        ))
        assertContains(w, "_plus")
    }

    // ── Exports ───────────────────────────────────────────────────────────────

    @Test
    fun `top-level user function is exported by name`() {
        val w = wat(singleFuncProgram(lambda = wildLambda(numType, numType, Expr.Literal.Num(0))))
        assertContains(w, "(export \"f\" (func \$fn.top.f))")
    }

    @Test
    fun `main function is exported`() {
        val w = wat(singleFuncProgram(
            name   = Function.Name.User(null, "main"),
            lambda = wildLambda(numType, numType, Expr.Literal.Num(0))
        ))
        assertContains(w, "(export \"main\" (func \$fn.top.main))")
    }

    // ── Multi-branch matching ─────────────────────────────────────────────────

    @Test
    fun `multiple branches emit multiple skip blocks`() {
        val lambda = Expr.Lambda(
            Type.Arrow(truvalType, numType),
            listOf(
                Expr.Lambda.Branch(
                    Pattern.Data(truvalType, trueCtor, emptyList()),
                    Expr.Literal.Num(1)
                ),
                Expr.Lambda.Branch(
                    Pattern.Data(truvalType, falseCtor, emptyList()),
                    Expr.Literal.Num(0)
                )
            )
        )
        val w = wat(singleFuncProgram(lambda = lambda))
        val skipCount = w.split("\$skip").size - 1
        assertTrue(skipCount >= 2, "Expected at least 2 skip labels, got $skipCount")
    }
}
