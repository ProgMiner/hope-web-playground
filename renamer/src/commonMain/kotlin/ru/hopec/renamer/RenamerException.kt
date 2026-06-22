package ru.hopec.renamer

import ru.hopec.core.CompilationContext
import ru.hopec.core.errorStatus
import ru.hopec.core.topography.Range

open class RenamerException : IllegalStateException {
    val range: Range
    val fatal: Boolean

    constructor(message: String, range: Range, fatal: Boolean = false) : super("$range: $message") {
        this.range = range
        this.fatal = fatal
    }
}

fun CompilationContext.add(exception: Exception) {
    report(errorStatus(exception.message ?: "Unknown error", exception.range()))
}

private fun Exception.range(): Range =
    if (this is RenamerException) {
        range
    } else {
        Range()
    }
