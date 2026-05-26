package ru.hopec.renamer

import ru.hopec.core.StatusSeverity
import ru.hopec.core.topography.Range

open class RenamerException : IllegalStateException {
    val range: Range
    val fatal: Boolean

    constructor(message: String, range: Range, fatal: Boolean = false) :
        super("[${StatusSeverity.ERROR.name}] " + "${range.from?.row}:${range.from?.column} " + message) {
        this.range = range
        this.fatal = fatal
    }
}
