package ru.hopec.codegen

import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.typecheck.TypedRepresentation.Declarations.Data.Name as DataName
import ru.hopec.typecheck.TypedRepresentation.Declarations.Function.Name as FuncName

/**
 * Переводит [TypedRepresentation] в WebAssembly Text Format (WAT).
 *
 * ## Представление в памяти
 * Любое значение HOPE — это `i32`.
 *
 * | Тип HOPE                  | Значение в WAT                                        |
 * |---------------------------|-------------------------------------------------------|
 * | `Num`                     | распакованный `i32` (младшие 32 бита `Long`)          |
 * | `Char`                    | распакованный `i32` (код Unicode)                     |
 * | `TruVal`                  | распакованный `i32`: 0 = false, 1 = true              |
 * | `nil`                     | `i32` == 0 (нулевой указатель)                        |
 * | `cons(arg)`               | указатель на кучу → `[field: i32]`                    |
 * | `Tuple # (a, b)`          | указатель на кучу → `[fst: i32, snd: i32]`            |
 * | `emptySet`                | `i32` == 0 (нулевой указатель)                        |
 * | `set a` (непустое)        | указатель на кучу → `[value: i32, next: i32]`         |
 * | пользовательский ADT ctor | указатель на кучу → `[tag: i32, field₀: i32, …]`      |
 * | замыкание `a → b`         | указатель на кучу → `[func_idx: i32, n_caps: i32, …]` |
 *
 * Генерация кода выражений и сопоставления с паттернами вынесена в [WatCodeEmitter].
 */
