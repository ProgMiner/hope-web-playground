package ru.hopec.core

interface CompilationPass<F, T>
        where F : Representation, T : Representation {
    fun run(from: F, context: CompilationContext): T?
}