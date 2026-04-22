package ru.hopec.typecheck

infix fun Unit?.join(other: Unit?) =
    if (this == null) {
        null
    } else {
        other
    }
