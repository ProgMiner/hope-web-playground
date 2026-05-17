package ru.hopec.codegen

import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data.Name as DataName
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function.Name as FuncName

/**
 * Генерирует WAT-инструкции для выражений и веток сопоставления с паттернами.
 * Всё изменяемое состояние генерации (метки, поднятые лямбды, таблица функций)
 * хранится в [gen] — так обе половины остаются согласованы.
 */
internal class WatCodeEmitter(
    private val gen: WatGenerator,
) {
    // ═══════════════════════════════════════════════════════════════════════
    // Ветвление / сопоставление с паттернами
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Эмиттирует многоветочное сопоставление с паттернами в [out].
     *
     * Каждая ветка обёрнута в `(block $skip …)`: неуспешная проверка паттерна
     * делает `br_if $skip`, пропуская тело и переходя к следующей ветке.
     * Успешная проверка выполняет тело, затем `br $match_end`.
     */
    fun emitBranchMatch(
        branches: List<Expr.Lambda.Branch>,
        argLocal: String,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        if (branches.isEmpty()) {
            out.line("unreachable")
            return
        }
        val matchEnd = gen.freshLabel("match_end")
        out.line("(block $matchEnd (result i32)")
        out.indent {
            for (branch in branches) {
                val skip = gen.freshLabel("skip")
                out.line("(block $skip")
                out.indent {
                    emitPatternCheck(branch.pattern, argLocal, skip, ctx, out)
                    genExpr(branch.body, ctx, out)
                    out.line("br $matchEnd")
                }
                out.line(")")
            }
            out.line("unreachable")
        }
        out.line(")")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Проверка паттернов
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Эмиттирует проверки паттерна [pattern] для значения в WAT-локале [argLocal].
     * При несовпадении: эмиттируется `br_if $failLabel` (выход из охватывающего блока).
     * При совпадении: устанавливаются локалы для связанных переменных, управление
     * проваливается дальше.
     */
    fun emitPatternCheck(
        pattern: Pattern,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        when (pattern) {
            is Pattern.Wildcard -> { /* всегда совпадает */ }
            is Pattern.Variable -> {
                out.line("local.get $argLocal")
                out.line("local.set ${ctx.getOrAdd(pattern.name)}")
            }
            is Pattern.NamedData -> {
                out.line("local.get $argLocal")
                out.line("local.set ${ctx.getOrAdd(pattern.name)}")
                emitPatternCheck(pattern.data, argLocal, failLabel, ctx, out)
            }
            is Pattern.Data -> emitDataCheck(pattern, argLocal, failLabel, ctx, out)
        }
    }

    private fun emitDataCheck(
        pattern: Pattern.Data,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        val ctor = pattern.constructor
        when {
            // ── TruVal ──────────────────────────────────────────────────────
            ctor.data == DataName.Core.TruVal -> {
                val expected = if (ctor.constructor == "true") 1 else 0
                out.line("local.get $argLocal")
                out.line("i32.const $expected")
                out.line("i32.ne")
                out.line("br_if $failLabel")
            }

            // ── nil: указатель на кучу == 0 ─────────────────────────────────
            ctor.data == DataName.Core.List && ctor.constructor == "nil" -> {
                out.line("local.get $argLocal")
                out.line("br_if $failLabel") // ненулевой = cons = не совпадает
            }

            // ── emptySet: указатель на кучу == 0 (как и nil) ────────────────
            ctor.data == DataName.Core.Set && ctor.constructor == "emptySet" -> {
                out.line("local.get $argLocal")
                out.line("br_if $failLabel") // ненулевой = непустое множество = не совпадает
            }

            // ── cons: хранит одно поле (указатель на кортеж (head, tail)) ───
            ctor.data == DataName.Core.List && ctor.constructor == "cons" -> {
                out.line("local.get $argLocal")
                out.line("i32.eqz")
                out.line("br_if $failLabel") // ноль = nil = не совпадает
                if (pattern.args.isNotEmpty()) {
                    val tmp = ctx.freshTmp()
                    out.line("local.get $argLocal")
                    out.line("i32.load offset=0")
                    out.line("local.set $tmp")
                    emitPatternCheck(pattern.args[0], tmp, failLabel, ctx, out)
                }
            }

            // ── Tuple # (fst, snd): объект в куче {fst: i32, snd: i32} ──────
            ctor.data == DataName.Core.Tuple -> {
                if (pattern.args.isNotEmpty()) {
                    val t0 = ctx.freshTmp()
                    out.line("local.get $argLocal")
                    out.line("i32.load offset=0")
                    out.line("local.set $t0")
                    emitPatternCheck(pattern.args[0], t0, failLabel, ctx, out)
                }
                if (pattern.args.size >= 2) {
                    val t1 = ctx.freshTmp()
                    out.line("local.get $argLocal")
                    out.line("i32.load offset=4")
                    out.line("local.set $t1")
                    emitPatternCheck(pattern.args[1], t1, failLabel, ctx, out)
                }
            }

            // ── Пользовательский ADT: {tag: i32, field₀: i32, …} ─────────────
            else -> {
                val tag = gen.constructorTags[ctor.data to ctor.constructor] ?: 0
                out.line("local.get $argLocal")
                out.line("i32.load offset=0")
                out.line("i32.const $tag")
                out.line("i32.ne")
                out.line("br_if $failLabel")
                for ((i, sub) in pattern.args.withIndex()) {
                    val tmp = ctx.freshTmp()
                    out.line("local.get $argLocal")
                    out.line("i32.load offset=${4 + i * 4}")
                    out.line("local.set $tmp")
                    emitPatternCheck(sub, tmp, failLabel, ctx, out)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Генерация кода для выражений
    // ═══════════════════════════════════════════════════════════════════════

    /** Генерирует код для [expr], оставляя результат типа `i32` на стеке операндов. */
    fun genExpr(
        expr: Expr,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        when (expr) {
            is Expr.Literal.Num -> out.line("i32.const ${expr.value.toInt()}")
            is Expr.Literal.TruVal -> out.line("i32.const ${if (expr.value) 1 else 0}")
            is Expr.Literal.Char -> out.line("i32.const ${expr.value.code}")
            is Expr.Literal.String -> genString(expr.value, ctx, out)
            is Expr.Variable -> out.line("local.get ${ctx.getOrAdd(expr.name)}")
            is Expr.Identifier -> genIdentifier(expr, ctx, out)
            is Expr.Application -> genApplication(expr, ctx, out)
            is Expr.If -> genIf(expr, ctx, out)
            is Expr.Let -> genLet(expr, ctx, out)
            is Expr.Lambda -> genLambdaClosure(expr, ctx, out)
        }
    }

    private fun genIf(
        expr: Expr.If,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        genExpr(expr.condition, ctx, out)
        out.line("(if (result i32)")
        out.indent {
            out.line("(then")
            out.indent { genExpr(expr.positive, ctx, out) }
            out.line(")")
            out.line("(else")
            out.indent { genExpr(expr.negative, ctx, out) }
            out.line(")")
        }
        out.line(")")
    }

    private fun genLet(
        expr: Expr.Let,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        val tmp = ctx.freshTmp()
        genExpr(expr.matcher, ctx, out)
        out.line("local.set $tmp")
        // В корректно типизированных программах let-паттерн всегда совпадает.
        // Тем не менее, эмиттируем структуру блока — недостижимая ветка br
        // остаётся валидным WAT.
        val letFail = gen.freshLabel("let_fail")
        out.line("(block $letFail")
        out.indent { emitPatternCheck(expr.pattern, tmp, letFail, ctx, out) }
        out.line(")")
        genExpr(expr.body, ctx, out)
    }

    // ── Идентификаторы ──────────────────────────────────────────────────────

    private fun genIdentifier(
        expr: Expr.Identifier,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        when (val name = expr.name) {
            is FuncName.Core ->
                when (name.name) {
                    "nil" -> out.line("i32.const 0")
                    "true" -> out.line("i32.const 1")
                    "false" -> out.line("i32.const 0")
                    "emptySet" -> out.line("i32.const 0")
                    else -> genClosureRef(gen.watId(name), emptyList(), ctx, out)
                }
            is FuncName.Constructor ->
                when {
                    name.data == DataName.Core.TruVal && name.constructor == "true" -> out.line("i32.const 1")
                    name.data == DataName.Core.TruVal && name.constructor == "false" -> out.line("i32.const 0")
                    name.data == DataName.Core.List && name.constructor == "nil" -> out.line("i32.const 0")
                    name.data == DataName.Core.Set && name.constructor == "emptySet" -> out.line("i32.const 0")
                    else -> genClosureRef(gen.watId(name), emptyList(), ctx, out)
                }
            is FuncName.User -> genClosureRef(gen.watId(name), emptyList(), ctx, out)
        }
    }

    // ── Применения функций ──────────────────────────────────────────────────

    private fun genApplication(
        expr: Expr.Application,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        // Распознаём полностью применённый конструктор.
        val (ctor, ctorArgs) = unwrapCtorApp(expr)
        if (ctor != null && expr.type !is Type.Arrow) {
            genConstructorCall(ctor, ctorArgs, ctx, out)
            return
        }

        when {
            // Core `+`: аргумент — объект Tuple(Num, Num) в куче.
            expr.left is Expr.Identifier &&
                (expr.left as Expr.Identifier).name == FuncName.Core("+") -> {
                val tmp = ctx.freshTmp()
                genExpr(expr.right, ctx, out)
                out.line("local.tee $tmp")
                out.line("i32.load offset=0") // fst
                out.line("local.get $tmp")
                out.line("i32.load offset=4") // snd
                out.line("i32.add")
            }

            // Прямой вызов пользовательской функции.
            expr.left is Expr.Identifier &&
                (expr.left as Expr.Identifier).name is FuncName.User -> {
                genExpr(expr.right, ctx, out)
                out.line("call ${gen.watId((expr.left as Expr.Identifier).name)}")
            }

            // Общий случай: вычисляем левую часть до указателя на замыкание
            // и применяем его.
            else -> {
                genExpr(expr.left, ctx, out)
                genExpr(expr.right, ctx, out)
                out.line("call \$rt.apply")
            }
        }
    }

    /**
     * Разворачивает левый «хребет» цепочки применений до корня и проверяет,
     * не конструктор ли в корне.
     * Возвращает `(ctor, [arg₀, arg₁, …])` либо `(null, [])`, если корень — не конструктор.
     */
    private fun unwrapCtorApp(expr: Expr): Pair<FuncName.Constructor?, List<Expr>> {
        val args = mutableListOf<Expr>()
        var cur: Expr = expr
        while (cur is Expr.Application) {
            args.add(0, cur.right)
            cur = cur.left
        }
        return if (cur is Expr.Identifier && cur.name is FuncName.Constructor) {
            (cur.name as FuncName.Constructor) to args
        } else {
            null to emptyList()
        }
    }

    private fun genConstructorCall(
        ctor: FuncName.Constructor,
        args: List<Expr>,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        when {
            ctor.data == DataName.Core.TruVal ->
                out.line("i32.const ${if (ctor.constructor == "true") 1 else 0}")

            ctor.data == DataName.Core.List && ctor.constructor == "nil" ->
                out.line("i32.const 0")

            ctor.data == DataName.Core.List && ctor.constructor == "cons" && args.size == 1 -> {
                genExpr(args[0], ctx, out)
                out.line("call \$rt.mk_cons")
            }

            ctor.data == DataName.Core.Tuple && args.size == 2 -> {
                genExpr(args[0], ctx, out)
                genExpr(args[1], ctx, out)
                out.line("call \$rt.mk_tuple")
            }

            // Пустое множество — нулевой указатель.
            // TODO: Добавление элементов делается через рантайм-помощник `$rt.set_insert` (вызывается
            // из пользовательских функций, когда они появятся в Signature.core).
            ctor.data == DataName.Core.Set && ctor.constructor == "emptySet" ->
                out.line("i32.const 0")

            else -> {
                val tag = gen.constructorTags[ctor.data to ctor.constructor] ?: 0
                val tmp = ctx.freshTmp()
                out.line("i32.const ${args.size}")
                out.line("i32.const $tag")
                out.line("call \$rt.mk_adt")
                out.line("local.set $tmp")
                for ((i, argExpr) in args.withIndex()) {
                    out.line("local.get $tmp")
                    genExpr(argExpr, ctx, out)
                    out.line("i32.store offset=${4 + i * 4}")
                }
                out.line("local.get $tmp")
            }
        }
    }

    // ── Строковый литерал ───────────────────────────────────────────────────

    private fun genString(
        value: String,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        val tmpList = ctx.freshTmp()
        val tmpTuple = ctx.freshTmp()
        out.line("i32.const 0")
        out.line("local.set $tmpList")
        for (c in value.reversed()) {
            out.line("i32.const ${c.code}")
            out.line("local.get $tmpList")
            out.line("call \$rt.mk_tuple")
            out.line("local.tee $tmpTuple")
            out.line("call \$rt.mk_cons")
            out.line("local.set $tmpList")
        }
        out.line("local.get $tmpList")
    }

    // ── Лямбда как значение → поднятая функция + объект-замыкание ────────────

    private fun genLambdaClosure(
        lambda: Expr.Lambda,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        val freeVars = computeFreeVars(lambda)
        val name = "\$lifted_${gen.nextLiftedId()}"
        gen.addLiftedLambda(WatGenerator.LiftedLambda(name, freeVars, lambda))
        genClosureRef(name, freeVars, ctx, out)
    }

    /**
     * Выделяет в куче объект-замыкание `{func_table_idx: i32, n_caps: i32, cap₀: i32, …}`
     * и оставляет указатель на него на стеке операндов.
     */
    internal fun genClosureRef(
        watFuncName: String,
        captureNames: List<String>,
        ctx: WatFunctionContext,
        out: WatEmitter,
    ) {
        val idx = gen.registerInFuncTable(watFuncName)
        val nCaps = captureNames.size
        val tmp = ctx.freshTmp()
        out.line("i32.const $idx")
        out.line("i32.const $nCaps")
        out.line("call \$rt.mk_closure")
        out.line("local.set $tmp")
        for ((i, cap) in captureNames.withIndex()) {
            out.line("local.get $tmp")
            out.line("local.get ${ctx.getOrAdd(cap)}")
            out.line("i32.store offset=${8 + i * 4}")
        }
        out.line("local.get $tmp")
    }

    // ── Анализ свободных переменных ─────────────────────────────────────────

    private fun computeFreeVars(lambda: Expr.Lambda): List<String> {
        val bound = mutableSetOf<String>()
        val free = linkedSetOf<String>()

        fun pat(p: Pattern) {
            when (p) {
                is Pattern.Variable -> bound.add(p.name)
                is Pattern.NamedData -> {
                    bound.add(p.name)
                    pat(p.data)
                }
                is Pattern.Data -> p.args.forEach(::pat)
                is Pattern.Wildcard -> {}
            }
        }

        fun expr(e: Expr) {
            when (e) {
                is Expr.Variable -> if (e.name !in bound) free.add(e.name)
                is Expr.Application -> {
                    expr(e.left)
                    expr(e.right)
                }
                is Expr.If -> {
                    expr(e.condition)
                    expr(e.positive)
                    expr(e.negative)
                }
                is Expr.Let -> {
                    expr(e.matcher)
                    val saved = bound.toMutableSet()
                    pat(e.pattern)
                    expr(e.body)
                    bound.clear()
                    bound.addAll(saved)
                }
                is Expr.Lambda -> {
                    for (b in e.branches) {
                        val saved = bound.toMutableSet()
                        pat(b.pattern)
                        expr(b.body)
                        bound.clear()
                        bound.addAll(saved)
                    }
                }
                else -> {}
            }
        }

        for (b in lambda.branches) {
            val saved = bound.toMutableSet()
            pat(b.pattern)
            expr(b.body)
            bound.clear()
            bound.addAll(saved)
        }
        return free.toList()
    }
}
