package ru.hopec.codegen

/**
 * Per-function state for WAT code generation.
 *
 * WAT requires all local declarations at the top of the function body, before
 * any instructions.  This class tracks both user-visible variables (bound by
 * patterns and let-expressions) and compiler-generated temporaries so that
 * [WatGenerator] can declare them all in one pass after the body has been
 * generated into a [WatEmitter].
 */
internal class WatFunctionContext(
    private val escapeName: (String) -> String,
) {
    // Ordered so that declarations are emitted in insertion order.
    private val userLocals = LinkedHashMap<String, String>()
    private val tmpLocals = mutableListOf<String>()
    private var tmpCounter = 0

    /** Returns the WAT local identifier for [name], creating one if absent. */
    fun getOrAdd(name: String): String = userLocals.getOrPut(name) { "\$v_${escapeName(name)}_${userLocals.size}" }

    /** Allocates a fresh compiler-generated temporary. */
    fun freshTmp(): String {
        val id = "\$t_$tmpCounter"
        tmpCounter++
        tmpLocals.add(id)
        return id
    }

    /** All locals that must appear as `(local … i32)` in the WAT function header. */
    fun allLocals(): List<String> = userLocals.values.toList() + tmpLocals
}
