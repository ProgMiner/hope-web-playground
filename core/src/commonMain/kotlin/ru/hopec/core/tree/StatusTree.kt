package ru.hopec.core.tree

import ru.hopec.core.CompilationStatus
import ru.hopec.core.MultiStatus

fun CompilationStatus.intoNode(): GenericNode =
    GenericNode(
        range,
        message,
        if (this is MultiStatus) {
            children().map { it.intoNode() }
        } else {
            listOf()
        },
    )
