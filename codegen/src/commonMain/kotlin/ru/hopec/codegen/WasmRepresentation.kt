package ru.hopec.codegen

import ru.hopec.core.Representation

/** Holds the WAT text produced by [WatGenerator]. */
class WasmRepresentation(
    val wat: String,
) : Representation
