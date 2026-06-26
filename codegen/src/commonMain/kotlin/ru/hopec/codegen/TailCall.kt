package ru.hopec.codegen

import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

internal data class SelfTailIf(
    val condition: Expr,
    val thenArgs: List<Expr>,
    val elseArgs: List<Expr>,
)

internal data class SelfTailElse(
    val condition: Expr,
    val nonTail: Expr,
    val tailIf: SelfTailIf,
)

internal fun collectSelfTailArgs(
    expr: Expr,
    self: FuncName.User,
): List<Expr>? {
    val args = mutableListOf<Expr>()
    var cur: Expr = expr
    while (cur is Expr.Application) {
        args.add(0, cur.right)
        when (val left = cur.left) {
            is Expr.Identifier -> {
                if (left.name == self) return args
                return null
            }
            else -> cur = left
        }
    }
    return null
}

internal fun collectSelfTailIf(
    expr: Expr,
    self: FuncName.User,
): SelfTailIf? {
    if (expr !is Expr.If) return null
    val thenArgs = collectSelfTailArgs(expr.positive, self) ?: return null
    val elseArgs = collectSelfTailArgs(expr.negative, self) ?: return null
    if (thenArgs.size != elseArgs.size) return null
    return SelfTailIf(expr.condition, thenArgs, elseArgs)
}

internal fun collectSelfTailElse(
    expr: Expr,
    self: FuncName.User,
): SelfTailElse? {
    if (expr !is Expr.If) return null
    if (collectSelfTailArgs(expr.positive, self) != null) return null
    val tailIf = collectSelfTailIf(expr.negative, self) ?: return null
    return SelfTailElse(expr.condition, expr.positive, tailIf)
}

internal fun isSelfTailCall(
    expr: Expr,
    self: FuncName.User,
): Boolean =
    collectSelfTailArgs(expr, self) != null ||
        collectSelfTailIf(expr, self) != null ||
        collectSelfTailElse(expr, self) != null