class WatGenerator(
    private val program: TypedRepresentation,
) {
    // ── Эмиттер вывода ──────────────────────────────────────────────────────
    private val out = WatEmitter()

    // ── Счётчики ────────────────────────────────────────────────────────────
    private var labelCounter = 0
    private var liftedCounter = 0

    // ── Теги конструкторов: (DataName × ctorName) → тег в рамках типа ───────
    internal val constructorTags = mutableMapOf<Pair<DataName, String>, Int>()

    // ── Таблица функций для косвенных вызовов (замыкания) ────────────────────
    private val funcTable = mutableListOf<String>()
    private val funcTableIdx = mutableMapOf<String, Int>()

    // ── Лямбды, поднятые в область модуля ────────────────────────────────────
    internal data class LiftedLambda(
        val watName: String,
        val captures: List<String>,
        val lambda: Expr.Lambda,
    )

    private val liftedLambdas = mutableListOf<LiftedLambda>()

    // ── Делегат для кода выражений / паттернов ───────────────────────────────
    private val code = WatCodeEmitter(this)

    // ═══════════════════════════════════════════════════════════════════════
    // API, доступное WatCodeEmitter
    // ═══════════════════════════════════════════════════════════════════════

    internal fun freshLabel(prefix: String = "lbl") = "\$$prefix${labelCounter++}"

    internal fun nextLiftedId() = liftedCounter++

    internal fun addLiftedLambda(lifted: LiftedLambda) {
        liftedLambdas.add(lifted)
    }

    internal fun registerInFuncTable(watName: String): Int =
        funcTableIdx.getOrPut(watName) {
            funcTable.add(watName)
            funcTable.size - 1
        }

    internal fun watId(name: FuncName): String =
        when (name) {
            is FuncName.Core -> "\$core.${esc(name.name)}"
            is FuncName.User -> "\$fn.${name.module ?: "top"}.${esc(name.name)}"
            is FuncName.Constructor -> "\$ctor.${dataStr(name.data)}.${esc(name.constructor)}"
        }

    internal fun esc(s: String): String =
        s
            .replace("+", "_plus")
            .replace("-", "_minus")
            .replace("*", "_mul")
            .replace("/", "_div")
            .replace("#", "hash")
            .replace(".", "_dot")
            .replace(" ", "_")
            .filter { it.isLetterOrDigit() || it == '_' }

    private fun dataStr(name: DataName): String =
        when (name) {
            DataName.Core.Char -> "Char"
            DataName.Core.TruVal -> "TruVal"
            DataName.Core.Num -> "Num"
            DataName.Core.List -> "List"
            DataName.Core.Set -> "Set"
            DataName.Core.Tuple -> "Tuple"
            is DataName.Defined -> "${name.module ?: "top"}.${name.name}"
        }

    // ═══════════════════════════════════════════════════════════════════════
    // Точка входа
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
    // Назначение тегов конструкторам
    // ═══════════════════════════════════════════════════════════════════════

    private fun assignConstructorTags() {
        fun process(
            name: DataName,
            data: Data,
        ) {
            var tag = 0
            for ((ctor, _) in data.constructors) constructorTags[name to ctor] = tag++
        }
        for ((name, data) in program.topLevel.data) process(name, data)
        for ((_, module) in program.modules) {
            for ((name, data) in module.public.data) process(name, data)
            for ((name, data) in module.private.data) process(name, data)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Память, глобальные переменные, рантайм
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
        emitSetContains()
        emitSetInsert()
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

    /**
     * `$rt.set_contains(set, value) -> i32`
     *
     * Линейный поиск значения [value] в [set]. Возвращает 1, если найдено,
     * 0 — иначе. Сравнение по [i32.eq]: подходит для распакованных значений
     * (`Num`, `Char`, `TruVal`, `nil`) и для ссылочного равенства указателей.
     */
    private fun emitSetContains() {
        out.line("(func \$rt.set_contains (param \$set i32) (param \$value i32) (result i32)")
        out.indent {
            out.line("(local \$cur i32)")
            out.line("local.get \$set")
            out.line("local.set \$cur")
            out.line("(block \$done (result i32)")
            out.indent {
                out.line("(loop \$walk")
                out.indent {
                    // Если cur == 0, значение не найдено.
                    out.line("local.get \$cur")
                    out.line("i32.eqz")
                    out.line("(if")
                    out.indent {
                        out.line("(then i32.const 0 br \$done)")
                    }
                    out.line(")")
                    // Если *cur == value, нашли.
                    out.line("local.get \$cur")
                    out.line("i32.load offset=0")
                    out.line("local.get \$value")
                    out.line("i32.eq")
                    out.line("(if")
                    out.indent {
                        out.line("(then i32.const 1 br \$done)")
                    }
                    out.line(")")
                    // cur = cur->next, продолжаем цикл.
                    out.line("local.get \$cur")
                    out.line("i32.load offset=4")
                    out.line("local.set \$cur")
                    out.line("br \$walk")
                }
                out.line(")")
                out.line("unreachable")
            }
            out.line(")")
        }
        out.line(")")
    }

    /**
     * `$rt.set_insert(set, value) -> set`
     *
     * Если [value] уже есть в [set], возвращает исходный [set] без изменений.
     * Иначе аллоцирует новую ячейку `[value, set]` и возвращает указатель
     * на неё (prepend). Среднее время: O(n). Дубликаты не создаются.
     */
    private fun emitSetInsert() {
        out.line("(func \$rt.set_insert (param \$set i32) (param \$value i32) (result i32)")
        out.indent {
            out.line("(local \$cell i32)")
            // Если value уже в множестве — возвращаем сам set.
            out.line("local.get \$set")
            out.line("local.get \$value")
            out.line("call \$rt.set_contains")
            out.line("(if (result i32)")
            out.indent {
                out.line("(then local.get \$set)")
                out.line("(else")
                out.indent {
                    // Аллоцируем 8 байт: [value, next=set].
                    out.line("i32.const 8")
                    out.line("call \$rt.malloc")
                    out.line("local.tee \$cell")
                    out.line("local.get \$value")
                    out.line("i32.store offset=0")
                    out.line("local.get \$cell")
                    out.line("local.get \$set")
                    out.line("i32.store offset=4")
                    out.line("local.get \$cell")
                }
                out.line(")")
            }
            out.line(")")
        }
        out.line(")")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Эмиссия функций
    // ═══════════════════════════════════════════════════════════════════════

    private fun emitAllFunctions() {
        emitDeclFunctions(program.topLevel)
        for ((_, module) in program.modules) {
            emitDeclFunctions(module.public)
            emitDeclFunctions(module.private)
        }
        // Список поднятых лямбд может расти в процессе эмиссии (лямбды внутри лямбд).
        var i = 0
        while (i < liftedLambdas.size) {
            emitLiftedLambda(liftedLambdas[i++])
        }
    }

    private fun emitDeclFunctions(decls: Declarations) {
        for ((name, func) in decls.functions) emitFunction(watId(name), func.lambda)
    }

    /** Эмиттирует функцию верхнего уровня с одним аргументом типа `i32`. */
    private fun emitFunction(
        watName: String,
        lambda: Expr.Lambda,
    ) {
        val ctx = WatFunctionContext(::esc)
        collectLambdaVars(lambda, ctx)

        // Сначала собираем тело во вспомогательный эмиттер, чтобы знать набор
        // временных переменных до момента вывода объявлений локалов.
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
     * Поднятая лямбда принимает `(closure_ptr: i32, arg: i32) → i32`,
     * чтобы её можно было вызвать через `$rt.apply`. Захваченные переменные
     * загружаются из `closure_ptr` по смещениям 8, 12, 16, … (после
     * func_idx[4] и n_caps[4]).
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
    // Предварительный обход: собираем имена пользовательских переменных
    // из паттернов и let-выражений
    // ═══════════════════════════════════════════════════════════════════════

    private fun collectLambdaVars(
        lambda: Expr.Lambda,
        ctx: WatFunctionContext,
    ) {
        for (b in lambda.branches) {
            collectPatVars(b.pattern, ctx)
            collectExprVars(b.body, ctx)
        }
    }

    private fun collectPatVars(
        p: Pattern,
        ctx: WatFunctionContext,
    ) {
        when (p) {
            is Pattern.Variable -> ctx.getOrAdd(p.name)
            is Pattern.NamedData -> {
                ctx.getOrAdd(p.name)
                collectPatVars(p.data, ctx)
            }
            is Pattern.Data -> p.args.forEach { collectPatVars(it, ctx) }
            is Pattern.Wildcard -> {}
        }
    }

    private fun collectExprVars(
        e: Expr,
        ctx: WatFunctionContext,
    ) {
        when (e) {
            is Expr.Let -> {
                collectPatVars(e.pattern, ctx)
                collectExprVars(e.matcher, ctx)
                collectExprVars(e.body, ctx)
            }
            is Expr.Lambda ->
                e.branches.forEach {
                    collectPatVars(it.pattern, ctx)
                    collectExprVars(it.body, ctx)
                }
            is Expr.Application -> {
                collectExprVars(e.left, ctx)
                collectExprVars(e.right, ctx)
            }
            is Expr.If -> {
                collectExprVars(e.condition, ctx)
                collectExprVars(e.positive, ctx)
                collectExprVars(e.negative, ctx)
            }
            else -> {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Экспорты
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
    // Таблица функций
    // ═══════════════════════════════════════════════════════════════════════

    private fun emitFunctionTable() {
        if (funcTable.isEmpty()) return
        out.line("(table (export \"table\") ${funcTable.size} funcref)")
        out.line("(elem (i32.const 0) ${funcTable.joinToString(" ")})")
    }
}
