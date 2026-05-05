package ru.hopec.codegen

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data.Name as DataName
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function.Name as FuncName

/**
 * Translates [TypedRepresentation] to WebAssembly Text Format (WAT).
 *
 * ## Memory representation
 * Every HOPE value is an `i32`.
 *
 * | HOPE type          | WAT value                                          |
 * |--------------------|----------------------------------------------------|
 * | `Num`              | unboxed `i32` (low 32 bits of the `Long` literal)  |
 * | `Char`             | unboxed `i32` (Unicode code point)                 |
 * | `TruVal`           | unboxed `i32`: 0 = false, 1 = true                 |
 * | `nil`              | `i32` == 0 (null pointer)                          |
 * | `cons(arg)`        | heap pointer → `[field: i32]`                      |
 * | `Tuple # (a, b)`   | heap pointer → `[fst: i32, snd: i32]`              |
 * | user ADT ctor      | heap pointer → `[tag: i32, field₀: i32, …]`        |
 * | closure `a → b`    | heap pointer → `[func_idx: i32, n_caps: i32, …]`   |
 *
 * Expression and pattern-match code generation is delegated to [WatCodeEmitter].
 */
class WatGenerator(private val program: TypedRepresentation) {

    // ── Output emitter ──────────────────────────────────────────────────────
    private val out = WatEmitter()

    // ── Counters ────────────────────────────────────────────────────────────
    private var labelCounter  = 0
    private var liftedCounter = 0

    // ── Constructor tags: (DataName × ctorName) → tag within the type ───────
    internal val constructorTags = mutableMapOf<Pair<DataName, String>, Int>()

    // ── Function table for indirect calls (closures) ─────────────────────────
    private val funcTable    = mutableListOf<String>()
    private val funcTableIdx = mutableMapOf<String, Int>()

    // ── Lambdas lifted to module scope ───────────────────────────────────────
    internal data class LiftedLambda(
        val watName  : String,
        val captures : List<String>,
        val lambda   : Expr.Lambda
    )
    private val liftedLambdas = mutableListOf<LiftedLambda>()

    // ── Delegate for expression / pattern code ───────────────────────────────
    private val code = WatCodeEmitter(this)

    // ═══════════════════════════════════════════════════════════════════════
    // API exposed to WatCodeEmitter
    // ═══════════════════════════════════════════════════════════════════════

    internal fun freshLabel(prefix: String = "lbl") = "\$$prefix${labelCounter++}"

    internal fun nextLiftedId() = liftedCounter++

    internal fun addLiftedLambda(lifted: LiftedLambda) { liftedLambdas.add(lifted) }

    internal fun registerInFuncTable(watName: String): Int =
        funcTableIdx.getOrPut(watName) { funcTable.add(watName); funcTable.size - 1 }

    internal fun watId(name: FuncName): String = when (name) {
        is FuncName.Core        -> "\$core.${esc(name.name)}"
        is FuncName.User        -> "\$fn.${name.module ?: "top"}.${esc(name.name)}"
        is FuncName.Constructor -> "\$ctor.${dataStr(name.data)}.${esc(name.constructor)}"
    }

    internal fun esc(s: String): String =
        s.replace("+", "_plus")
         .replace("-", "_minus")
         .replace("*", "_mul")
         .replace("/", "_div")
         .replace("#", "hash")
         .replace(".", "_dot")
         .replace(" ", "_")
         .filter { it.isLetterOrDigit() || it == '_' }

