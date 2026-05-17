package ru.hopec.desugarer

import ru.hopec.desugarer.DesugaredRepresentation.Declarations.Function.Name.Constructor

sealed interface ResolvedExpr {
    data class Local(val level: Int) : ResolvedExpr

    data class GlobalSet(val idents: Set<DesugaredRepresentation.Declarations.Function.Name>) : ResolvedExpr
}

sealed interface ResolvedPattern {
    object Var : ResolvedPattern
    data class GlobalSet(val idents: Set<Constructor>) : ResolvedPattern {
        fun toExpr() = ResolvedExpr.GlobalSet((idents.toSet() as Set<DesugaredRepresentation.Declarations.Function.Name>).toMutableSet())
    }
}
