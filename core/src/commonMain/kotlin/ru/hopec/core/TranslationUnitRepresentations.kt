package ru.hopec.core

import ru.hopec.core.topography.Resource
import ru.hopec.core.tree.GenericTree

data class TranslationUnitRepresentations(
    val resource: Resource,
    val trees: List<GenericTree>,
)
