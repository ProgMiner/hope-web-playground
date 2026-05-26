package ru.hopec.core

import ru.hopec.core.topography.Range

class MultiStatus : CompilationStatus {
    override val severity: StatusSeverity
        get() = severity()
    override val message: String
        get() = message()
    override val range: Range
    private val label: String
    private val children: MutableList<CompilationStatus>

    constructor(range: Range = Range(), label: String = "") {
        this.range = range
        this.label = label
        children = ArrayList()
    }

    fun add(status: CompilationStatus) {
        children.add(status)
    }

    fun children(): List<CompilationStatus> = children

    private fun severity() = aggregatedSeverities().keys.maxOrNull() ?: StatusSeverity.INFO

    private fun message(): String =
        aggregatedSeverities()
            .entries
            .sortedByDescending { it.key }
            .mapNotNull { severitySummary(it.key, it.value) }
            .run { "$label: ${composedMessage()}" }

    private fun List<String>.composedMessage(): String =
        if (isEmpty()) {
            okMessage()
        } else {
            joinToString(separator = ", ")
        }

    private fun okMessage(): String = "everything is OK"

    private fun severitySummary(
        severity: StatusSeverity,
        count: Int,
    ): String? =
        severity.toString().lowercase().run {
            when (count) {
                0 -> null
                1 -> "1 $this"
                else -> "$count ${this}s"
            }
        }

    fun aggregatedSeverities(): Map<StatusSeverity, Int> =
        children
            .map { it.severities() }
            .fold(mutableMapOf()) { acc, next -> acc.merge(next) }
}

fun CompilationStatus.severities(): Map<StatusSeverity, Int> =
    if (this is MultiStatus) {
        aggregatedSeverities()
    } else {
        mapOf(severity to 1)
    }

fun MutableMap<StatusSeverity, Int>.merge(other: Map<StatusSeverity, Int>): MutableMap<StatusSeverity, Int> {
    for ((severity, count) in other) this[severity] = this[severity]?.let { it + count } ?: count
    return this
}
