package ru.hopec.codegen

internal class WatFunctionContext(
    private val escapeName: (String) -> String,
) {
    private class Scope {
        val bindings = mutableMapOf<String, String>()
    }

    private val userLocals = mutableListOf<String>()
    private val tmpLocals = mutableListOf<String>()
    private var userCounter = 0
    private var tmpCounter = 0

    private val scopes = ArrayDeque<Scope>().apply { addLast(Scope()) }

    fun pushScope() {
        scopes.addLast(Scope())
    }

    fun popScope() {
        scopes.removeLast()
    }

    fun bind(name: String): String {
        val id = "\$v_${escapeName(name)}_$userCounter"
        userCounter++
        userLocals.add(id)
        scopes.last().bindings[name] = id
        return id
    }

    fun lookup(name: String): String? {
        for (scope in scopes.asReversed()) {
            scope.bindings[name]?.let { return it }
        }
        return null
    }

    fun getOrAdd(name: String): String = lookup(name) ?: bind(name)

    fun freshTmp(): String {
        val id = "\$t_$tmpCounter"
        tmpCounter++
        tmpLocals.add(id)
        return id
    }

    fun allLocals(): List<String> = userLocals + tmpLocals
}
