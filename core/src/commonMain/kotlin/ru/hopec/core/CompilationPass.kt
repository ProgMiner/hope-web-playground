package ru.hopec.core

interface CompilationPass<F, T>
        where F : Representation, T : Representation {
    fun run(from: F, context: CompilationContext): T?
}

fun <F, I, T> CompilationPass<F, I>.then(next: CompilationPass<I, T>): CompilationPass<F, T>
        where F : Representation, I : Representation, T : Representation {
    return PassChain(this, next)
}

private class PassChain<F, I, T>(private val first: CompilationPass<F, I>, private val second: CompilationPass<I, T>) :
    CompilationPass<F, T>
        where F : Representation, I : Representation, T : Representation {
    override fun run(from: F, context: CompilationContext): T? {
        val intermediate = first.run(from, context) ?: return null
        return second.run(intermediate, context)
    }

}