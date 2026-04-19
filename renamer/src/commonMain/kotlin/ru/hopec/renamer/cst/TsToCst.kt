package ru.hopec.renamer.cst


class TsToCst {
    data class Infix(val priority: Int, val isRightAssoc: Boolean)
    val operators: Map<String, Infix> = emptyMap()
}