package ru.hopec.codegen

internal sealed class SExpr {
    abstract fun render(
        sb: StringBuilder,
        indent: Int,
    )

    class Inst(
        val head: String,
        val args: List<SExpr>,
    ) : SExpr() {
        override fun render(
            sb: StringBuilder,
            indent: Int,
        ) {
            val pad = "  ".repeat(indent)
            if (args.isEmpty()) {
                sb
                    .append(pad)
                    .append('(')
                    .append(head)
                    .append(')')
            } else {
                sb.append(pad).append('(').append(head)
                for (a in args) {
                    sb.append('\n')
                    a.render(sb, indent + 1)
                }
                sb.append(')')
            }
        }
    }

    class Raw(
        val text: String,
    ) : SExpr() {
        override fun render(
            sb: StringBuilder,
            indent: Int,
        ) {
            val pad = "  ".repeat(indent)
            val lines = text.trim('\n').split('\n')
            for ((i, line) in lines.withIndex()) {
                if (i > 0) sb.append('\n')
                if (line.isBlank()) continue
                sb.append(pad).append(line)
            }
        }
    }

    fun format(): String {
        val sb = StringBuilder()
        render(sb, 0)
        sb.append('\n')
        return sb.toString()
    }
}

// ═════════════════════════════════════════════════════════════════════════
// Конструкторы-помощники для часто используемых инструкций
// ═════════════════════════════════════════════════════════════════════════

internal fun atom(text: String): SExpr = SExpr.Inst(text, emptyList())

internal fun inst(
    head: String,
    vararg args: SExpr,
): SExpr = SExpr.Inst(head, args.toList())

internal fun inst(
    head: String,
    args: List<SExpr>,
): SExpr = SExpr.Inst(head, args)

internal fun localGet(name: String): SExpr = atom("local.get $name")

internal fun localSet(
    name: String,
    value: SExpr,
): SExpr = inst("local.set $name", value)

internal fun localTee(
    name: String,
    value: SExpr,
): SExpr = inst("local.tee $name", value)

internal fun globalGet(name: String): SExpr = atom("global.get $name")

internal fun globalSet(
    name: String,
    value: SExpr,
): SExpr = inst("global.set $name", value)

internal fun i32Const(value: Int): SExpr = atom("i32.const $value")

internal fun i32Load(
    offset: Int,
    ptr: SExpr,
): SExpr = inst("i32.load offset=$offset", ptr)

internal fun i32Store(
    offset: Int,
    ptr: SExpr,
    value: SExpr,
): SExpr = inst("i32.store offset=$offset", ptr, value)

internal fun i32Add(
    lhs: SExpr,
    rhs: SExpr,
): SExpr = inst("i32.add", lhs, rhs)

internal fun i32Eq(
    lhs: SExpr,
    rhs: SExpr,
): SExpr = inst("i32.eq", lhs, rhs)

internal fun i32Ne(
    lhs: SExpr,
    rhs: SExpr,
): SExpr = inst("i32.ne", lhs, rhs)

internal fun i32Eqz(value: SExpr): SExpr = inst("i32.eqz", value)

internal fun call(
    name: String,
    args: List<SExpr>,
): SExpr = inst("call $name", args)

internal fun br(label: String): SExpr = atom("br $label")

internal fun brValue(
    label: String,
    value: SExpr,
): SExpr = inst("br $label", value)

internal fun brIf(
    label: String,
    cond: SExpr,
): SExpr = inst("br_if $label", cond)

internal fun unreachable(): SExpr = atom("unreachable")

internal fun block(
    label: String,
    result: String?,
    body: List<SExpr>,
): SExpr {
    val head = if (result != null) "block $label (result $result)" else "block $label"
    return SExpr.Inst(head, body)
}

internal fun resultBlock(
    stmts: List<SExpr>,
    value: SExpr,
): SExpr = SExpr.Inst("block (result i32)", stmts + value)
