package ru.hopec.codegen

import ru.hopec.codegen.runtime.WatRuntime
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Data.Name as DataName
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

class WatGenerator(
    private val program: TypedRepresentation,
) {
    private val moduleChildren = mutableListOf<SExpr>()

    private var labelCounter = 0
    private var liftedCounter = 0

    internal val constructorTags = mutableMapOf<Pair<DataName, String>, Int>()
    internal val constructorArity = mutableMapOf<Pair<DataName, String>, Int>()

    private val funcTable = mutableListOf<String>()
    private val funcTableIdx = mutableMapOf<String, Int>()

    private val wrapperIds = mutableMapOf<FuncName, String>()
    private val pendingWrappers = ArrayDeque<Pair<FuncName, String>>()

    internal data class LiftedLambda(
        val watName: String,
        val captures: List<String>,
        val lambda: Expr.Lambda,
    )

    private val liftedLambdas = mutableListOf<LiftedLambda>()

    private val code = WatCodeEmitter(this)


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

    internal fun wrapperFor(name: FuncName): String =
        wrapperIds.getOrPut(name) {
            val id = "\$wrap.${watId(name).removePrefix("\$")}"
            pendingWrappers.addLast(name to id)
            id
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
            .replace("<", "_lt")
            .replace(">", "_gt")
            .replace("=", "_eq")
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


    fun generate(): String {
        assignConstructorTags()
        emitMemoryAndGlobals()
        val tableInsertIndex = moduleChildren.size
        emitRuntime()
        emitAllFunctions()
        emitFunctionElem()
        emitExports()
        moduleChildren.add(
            tableInsertIndex,
            SExpr.Raw("(table (export \"table\") ${funcTable.size} funcref)"),
        )
        return SExpr.Inst("module", moduleChildren.toList()).format()
    }


    private fun assignConstructorTags() {
        fun process(
            name: DataName,
            data: Data,
        ) {
            var tag = 0
            for ((ctor, args) in data.constructors) {
                constructorTags[name to ctor] = tag++
                constructorArity[name to ctor] = args.size
            }
        }
        for ((name, data) in program.topLevel.data) process(name, data)
        for ((_, module) in program.modules) {
            for ((name, data) in module.public.data) process(name, data)
            for ((name, data) in module.private.data) process(name, data)
        }
    }


    private fun emitMemoryAndGlobals() {
        moduleChildren.add(SExpr.Raw("(memory (export \"memory\") 1)"))
        moduleChildren.add(SExpr.Raw("(global \$heap_ptr (mut i32) (i32.const 4096))"))
    }

    private fun emitRuntime() {
        for (snippet in WatRuntime.ALL) moduleChildren.add(SExpr.Raw(snippet))
    }


    private fun emitAllFunctions() {
        emitDeclFunctions(program.topLevel)
        for ((_, module) in program.modules) {
            emitDeclFunctions(module.public)
            emitDeclFunctions(module.private)
        }
        var lifted = 0
        while (lifted < liftedLambdas.size || pendingWrappers.isNotEmpty()) {
            while (lifted < liftedLambdas.size) {
                emitLiftedLambda(liftedLambdas[lifted++])
            }
            while (pendingWrappers.isNotEmpty()) {
                val (name, watName) = pendingWrappers.removeFirst()
                emitWrapper(name, watName)
            }
        }
    }

    private fun emitDeclFunctions(decls: Declarations) {
        for ((name, func) in decls.functions) emitFunction(watId(name), func.lambda)
    }

    private fun emitFunction(
        watName: String,
        lambda: Expr.Lambda,
    ) {
        val ctx = WatFunctionContext(::esc)

        val body = code.emitBranchMatch(lambda.branches, "\$arg", ctx)

        val children = mutableListOf<SExpr>()
        for (local in ctx.allLocals()) children.add(atom("local $local i32"))
        children.add(body)

        moduleChildren.add(SExpr.Inst("func $watName (param \$arg i32) (result i32)", children))
    }

    private fun emitLiftedLambda(lifted: LiftedLambda) {
        val ctx = WatFunctionContext(::esc)

        val stmts = mutableListOf<SExpr>()
        for ((i, cap) in lifted.captures.withIndex()) {
            stmts.add(
                localSet(ctx.bind(cap), i32Load(8 + i * 4, localGet("\$closure_ptr"))),
            )
        }
        val match = code.emitBranchMatch(lifted.lambda.branches, "\$arg", ctx)

        val children = mutableListOf<SExpr>()
        for (local in ctx.allLocals()) children.add(atom("local $local i32"))
        children.add(if (stmts.isEmpty()) match else resultBlock(stmts, match))

        moduleChildren.add(
            SExpr.Inst("func ${lifted.watName} (param \$closure_ptr i32) (param \$arg i32) (result i32)", children),
        )
    }

    private fun emitWrapper(
        name: FuncName,
        watName: String,
    ) {
        val body: List<SExpr> =
            when (name) {
                is FuncName.User ->
                    listOf(call(watId(name), listOf(localGet("\$arg"))))

                is FuncName.Core -> {
                    val op = WatCodeEmitter.CORE_BIN_OPS[name.name]
                    if (op != null) {
                        listOf(
                            inst(
                                op,
                                i32Load(0, localGet("\$arg")),
                                i32Load(4, localGet("\$arg")),
                            ),
                        )
                    } else {
                        listOf(unreachable())
                    }
                }

                is FuncName.Constructor -> wrapperBodyForConstructor(name, watName)
            }

        moduleChildren.add(
            SExpr.Inst(
                "func $watName (param \$closure_ptr i32) (param \$arg i32) (result i32)",
                body,
            ),
        )
    }

    private fun wrapperBodyForConstructor(
        name: FuncName.Constructor,
        watName: String,
    ): List<SExpr> =
        when {
            name.data == DataName.Core.List && name.constructor == "cons" ->
                listOf(call("\$rt.mk_cons", listOf(localGet("\$arg"))))

            name.data == DataName.Core.Set && name.constructor == "setCons" ->
                listOf(
                    call(
                        "\$rt.set_insert",
                        listOf(
                            i32Load(4, localGet("\$arg")),
                            i32Load(0, localGet("\$arg")),
                        ),
                    ),
                )

            name.data == DataName.Core.Tuple -> {
                val stage2 = "$watName.1"
                val stage2Idx = registerInFuncTable(stage2)
                moduleChildren.add(
                    SExpr.Inst(
                        "func $stage2 (param \$closure_ptr i32) (param \$arg i32) (result i32)",
                        listOf(
                            call(
                                "\$rt.mk_tuple",
                                listOf(
                                    i32Load(8, localGet("\$closure_ptr")),
                                    localGet("\$arg"),
                                ),
                            ),
                        ),
                    ),
                )
                listOf(
                    atom("local \$ptr i32"),
                    localSet(
                        "\$ptr",
                        call("\$rt.mk_closure", listOf(i32Const(stage2Idx), i32Const(1))),
                    ),
                    i32Store(8, localGet("\$ptr"), localGet("\$arg")),
                    localGet("\$ptr"),
                )
            }

            else -> {
                val tag = constructorTags[name.data to name.constructor] ?: 0
                listOf(
                    atom("local \$ptr i32"),
                    localSet(
                        "\$ptr",
                        call("\$rt.mk_adt", listOf(i32Const(1), i32Const(tag))),
                    ),
                    i32Store(4, localGet("\$ptr"), localGet("\$arg")),
                    localGet("\$ptr"),
                )
            }
        }


    private fun emitExports() {
        for ((name, _) in program.topLevel.functions) {
            if (name is FuncName.User) {
                moduleChildren.add(SExpr.Raw("(export \"${name.name}\" (func ${watId(name)}))"))
            }
        }
        for ((moduleName, module) in program.modules) {
            for ((name, _) in module.public.functions) {
                if (name is FuncName.User) {
                    moduleChildren.add(SExpr.Raw("(export \"$moduleName.${name.name}\" (func ${watId(name)}))"))
                }
            }
        }
    }


    private fun emitFunctionElem() {
        if (funcTable.isNotEmpty()) {
            moduleChildren.add(SExpr.Raw("(elem (i32.const 0) ${funcTable.joinToString(" ")})"))
        }
    }
}
