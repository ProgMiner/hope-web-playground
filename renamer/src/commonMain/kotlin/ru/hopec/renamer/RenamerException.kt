package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.core.topography.Range

open class RenamerException : IllegalStateException {
    val severity: StatusSeverity
    val range: Range

    constructor(severity: StatusSeverity, message: String, range: Range) :
        super("[${severity.name}] " + "${range.from?.row}:${range.from?.column} " + message) {
        this.severity = severity
        this.range = range
    }
}
