package ru.hopec.core.topography

data class Resource(
    val path: String,
) {
    fun moduleName(): String =
        path
            .split('/')
            .last()
            .split('.')
            .first()
}
