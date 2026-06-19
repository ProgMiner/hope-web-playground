package ru.hopec.core

class TranslationUnits {
    private val units: MutableMap<String, TranslationUnit> = mutableMapOf()

    fun add(initial: TranslationUnit) = units.put(initial.resource.moduleName(), initial)

    fun resolve(module: String) = units[module]

    fun resolveMain() = units.values.find { it.isMain() }

    fun all(): List<TranslationUnit> = units.values.toList()
}
