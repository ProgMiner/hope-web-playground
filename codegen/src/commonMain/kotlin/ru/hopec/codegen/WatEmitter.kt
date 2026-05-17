package ru.hopec.codegen

/**
 * Собирает WAT-текст с правильными отступами.
 * Внутри хранит пары (относительная глубина, текст), чтобы вложенные эмиттеры
 * можно было встраивать в родительский на любой глубине без манипуляций со строкой.
 */
internal class WatEmitter {
    private data class Entry(
        val depth: Int,
        val text: String,
    )

    private val entries = mutableListOf<Entry>()
    private var depth = 0

    fun line(s: String) {
        entries.add(Entry(depth, s))
    }

    fun lineAt(
        extraDepth: Int,
        s: String,
    ) {
        entries.add(Entry(depth + extraDepth, s))
    }

    fun indent(block: () -> Unit) {
        depth++
        block()
        depth--
    }

    /** Добавляет строки другого эмиттера, сдвинутые на текущую глубину этого эмиттера. */
    fun append(other: WatEmitter) {
        for (e in other.entries) {
            entries.add(Entry(depth + e.depth, e.text))
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for ((d, text) in entries) {
            repeat(d) { sb.append("  ") }
            sb.appendLine(text)
        }
        return sb.toString()
    }
}
