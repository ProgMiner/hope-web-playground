package ru.hopec.codegen

import ru.hopec.codegen.runtime.WatImports
import ru.hopec.desugarer.DesugaredRepresentation.Type
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.typecheck.TypedRepresentation.Pattern
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name as DataName
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

internal class WatCodeEmitter(
    private val gen: WatGenerator,
) {
    internal companion object {
        val CORE_BIN_OPS: Map<String, String> =
            mapOf(
                "+" to "i32.add",
                "-" to "i32.sub",
                "*" to "i32.mul",
                "div" to "i32.div_s",
                "mod" to "i32.rem_s",
                "<" to "i32.lt_s",
                "<=" to "i32.le_s",
                ">" to "i32.gt_s",
                ">=" to "i32.ge_s",
                "=" to "i32.eq",
            )
    }

    fun emitBranchMatch(
        branches: List<Expr.Lambda.Branch>,
        argLocal: String,
        ctx: WatFunctionContext,
        selfLoopLabel: String? = null,
        selfFunc: FuncName.User? = null,
    ): SExpr {
        if (branches.isEmpty()) return unreachable()

        val matchEnd = gen.freshLabel("match_end")
        val blocks = mutableListOf<SExpr>()
        for (branch in branches) {
            val skip = gen.freshLabel("skip")
            ctx.pushScope()
            val stmts = emitPatternCheck(branch.pattern, argLocal, skip, ctx)
            val tailArg = selfFunc?.let { unwrapSelfTailArg(branch.body, it) }
            val branchEnd =
                if (tailArg != null && selfLoopLabel != null) {
                    listOf(localSet(argLocal, genExpr(tailArg, ctx)), br(selfLoopLabel))
                } else {
                    listOf(brValue(matchEnd, genExpr(branch.body, ctx)))
                }
            ctx.popScope()
            blocks.add(block(skip, null, stmts + branchEnd))
        }
        blocks.add(unreachable())
        return block(matchEnd, "i32", blocks)
    }

    private fun unwrapSelfTailArg(
        expr: Expr,
        self: FuncName.User,
    ): Expr? {
        if (expr !is Expr.Application) return null
        val callee = expr.left as? Expr.Identifier ?: return null
        val name = callee.name as? FuncName.User ?: return null
        if (name != self) return null
        return expr.right
    }

    fun emitPatternCheck(
        pattern: Pattern,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
    ): List<SExpr> =
        when (pattern) {
            is Pattern.Wildcard -> emptyList()

            is Pattern.Variable ->
                listOf(localSet(ctx.bind(pattern.name), localGet(argLocal)))

            is Pattern.NamedData ->
                listOf(localSet(ctx.bind(pattern.name), localGet(argLocal))) +
                    emitPatternCheck(pattern.data, argLocal, failLabel, ctx)

            is Pattern.Data -> {
                emitDataCheck(pattern, argLocal, failLabel, ctx)
            }

            is Expr.Literal.Num ->
                listOf(brIf(failLabel, i32Ne(localGet(argLocal), i32Const(pattern.value.toInt()))))

            is Expr.Literal.Char ->
                listOf(brIf(failLabel, i32Ne(localGet(argLocal), i32Const(pattern.value.code))))

            is Expr.Literal.TruVal ->
                listOf(brIf(failLabel, i32Ne(localGet(argLocal), i32Const(if (pattern.value) 1 else 0))))

            is Expr.Literal.String ->
                emitStringPatternCheck(pattern.value, argLocal, failLabel, ctx)
        }

    private fun emitStringPatternCheck(
        value: String,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
    ): List<SExpr> {
        val cell = ctx.freshTmp()
        val tup = ctx.freshTmp()
        val stmts = mutableListOf<SExpr>()
        stmts.add(localSet(cell, localGet(argLocal)))
        for (c in value) {
            stmts.add(brIf(failLabel, i32Eqz(localGet(cell))))
            stmts.add(localSet(tup, i32Load(0, localGet(cell))))
            stmts.add(brIf(failLabel, i32Ne(i32Load(0, localGet(tup)), i32Const(c.code))))
            stmts.add(localSet(cell, i32Load(4, localGet(tup))))
        }
        stmts.add(brIf(failLabel, localGet(cell)))
        return stmts
    }

    private fun emitDataCheck(
        pattern: Pattern.Data,
        argLocal: String,
        failLabel: String,
        ctx: WatFunctionContext,
    ): List<SExpr> {
        val ctor = pattern.constructor
        return when {
            ctor.data == DataName.Core.TruVal -> {
                val expected = if (ctor.constructor == "true") 1 else 0
                listOf(brIf(failLabel, i32Ne(localGet(argLocal), i32Const(expected))))
            }

            ctor.data == DataName.Core.List && ctor.constructor == "nil" ->
                listOf(brIf(failLabel, localGet(argLocal)))

            ctor.data == DataName.Core.Set && ctor.constructor == "emptySet" ->
                listOf(brIf(failLabel, localGet(argLocal)))

            ctor.data == DataName.Core.List && ctor.constructor == "cons" -> {
                val stmts = mutableListOf<SExpr>()
                stmts.add(brIf(failLabel, i32Eqz(localGet(argLocal))))
                if (pattern.args.isNotEmpty()) {
                    val tmp = ctx.freshTmp()
                    stmts.add(localSet(tmp, i32Load(0, localGet(argLocal))))
                    stmts.addAll(emitPatternCheck(pattern.args[0], tmp, failLabel, ctx))
                }
                stmts
            }

            ctor.data == DataName.Core.Set && ctor.constructor == "setCons" -> {
                val stmts = mutableListOf<SExpr>()
                stmts.add(brIf(failLabel, i32Eqz(localGet(argLocal))))
                if (pattern.args.isNotEmpty()) {
                    stmts.addAll(emitPatternCheck(pattern.args[0], argLocal, failLabel, ctx))
                }
                stmts
            }

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

    fun genExpr(
        expr: Expr,
        ctx: WatFunctionContext,
    ): SExpr =
        when (expr) {
            is Expr.Literal.Num -> i32Const(expr.value.toInt())
            is Expr.Literal.TruVal -> i32Const(if (expr.value) 1 else 0)
            is Expr.Literal.Char -> i32Const(expr.value.code)
            is Expr.Literal.String -> genString(expr.value, ctx)
            is Expr.Variable ->
                localGet(
                    ctx.lookup(expr.name)
                        ?: error("Unbound variable '${expr.name}'"),
                )
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
        val letFail = gen.freshLabel("let_fail")
        val letOk = gen.freshLabel("let_ok")
        ctx.pushScope()
        val patternStmts = emitPatternCheck(expr.pattern, tmp, letFail, ctx)
        val body = genExpr(expr.body, ctx)
        ctx.popScope()
        return resultBlock(
            stmts =
                listOf(
                    localSet(tmp, matcher),
                    block(
                        letOk,
                        null,
                        listOf(
                            block(letFail, null, patternStmts + br(letOk)),
                            unreachable(),
                        ),
                    ),
                ),
            value = body,
        )
    }

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
                    "io.getChar" -> call(WatImports.importId(name), emptyList())
                    else -> genClosureRef(gen.wrapperFor(name), emptyList(), ctx)
                }
            }

            is FuncName.Constructor -> {
                when {
                    name.data == DataName.Core.TruVal && name.constructor == "true" -> i32Const(1)
                    name.data == DataName.Core.TruVal && name.constructor == "false" -> i32Const(0)
                    name.data == DataName.Core.List && name.constructor == "nil" -> i32Const(0)
                    name.data == DataName.Core.Set && name.constructor == "emptySet" -> i32Const(0)

                    expr.type !is Type.Arrow -> {
                        val tag = gen.constructorTags[name.data to name.constructor] ?: 0
                        call("\$rt.mk_adt", listOf(i32Const(0), i32Const(tag)))
                    }

                    else -> genClosureRef(gen.wrapperFor(name), emptyList(), ctx)
                }
            }

            is FuncName.User -> {
                if (expr.type !is Type.Arrow) {
                    call(gen.watId(name), listOf(i32Const(0)))
                } else {
                    genClosureRef(gen.wrapperFor(name), emptyList(), ctx)
                }
            }
        }

    private fun genApplication(
        expr: Expr.Application,
        ctx: WatFunctionContext,
    ): SExpr {
        val (ctor, ctorArgs) = unwrapCtorApp(expr)
        if (ctor != null && expr.type !is Type.Arrow) {
            return genConstructorCall(ctor, ctorArgs, ctx)
        }

        val leftName = (expr.left as? Expr.Identifier)?.name
        val coreBinOp = (leftName as? FuncName.Core)?.let { CORE_BIN_OPS[it.name] }

        return when {
            coreBinOp != null -> {
                val tmp = ctx.freshTmp()
                val arg = genExpr(expr.right, ctx)
                inst(
                    coreBinOp,
                    i32Load(0, localTee(tmp, arg)),
                    i32Load(4, localGet(tmp)),
                )
            }

            leftName is FuncName.Core && leftName.name == "io.print" -> {
                val arg = genExpr(expr.right, ctx)
                resultBlock(
                    listOf(call(WatImports.importId(leftName), listOf(arg))),
                    i32Const(0),
                )
            }

            leftName is FuncName.User -> {
                val arg = genExpr(expr.right, ctx)
                call(gen.watId(leftName), listOf(arg))
            }

            else -> {
                val left = genExpr(expr.left, ctx)
                val right = genExpr(expr.right, ctx)
                call("\$rt.apply", listOf(left, right))
            }
        }
    }

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
                i32Const(if (ctor.constructor == "true") 1 else 0)
            }

            ctor.data == DataName.Core.List && ctor.constructor == "nil" -> {
                i32Const(0)
            }

            ctor.data == DataName.Core.List && ctor.constructor == "cons" && args.size == 1 ->
                call("\$rt.mk_cons", listOf(genExpr(args[0], ctx)))

            ctor.data == DataName.Core.Tuple && args.size == 2 ->
                call("\$rt.mk_tuple", listOf(genExpr(args[0], ctx), genExpr(args[1], ctx)))

            ctor.data == DataName.Core.Set && ctor.constructor == "emptySet" -> {
                i32Const(0)
            }

            ctor.data == DataName.Core.Set && ctor.constructor == "setCons" && args.size == 1 -> {
                val tup = ctx.freshTmp()
                resultBlock(
                    stmts = listOf(localSet(tup, genExpr(args[0], ctx))),
                    value =
                        call(
                            "\$rt.set_insert",
                            listOf(
                                i32Load(4, localGet(tup)),
                                i32Load(0, localGet(tup)),
                            ),
                        ),
                )
            }

            else -> {
                val tag = gen.constructorTags[ctor.data to ctor.constructor] ?: 0
                if (args.isEmpty()) {
                    call("\$rt.mk_adt", listOf(i32Const(0), i32Const(tag)))
                } else {
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
        }

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

    private fun genLambdaClosure(
        lambda: Expr.Lambda,
        ctx: WatFunctionContext,
    ): SExpr {
        val freeVars = computeFreeVars(lambda)
        val name = "\$lifted_${gen.nextLiftedId()}"
        gen.addLiftedLambda(WatGenerator.LiftedLambda(name, freeVars, lambda))
        return genClosureRef(name, freeVars, ctx)
    }

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
            val local =
                ctx.lookup(cap)
                    ?: error("Unbound capture '$cap'")
            stmts.add(i32Store(8 + i * 4, localGet(tmp), localGet(local)))
        }
        return resultBlock(stmts, localGet(tmp))
    }

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

                else -> {}
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
