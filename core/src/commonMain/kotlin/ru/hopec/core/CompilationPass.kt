package ru.hopec.core

interface CompilationPass<F: Representation, T: Representation> {
    fun run(from: F, context: CompilationContext): T?
}

fun <F: Representation, I: Representation, T: Representation> CompilationPass<F, I>.then(
    next: CompilationPass<I, T>,
): CompilationPass<F, T> {
    val first = this
    return object : CompilationPass<F, T> {
        override fun run(from: F, context: CompilationContext): T? {
            val intermediate = first.run(from, context) ?: return null
            return next.run(intermediate, context)
        }
    }
}