    private fun dataStr(name: DataName): String = when (name) {
        DataName.Core.Char   -> "Char"
        DataName.Core.TruVal -> "TruVal"
        DataName.Core.Num    -> "Num"
        DataName.Core.List   -> "List"
        DataName.Core.Set    -> "Set"
        DataName.Core.Tuple  -> "Tuple"
        is DataName.Defined  -> "${name.module ?: "top"}.${name.name}"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Entry point
    // ═══════════════════════════════════════════════════════════════════════

    fun generate(): String {
        assignConstructorTags()
        out.line("(module")
        out.indent {
            emitMemoryAndGlobals()
            emitRuntime()
            emitAllFunctions()
            emitFunctionTable()
            emitExports()
        }
        out.line(")")
        return out.toString()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Constructor-tag assignment
    // ═══════════════════════════════════════════════════════════════════════

    private fun assignConstructorTags() {
        fun process(name: DataName, data: Data) {
            var tag = 0
            for ((ctor, _) in data.constructors) constructorTags[name to ctor] = tag++
        }
        for ((name, data) in program.topLevel.data) process(name, data)
        for ((_, module) in program.modules) {
            for ((name, data) in module.public.data)  process(name, data)
            for ((name, data) in module.private.data) process(name, data)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Memory, globals, runtime
    // ═══════════════════════════════════════════════════════════════════════

    private fun emitMemoryAndGlobals() {
        out.line("(memory (export \"memory\") 1)")
        out.line("(global \$heap_ptr (mut i32) (i32.const 4096))")
    }

    private fun emitRuntime() {
        emitMalloc()
        emitMkTuple()
        emitMkCons()
        emitApply()
    }

    private fun emitMalloc() {
        out.line("(func \$rt.malloc (param \$bytes i32) (result i32)")
        out.indent {
            out.line("global.get \$heap_ptr")
            out.line("global.get \$heap_ptr")
            out.line("local.get \$bytes")
            out.line("i32.add")
            out.line("global.set \$heap_ptr")
        }
        out.line(")")
    }

    private fun emitMkTuple() {
        out.line("(func \$rt.mk_tuple (param \$fst i32) (param \$snd i32) (result i32)")
        out.indent {
            out.line("(local \$ptr i32)")
            out.line("i32.const 8")
            out.line("call \$rt.malloc")
            out.line("local.tee \$ptr")
            out.line("local.get \$fst")
            out.line("i32.store offset=0")
            out.line("local.get \$ptr")
            out.line("local.get \$snd")
            out.line("i32.store offset=4")
            out.line("local.get \$ptr")
        }
        out.line(")")
    }

    private fun emitMkCons() {
        out.line("(func \$rt.mk_cons (param \$field i32) (result i32)")
        out.indent {
            out.line("(local \$ptr i32)")
            out.line("i32.const 4")
            out.line("call \$rt.malloc")
            out.line("local.tee \$ptr")
            out.line("local.get \$field")
            out.line("i32.store offset=0")
            out.line("local.get \$ptr")
        }
        out.line(")")
    }

    private fun emitApply() {
        out.line("(type \$closure_fn (func (param i32 i32) (result i32)))")
        out.line("(func \$rt.apply (param \$closure i32) (param \$arg i32) (result i32)")
        out.indent {
            out.line("local.get \$closure")
            out.line("local.get \$arg")
            out.line("local.get \$closure")
            out.line("i32.load offset=0")
            out.line("call_indirect (type \$closure_fn)")
        }
        out.line(")")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Function emission
    // ═══════════════════════════════════════════════════════════════════════

    private fun emitAllFunctions() {
        emitDeclFunctions(program.topLevel)
        for ((_, module) in program.modules) {
            emitDeclFunctions(module.public)
            emitDeclFunctions(module.private)
        }
        // Lifted lambdas may grow as we emit (lambdas inside lambdas).
        var i = 0
        while (i < liftedLambdas.size) { emitLiftedLambda(liftedLambdas[i++]) }
    }

    private fun emitDeclFunctions(decls: Declarations) {
        for ((name, func) in decls.functions) emitFunction(watId(name), func.lambda)
    }

    /** Emits a top-level function with one `i32` argument. */
    private fun emitFunction(watName: String, lambda: Expr.Lambda) {
        val ctx  = WatFunctionContext(::esc)
        collectLambdaVars(lambda, ctx)

        // Generate body into a sub-emitter so tmps are known before emitting locals.
        val body = WatEmitter()
        code.emitBranchMatch(lambda.branches, "\$arg", ctx, body)

        out.line("(func $watName (param \$arg i32) (result i32)")
        out.indent {
            for (local in ctx.allLocals()) out.line("(local $local i32)")
            out.append(body)
        }
        out.line(")")
    }

    /**
     * A lifted lambda takes `(closure_ptr: i32, arg: i32) → i32` so it can be
     * called via `$rt.apply`.  Captures are loaded from `closure_ptr` at offsets
     * 8, 12, 16, … (skipping func_idx[4] and n_caps[4]).
     */
    private fun emitLiftedLambda(lifted: LiftedLambda) {
        val ctx = WatFunctionContext(::esc)
        collectLambdaVars(lifted.lambda, ctx)
        for (cap in lifted.captures) ctx.getOrAdd(cap)

        val body = WatEmitter()
        for ((i, cap) in lifted.captures.withIndex()) {
            body.line("local.get \$closure_ptr")
            body.line("i32.load offset=${8 + i * 4}")
            body.line("local.set ${ctx.getOrAdd(cap)}")
        }
        code.emitBranchMatch(lifted.lambda.branches, "\$arg", ctx, body)

        out.line("(func ${lifted.watName} (param \$closure_ptr i32) (param \$arg i32) (result i32)")
        out.indent {
            for (local in ctx.allLocals()) out.line("(local $local i32)")
            out.append(body)
        }
        out.line(")")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Pre-scan: collect user-visible variable names from patterns and lets
    // ═══════════════════════════════════════════════════════════════════════

    private fun collectLambdaVars(lambda: Expr.Lambda, ctx: WatFunctionContext) {
        for (b in lambda.branches) { collectPatVars(b.pattern, ctx); collectExprVars(b.body, ctx) }
    }

    private fun collectPatVars(p: Pattern, ctx: WatFunctionContext) {
        when (p) {
            is Pattern.Variable  -> ctx.getOrAdd(p.name)
            is Pattern.NamedData -> { ctx.getOrAdd(p.name); collectPatVars(p.data, ctx) }
            is Pattern.Data      -> p.args.forEach { collectPatVars(it, ctx) }
            is Pattern.Wildcard  -> {}
        }
    }

    private fun collectExprVars(e: Expr, ctx: WatFunctionContext) {
        when (e) {
            is Expr.Let    -> {
                collectPatVars(e.pattern, ctx)
                collectExprVars(e.matcher, ctx)
                collectExprVars(e.body, ctx)
            }
            is Expr.Lambda -> e.branches.forEach {
                collectPatVars(it.pattern, ctx)
                collectExprVars(it.body, ctx)
            }
            is Expr.Application -> { collectExprVars(e.left, ctx); collectExprVars(e.right, ctx) }
            is Expr.If -> {
                collectExprVars(e.condition, ctx)
                collectExprVars(e.positive, ctx)
                collectExprVars(e.negative, ctx)
            }
            else -> {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Exports
    // ═══════════════════════════════════════════════════════════════════════

    private fun emitExports() {
        for ((name, _) in program.topLevel.functions) {
            if (name is FuncName.User) {
                out.line("(export \"${name.name}\" (func ${watId(name)}))")
            }
        }
        for ((moduleName, module) in program.modules) {
            for ((name, _) in module.public.functions) {
                if (name is FuncName.User) {
                    out.line("(export \"$moduleName.${name.name}\" (func ${watId(name)}))")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Function table
    // ═══════════════════════════════════════════════════════════════════════

    private fun emitFunctionTable() {
        if (funcTable.isEmpty()) return
        out.line("(table (export \"table\") ${funcTable.size} funcref)")
        out.line("(elem (i32.const 0) ${funcTable.joinToString(" ")})")
    }
}
