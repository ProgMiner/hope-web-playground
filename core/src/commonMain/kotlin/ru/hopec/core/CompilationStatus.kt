package ru.hopec.core

enum class StatusSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class StatusLocation(val from: Int, val to: Int)

interface CompilationStatus {

    val severity: StatusSeverity

    val message: String

    val location: StatusLocation

    data class Plain(
        override val severity: StatusSeverity,
        override val message: String,
        override val location: StatusLocation
    ) : CompilationStatus
}

fun CompilationStatus.isError(): Boolean = this.severity == StatusSeverity.ERROR

fun ok(): CompilationStatus =
    CompilationStatus.Plain(StatusSeverity.INFO, "", StatusLocation(0, 0))

fun warning(message: String, location: StatusLocation): CompilationStatus =
    CompilationStatus.Plain(StatusSeverity.WARNING, message, location)

fun error(message: String, location: StatusLocation): CompilationStatus =
    CompilationStatus.Plain(StatusSeverity.ERROR, message, location)