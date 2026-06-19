package ru.hopec.driver

import ru.hopec.core.GlobalCompilationContext
import ru.hopec.desugarer.SignatureService
import ru.hopec.desugarer.withSignatureService

fun defaultContext() = GlobalCompilationContext().withSignatureService()
