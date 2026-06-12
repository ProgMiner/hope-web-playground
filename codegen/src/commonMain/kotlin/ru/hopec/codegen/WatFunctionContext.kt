package ru.hopec.codegen

/**
 * Состояние генерации WAT-кода для одной функции.
 *
 * В WAT все объявления локальных переменных должны идти в начале тела функции,
 * до любых инструкций, поэтому контекст накапливает все выделенные локалы и
 * выдаёт их список после того, как тело собрано.
 *
 * Видимость пользовательских переменных отслеживается стеком лексических
 * областей: каждая ветка лямбды / let вводит новую область ([pushScope] /
 * [popScope]), а затенённое имя получает собственный WAT-локал вместо
 * переиспользования внешнего.
 */
internal class WatFunctionContext(
    private val escapeName: (String) -> String,
) {
    private class Scope {
        val bindings = mutableMapOf<String, String>()
    }

    // Все объявленные пользовательские локалы в порядке создания.
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

    /** Связывает [name] в текущей области с новым WAT-локалом и возвращает его. */
    fun bind(name: String): String {
        val id = "\$v_${escapeName(name)}_$userCounter"
        userCounter++
        userLocals.add(id)
        scopes.last().bindings[name] = id
        return id
    }

    /** Ищет [name] от внутренней области к внешней. */
    fun lookup(name: String): String? {
        for (scope in scopes.asReversed()) {
            scope.bindings[name]?.let { return it }
        }
        return null
    }

    /**
     * Возвращает локал для [name]; если имя ещё не связано — связывает в
     * текущей области. Используется и для привязки паттернов, и для чтения
     * переменных (typecheck гарантирует, что чтение происходит после привязки).
     */
    fun getOrAdd(name: String): String = lookup(name) ?: bind(name)

    /** Выделяет свежую временную переменную, сгенерированную компилятором. */
    fun freshTmp(): String {
        val id = "\$t_$tmpCounter"
        tmpCounter++
        tmpLocals.add(id)
        return id
    }

    /** Все локалы, которые должны попасть в заголовок WAT-функции как `(local … i32)`. */
    fun allLocals(): List<String> = userLocals + tmpLocals
}
