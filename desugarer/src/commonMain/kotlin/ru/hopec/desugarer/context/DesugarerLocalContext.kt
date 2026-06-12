package ru.hopec.desugarer.context

import ru.hopec.desugarer.ResolvedExpr
import ru.hopec.desugarer.ResolvedPattern

data class DesugarerLocalContext(
    val localScope: MutableMap<String, Int> = mutableMapOf(),
    var level: Int = 0,
) {
    fun fork() = DesugarerLocalContext(HashMap(localScope), level)

    fun extendLocal(vars: List<String>) =
        vars.forEach {
            localScope[it] = level
            level++
        }

    // De Brujin index: 0 — ближайший биндер (level - declaredLevel - 1)
    fun getLocalVar(name: String) = localScope[name]?.let { ResolvedExpr.Local(level - it - 1) }

    // возможно тут сразу стоит отбросить не нуль-арные конструкторы
    fun resolvePatternVar(name: String) = ResolvedPattern.Var.also { extendLocal(listOf(name)) }
}
