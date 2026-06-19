package ru.hopec.core

import ru.hopec.core.topography.Range
import ru.hopec.core.topography.Resource

enum class StatusSeverity {
    INFO,
    WARNING,
    ERROR, ;

    fun prefix() = "[$name] "
}

interface CompilationStatus {
    val severity: StatusSeverity

    val message: String

    val range: Range

    data class Plain(
        override val severity: StatusSeverity,
        override val message: String,
        override val range: Range,
    ) : CompilationStatus
}

fun CompilationStatus.isError(): Boolean = this.severity == StatusSeverity.ERROR

fun okStatus(): CompilationStatus = CompilationStatus.Plain(StatusSeverity.INFO, "", Range())

fun warningStatus(
    message: String,
    location: Range,
): CompilationStatus = CompilationStatus.Plain(StatusSeverity.WARNING, message, location)

fun errorStatus(
    message: String,
    location: Range,
): CompilationStatus = CompilationStatus.Plain(StatusSeverity.ERROR, message, location)

fun CompilationStatus.inResource(resource: Resource): CompilationStatus =
    CompilationStatus.Plain(severity, message, Range(resource, range.from, range.to))
