package ru.hopec.desugarer.context

import ru.hopec.desugarer.ResolvedExpr
import ru.hopec.desugarer.ResolvedPattern

data class DesugarerLocalContext(
    val localScope: MutableMap<String, Int> = mutableMapOf(),
) {
    var level: Int = 0

    fun extendLocal(vars: List<String>) =
        vars.forEach {
            localScope[it] = level
            level++
        }

    fun getLocalVar(name: String) = localScope[name]?.let { ResolvedExpr.Local(level - it) }

    // возможно тут сразу стоит отбросить не нуль-арные конструкторы
    fun resolvePatternVar(name: String) = ResolvedPattern.Var.also { extendLocal(listOf(name)) }
}