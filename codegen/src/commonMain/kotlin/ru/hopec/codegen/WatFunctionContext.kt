package ru.hopec.codegen

/**
 * Состояние генерации WAT-кода для одной функции.
 *
 * В WAT все объявления локальных переменных должны идти в начале тела функции,
 * до любых инструкций. Этот класс отслеживает как видимые пользователю переменные
 * (связанные паттернами и let-выражениями), так и временные, сгенерированные
 * компилятором, чтобы [WatGenerator] мог объявить их все за один проход уже
 * после того, как тело собрано в [WatEmitter].
 */
internal class WatFunctionContext(
    private val escapeName: (String) -> String,
) {
    // Упорядочено, чтобы объявления выводились в порядке вставки.
    private val userLocals = LinkedHashMap<String, String>()
    private val tmpLocals = mutableListOf<String>()
    private var tmpCounter = 0

    /** Возвращает WAT-идентификатор локала для [name], создавая его при необходимости. */
    fun getOrAdd(name: String): String = userLocals.getOrPut(name) { "\$v_${escapeName(name)}_${userLocals.size}" }

    /** Выделяет свежую временную переменную, сгенерированную компилятором. */
    fun freshTmp(): String {
        val id = "\$t_$tmpCounter"
        tmpCounter++
        tmpLocals.add(id)
        return id
    }

    /** Все локалы, которые должны попасть в заголовок WAT-функции как `(local … i32)`. */
    fun allLocals(): List<String> = userLocals.values.toList() + tmpLocals
}
