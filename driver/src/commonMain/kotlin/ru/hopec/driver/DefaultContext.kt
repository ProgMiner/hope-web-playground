package ru.hopec.driver

import ru.hopec.core.GlobalCompilationContext
import ru.hopec.desugarer.withSignatureService

fun defaultContext() = GlobalCompilationContext().withSignatureService()
