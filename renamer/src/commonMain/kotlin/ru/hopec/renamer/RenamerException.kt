package ru.hopec.renamer

import ru.hopec.core.StatusSeverity

class RenamerException: IllegalStateException {
    val severity: StatusSeverity
    val location: RenamerLocation
    constructor(severity: StatusSeverity, message: String, location: RenamerLocation):
            super("[${severity.name}] " + "${location.row}:${location.column} " + message) {
        this.severity = severity
        this.location = location
    }

    data class RenamerLocation(val row: Int, val column: Int)
}