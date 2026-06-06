package ru.hopec.codegen

import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name as DataName
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

/**
 * Генерирует свёрнутые (folded) WAT-выражения [SExpr] для выражений и веток
 * сопоставления с паттернами. Всё изменяемое состояние генерации (метки,
 * поднятые лямбды, таблица функций) хранится в [gen] — так обе половины
 * остаются согласованы.
 *
 * Контракт:
 * - [genExpr] возвращает одно [SExpr], оставляющее результат типа `i32` на
 *   стеке операндов.
 * - [emitPatternCheck] возвращает список инструкций-«стейтментов»: проверки
 *   паттерна (`br_if` на провал) и привязки переменных (`local.set`).
 * - [emitBranchMatch] собирает блок сопоставления целиком.
 */
internal class WatCodeEmitter(
    private val gen: WatGenerator,
) {
    // ═══════════════════════════════════════════════════════════════════════
    // Ветвление / сопоставление с паттернами
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Строит многоветочное сопоставление с паттернами.
     *
     * ```wat
     * (block $match_end (result i32)
     *   (block $skip0 <проверки…> (br $match_end <тело0>))
     *   (block $skip1 …)
     *   (unreachable))
     * ```
     *
     * Каждая ветка обёрнута в `(block $skip …)`: неуспешная проверка паттерна
     * делает `br_if $skip`, пропуская тело. Успешная проверка выполняет тело
     * и `br $match_end` со значением-результатом.
     */
    fun emitBranchMatch(
        branches: List<Expr.Lambda.Branch>,
        argLocal: String,
        ctx: WatFunctionContext,
    ): SExpr {
        if (branches.isEmpty()) return unreachable()

        val matchEnd = gen.freshLabel("match_end")
        val blocks = mutableListOf<SExpr>()
        for (branch in branches) {
            val skip = gen.freshLabel("skip")
            val stmts = emitPatternCheck(branch.pattern, argLocal, skip, ctx)
            val body = genExpr(branch.body, ctx)
            blocks.add(block(skip, null, stmts + brValue(matchEnd, body)))
        }
        blocks.add(unreachable())
        return block(matchEnd, "i32", blocks)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Проверка паттернов
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Возвращает инструкции-проверки паттерна [pattern] для значения в
     * WAT-локале [argLocal]. При несовпадении эмиттируется
     * `(br_if $failLabel …)`; при совпадении — `local.set` для связанных
     * переменных, и управление проваливается дальше.
     */
    fun emitPatternCheck(
        pattern: Pattern,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
    ): List<SExpr> =
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

            is Pattern.Data -> {
                emitDataCheck(pattern, argLocal, failLabel, ctx, out)
            }

            else -> {
                TODO("Add support for literal patterns")
            }
        }

    private fun emitDataCheck(
        pattern: Pattern.Data,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
    ): List<SExpr> {
        val ctor = pattern.constructor
        return when {
            // ── TruVal ──────────────────────────────────────────────────────
            ctor.data == DataName.Core.TruVal -> {
                val expected = if (ctor.constructor == "true") 1 else 0
                listOf(brIf(failLabel, i32Ne(localGet(argLocal), i32Const(expected))))
            }

            // ── nil: указатель на кучу == 0 ─────────────────────────────────
            ctor.data == DataName.Core.List && ctor.constructor == "nil" ->
                listOf(brIf(failLabel, localGet(argLocal))) // ненулевой = cons = не совпадает

            // ── emptySet: указатель на кучу == 0 (как и nil) ────────────────
            ctor.data == DataName.Core.Set && ctor.constructor == "emptySet" ->
                listOf(brIf(failLabel, localGet(argLocal))) // ненулевой = непустое = не совпадает

            // ── cons: хранит одно поле (указатель на кортеж (head, tail)) ───
            ctor.data == DataName.Core.List && ctor.constructor == "cons" -> {
                val stmts = mutableListOf<SExpr>()
                stmts.add(brIf(failLabel, i32Eqz(localGet(argLocal)))) // ноль = nil = не совпадает
                if (pattern.args.isNotEmpty()) {
                    val tmp = ctx.freshTmp()
                    stmts.add(localSet(tmp, i32Load(0, localGet(argLocal))))
                    stmts.addAll(emitPatternCheck(pattern.args[0], tmp, failLabel, ctx))
                }
                stmts
            }

            // ── Tuple # (fst, snd): объект в куче {fst: i32, snd: i32} ──────
            ctor.data == DataName.Core.Tuple -> {
                val stmts = mutableListOf<SExpr>()
                if (pattern.args.isNotEmpty()) {
                    val t0 = ctx.freshTmp()
                    stmts.add(localSet(t0, i32Load(0, localGet(argLocal))))
                    stmts.addAll(emitPatternCheck(pattern.args[0], t0, failLabel, ctx))
                }
                if (pattern.args.size >= 2) {
                    val t1 = ctx.freshTmp()
                    stmts.add(localSet(t1, i32Load(4, localGet(argLocal))))
                    stmts.addAll(emitPatternCheck(pattern.args[1], t1, failLabel, ctx))
                }
                stmts
            }

            // ── Пользовательский ADT: {tag: i32, field₀: i32, …} ─────────────
            else -> {
                val tag = gen.constructorTags[ctor.data to ctor.constructor] ?: 0
                val stmts = mutableListOf<SExpr>()
                stmts.add(brIf(failLabel, i32Ne(i32Load(0, localGet(argLocal)), i32Const(tag))))
                for ((i, sub) in pattern.args.withIndex()) {
                    val tmp = ctx.freshTmp()
                    stmts.add(localSet(tmp, i32Load(4 + i * 4, localGet(argLocal))))
                    stmts.addAll(emitPatternCheck(sub, tmp, failLabel, ctx))
                }
                stmts
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Генерация кода для выражений
    // ═══════════════════════════════════════════════════════════════════════

    /** Возвращает свёрнутое выражение для [expr], результат типа `i32`. */
    fun genExpr(
        expr: Expr,
        ctx: WatFunctionContext,
    ): SExpr =
        when (expr) {
            is Expr.Literal.Num -> i32Const(expr.value.toInt())
            is Expr.Literal.TruVal -> i32Const(if (expr.value) 1 else 0)
            is Expr.Literal.Char -> i32Const(expr.value.code)
            is Expr.Literal.String -> genString(expr.value, ctx)
            is Expr.Variable -> localGet(ctx.getOrAdd(expr.name))
            is Expr.Identifier -> genIdentifier(expr, ctx)
            is Expr.Application -> genApplication(expr, ctx)
            is Expr.If -> genIf(expr, ctx)
            is Expr.Let -> genLet(expr, ctx)
            is Expr.Lambda -> genLambdaClosure(expr, ctx)
        }

    private fun genIf(
        expr: Expr.If,
        ctx: WatFunctionContext,
    ): SExpr {
        val cond = genExpr(expr.condition, ctx)
        val positive = genExpr(expr.positive, ctx)
        val negative = genExpr(expr.negative, ctx)
        return inst(
            "if (result i32)",
            cond,
            inst("then", positive),
            inst("else", negative),
        )
    }

    private fun genLet(
        expr: Expr.Let,
        ctx: WatFunctionContext,
    ): SExpr {
        val tmp = ctx.freshTmp()
        val matcher = genExpr(expr.matcher, ctx)
        // В корректно типизированных программах let-паттерн всегда совпадает.
        // Тем не менее, эмиттируем структуру блока — недостижимая ветка br
        // остаётся валидным WAT.
        val letFail = gen.freshLabel("let_fail")
        val patternStmts = emitPatternCheck(expr.pattern, tmp, letFail, ctx)
        val body = genExpr(expr.body, ctx)
        return resultBlock(
            stmts =
                listOf(
                    localSet(tmp, matcher),
                    block(letFail, null, patternStmts),
                ),
            value = body,
        )
    }

    // ── Идентификаторы ──────────────────────────────────────────────────────

    private fun genIdentifier(
        expr: Expr.Identifier,
        ctx: WatFunctionContext,
    ): SExpr =
        when (val name = expr.name) {
            is FuncName.Core -> {
                when (name.name) {
                    "nil" -> i32Const(0)
                    "true" -> i32Const(1)
                    "false" -> i32Const(0)
                    "emptySet" -> i32Const(0)
                    else -> genClosureRef(gen.watId(name), emptyList(), ctx)
                }
            }

            is FuncName.Constructor -> {
                when {
                    name.data == DataName.Core.TruVal && name.constructor == "true" -> i32Const(1)
                    name.data == DataName.Core.TruVal && name.constructor == "false" -> i32Const(0)
                    name.data == DataName.Core.List && name.constructor == "nil" -> i32Const(0)
                    name.data == DataName.Core.Set && name.constructor == "emptySet" -> i32Const(0)
                    else -> genClosureRef(gen.watId(name), emptyList(), ctx)
                }
            }

            is FuncName.User -> {
                genClosureRef(gen.watId(name), emptyList(), ctx, out)
            }
        }

    // ── Применения функций ──────────────────────────────────────────────────

    private fun genApplication(
        expr: Expr.Application,
        ctx: WatFunctionContext,
    ): SExpr {
        // Распознаём полностью применённый конструктор.
        val (ctor, ctorArgs) = unwrapCtorApp(expr)
        if (ctor != null && expr.type !is Type.Arrow) {
            return genConstructorCall(ctor, ctorArgs, ctx)
        }

        return when {
            // Core `+`: аргумент — объект Tuple(Num, Num) в куче.
            expr.left is Expr.Identifier &&
                (expr.left as Expr.Identifier).name == FuncName.Core("+") -> {
                val tmp = ctx.freshTmp()
                val arg = genExpr(expr.right, ctx)
                i32Add(
                    i32Load(0, localTee(tmp, arg)),
                    i32Load(4, localGet(tmp)),
                )
            }

            // Прямой вызов пользовательской функции.
            expr.left is Expr.Identifier &&
                (expr.left as Expr.Identifier).name is FuncName.User -> {
                val arg = genExpr(expr.right, ctx)
                call(gen.watId((expr.left as Expr.Identifier).name), listOf(arg))
            }

            // Общий случай: вычисляем левую часть до указателя на замыкание
            // и применяем его.
            else -> {
                val left = genExpr(expr.left, ctx)
                val right = genExpr(expr.right, ctx)
                call("\$rt.apply", listOf(left, right))
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
    ): SExpr =
        when {
            ctor.data == DataName.Core.TruVal -> {
                out.line("i32.const ${if (ctor.constructor == "true") 1 else 0}")
            }

            ctor.data == DataName.Core.List && ctor.constructor == "nil" -> {
                out.line("i32.const 0")
            }

            ctor.data == DataName.Core.List && ctor.constructor == "cons" && args.size == 1 ->
                call("\$rt.mk_cons", listOf(genExpr(args[0], ctx)))

            ctor.data == DataName.Core.Tuple && args.size == 2 ->
                call("\$rt.mk_tuple", listOf(genExpr(args[0], ctx), genExpr(args[1], ctx)))

            // Пустое множество — нулевой указатель.
            // TODO: Добавление элементов делается через рантайм-помощник `$rt.set_insert` (вызывается
            // из пользовательских функций, когда они появятся в Signature.core).
            ctor.data == DataName.Core.Set && ctor.constructor == "emptySet" -> {
                out.line("i32.const 0")
            }

            else -> {
                val tag = gen.constructorTags[ctor.data to ctor.constructor] ?: 0
                val tmp = ctx.freshTmp()
                val stmts = mutableListOf<SExpr>()
                stmts.add(
                    localSet(
                        tmp,
                        call("\$rt.mk_adt", listOf(i32Const(args.size), i32Const(tag))),
                    ),
                )
                for ((i, argExpr) in args.withIndex()) {
                    stmts.add(i32Store(4 + i * 4, localGet(tmp), genExpr(argExpr, ctx)))
                }
                resultBlock(stmts, localGet(tmp))
            }
        }

    // ── Строковый литерал ───────────────────────────────────────────────────

    private fun genString(
        value: String,
        ctx: WatFunctionContext,
    ): SExpr {
        val tmpList = ctx.freshTmp()
        val stmts = mutableListOf<SExpr>()
        stmts.add(localSet(tmpList, i32Const(0)))
        for (c in value.reversed()) {
            stmts.add(
                localSet(
                    tmpList,
                    call(
                        "\$rt.mk_cons",
                        listOf(
                            call("\$rt.mk_tuple", listOf(i32Const(c.code), localGet(tmpList))),
                        ),
                    ),
                ),
            )
        }
        return resultBlock(stmts, localGet(tmpList))
    }

    // ── Лямбда как значение → поднятая функция + объект-замыкание ────────────

    private fun genLambdaClosure(
        lambda: Expr.Lambda,
        ctx: WatFunctionContext,
    ): SExpr {
        val freeVars = computeFreeVars(lambda)
        val name = "\$lifted_${gen.nextLiftedId()}"
        gen.addLiftedLambda(WatGenerator.LiftedLambda(name, freeVars, lambda))
        return genClosureRef(name, freeVars, ctx)
    }

    /**
     * Выделяет в куче объект-замыкание `{func_table_idx: i32, n_caps: i32, cap₀: i32, …}`
     * и возвращает указатель на него.
     */
    internal fun genClosureRef(
        watFuncName: String,
        captureNames: List<String>,
        ctx: WatFunctionContext,
    ): SExpr {
        val idx = gen.registerInFuncTable(watFuncName)
        val nCaps = captureNames.size
        val mk = call("\$rt.mk_closure", listOf(i32Const(idx), i32Const(nCaps)))
        if (captureNames.isEmpty()) return mk

        val tmp = ctx.freshTmp()
        val stmts = mutableListOf<SExpr>()
        stmts.add(localSet(tmp, mk))
        for ((i, cap) in captureNames.withIndex()) {
            stmts.add(i32Store(8 + i * 4, localGet(tmp), localGet(ctx.getOrAdd(cap))))
        }
        return resultBlock(stmts, localGet(tmp))
    }

    // ── Анализ свободных переменных ─────────────────────────────────────────

    private fun computeFreeVars(lambda: Expr.Lambda): List<String> {
        val bound = mutableSetOf<String>()
        val free = linkedSetOf<String>()

        fun pat(p: Pattern) {
            when (p) {
                is Pattern.Variable -> {
                    bound.add(p.name)
                }

                is Pattern.NamedData -> {
                    bound.add(p.name)
                    pat(p.data)
                }

                is Pattern.Data -> {
                    p.args.forEach(::pat)
                }

                is Pattern.Wildcard -> {}

                else -> {
                    TODO("add support for literal patterns")
                }
            }
        }

        fun expr(e: Expr) {
            when (e) {
                is Expr.Variable -> {
                    if (e.name !in bound) free.add(e.name)
                }

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
