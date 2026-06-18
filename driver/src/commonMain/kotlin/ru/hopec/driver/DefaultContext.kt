package ru.hopec.driver

import ru.hopec.core.GlobalCompilationContext
import ru.hopec.desugarer.SignatureService

fun defaultContext(): GlobalCompilationContext {
    val context = GlobalCompilationContext()
    val signatureService = SignatureService.core(context)
    context.services().addService(signatureService)
    return context
}
