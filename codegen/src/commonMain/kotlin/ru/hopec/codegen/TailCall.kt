package ru.hopec.codegen

import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

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

internal fun isSelfTailCall(
    expr: Expr,
    self: FuncName.User,
): Boolean = collectSelfTailArgs(expr, self) != null
