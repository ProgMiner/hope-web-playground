package ru.hopec.core.topography

/**
 * zero-based
 */
data class Point(
    val index: UInt,
    val row: UInt,
    val column: UInt,
) {
    override fun toString(): String = "${row + 1U}:$column"
}
