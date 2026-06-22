package ru.hopec.codegen

import ru.hopec.codegen.runtime.WatImports
import ru.hopec.typecheck.TypedRepresentation
import ru.hopec.typecheck.TypedRepresentation.Declarations
import ru.hopec.typecheck.TypedRepresentation.Expr
import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name as FuncName

internal object IoImportUsage {
    fun collect(program: TypedRepresentation): Set<FuncName.Core> {
        val used = mutableSetOf<FuncName.Core>()

        fun walkExpr(expr: Expr) {
            when (expr) {
                is Expr.Identifier -> {
                    val name = expr.name
                    if (name is FuncName.Core && WatImports.isBuiltin(name)) {
                        used.add(name)
                    }
                }

                is Expr.Variable -> {}

                is Expr.Application -> {
                    walkExpr(expr.left)
                    walkExpr(expr.right)
                }

                is Expr.If -> {
                    walkExpr(expr.condition)
                    walkExpr(expr.positive)
                    walkExpr(expr.negative)
                }

                is Expr.Let -> {
                    walkExpr(expr.matcher)
                    walkExpr(expr.body)
                }

                is Expr.Lambda -> expr.branches.forEach { walkExpr(it.body) }

                is Expr.Literal -> {}
            }
        }

        fun walkDecls(decls: Declarations) {
            decls.functions.values.forEach { function ->
                function.lambda.branches.forEach { walkExpr(it.body) }
            }
        }

        walkDecls(program.topLevel)
        for (module in program.modules.values) {
            walkDecls(module.public)
            walkDecls(module.private)
        }
        return used
    }
}
