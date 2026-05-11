package ru.hopec.codegen

/**
 * Builds WAT text with correct indentation.
 * Internally stores (relativeDepth, text) pairs so that sub-emitters can be
 * embedded into a parent emitter at any depth without string manipulation.
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

    fun indent(block: () -> Unit) {
        depth++
        block()
        depth--
    }

    /** Appends another emitter's lines, shifted by this emitter's current depth. */
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
